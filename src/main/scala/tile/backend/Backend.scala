package tile.backend

import chisel3._
import chisel3.util._
import chisel3.util.experimental._
import freechips.rocketchip.config._
import bus._
import tile._
import freechips.rocketchip.util.DontTouch
import top.Setting
import utils.PipelineConnect
import _root_.tile.frontend._
import difftest._

class Backend()(implicit p: Parameters) extends Module with Setting {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new PipelineBundle))

    val redirect = (DecoupledIO(UInt(XLEN.W)))

    val d_req = new LsuBus
    // val resp_to_lsu = Flipped(Decoupled(new ReadResp))

    val wb = Decoupled(new WBundle)

    // debug port
    val gpr = Input(Vec(32, UInt(64.W)))
  })
  val in = io.in.bits
  val pc = io.in.bits.cf.cf.pc

  /*
  IDU TO EXU
   */

  // alu region
  val alu = Module(new ALU)
  alu.io.in.bits.cf := in.cf
  alu.io.in.bits.src(0) := in.Src1
  alu.io.in.bits.src(1) := in.Src2
  alu.io.in.valid := in.isAlu && io.in.valid
  // assign redirect
  val redirect = Wire(DecoupledIO(UInt(XLEN.W)))
  val jal = in.isJmp && in.cf.ctrl.fuOpType === JumpOpType.jal
  val jalr = in.isJmp && in.cf.ctrl.fuOpType === JumpOpType.jalr
  val pc_with_offset = pc + in.Imm
  val jmp_target = Mux(jal, pc_with_offset, in.Src1 + in.Imm)
  redirect.bits := Mux(in.isBranch, pc_with_offset, jmp_target)
  redirect.valid := io.in.valid && (jal || jalr || alu.io.taken_branch)
  io.redirect <> redirect

  // mdu region
  val mdu = Module(new SSDMDU)
  mdu.io.divflush := false.B
  mdu.io.in.bits.func := in.cf.ctrl.fuOpType
  mdu.io.in.bits.src1 := in.Src1
  mdu.io.in.bits.src2 := in.Src2
  mdu.io.in.valid := in.isMdu && io.in.valid

  // assign to exu out,FILL lsAddr!
  val mem_in = Wire(Decoupled(new PipelineBundle))
  val mem_out = Wire(Decoupled(new PipelineBundle))
  val wb_in = Wire(Decoupled(new PipelineBundle))
  //this instr is mdu
  val have_mdu = RegInit(false.B)
  val mdu_reg = RegEnable(in, mdu.io.in.fire)
  when(mdu.io.in.fire) {
    have_mdu := true.B
  }.elsewhen(mdu.io.out.fire) {
    have_mdu := false.B
  }

  mem_in.bits := Mux(have_mdu || mdu.io.in.fire , mdu_reg, io.in.bits)
  mem_in.bits.lsAddr := in.Src1 + in.Imm // init
  mem_in.bits.WRITE_BACK := Mux(
    in.isAlu && io.in.valid,
    alu.io.out.bits.result,
    Mux(
      have_mdu,
      mdu.io.out.bits,
      Mux(jal || jalr, pc + 4.U, Mux(in.isAuipc, pc_with_offset, 0.U))
    )
  )
  mem_in.valid := Mux(have_mdu || mdu.io.in.fire, mdu.io.out.valid, io.in.valid)

  mdu.io.out.ready := mem_in.ready
  alu.io.out.ready := mem_in.ready
  // ready transmit to frontedn
  io.in.ready := mem_in.ready
  /*
  EX to DCACHE
   */
  val lsu = Module(new LSU)
  lsu.io.in <> mem_in
  lsu.io.d_req <> io.d_req

  /*
    Dcache to write back
   */
  mem_out <> lsu.io.out

  PipelineConnect(mem_out, wb_in, wb_in.fire, false.B)
  // trap
  val trap_cond = "h5006b".U === wb_in.bits.cf.cf.instr

  io.wb.valid := wb_in.valid
  io.wb.bits.rd := Mux(trap_cond, 10.U, wb_in.bits.rd)
  io.wb.bits.data := Mux(trap_cond, 0.U, wb_in.bits.WRITE_BACK)
  io.wb.bits.wen := Mux(
    trap_cond,
    true.B,
    wb_in.valid && wb_in.bits.cf.ctrl.rfWen
  )
  wb_in.ready := io.wb.ready

  alu.io.out.ready := true.B

  /*
    DIFFETST REGION

   */
  val dt_te = Module(new DifftestTrapEvent)
  val dt_ic = Module(new DifftestInstrCommit)
  val dt_iw = Module(new DifftestIntWriteback)
  val dt_irs = Module(new DifftestArchIntRegState)
  val dt_cs = Module(new DifftestCSRState)

  dt_ic.io.clock := clock
  dt_ic.io.coreid := 0.U
  dt_ic.io.index := 0.U
  dt_ic.io.valid := RegNext(io.wb.valid)
  dt_ic.io.pc := RegNext(Cat(0.U((64 - 32).W), wb_in.bits.cf.cf.pc))
  dt_ic.io.instr := RegNext(wb_in.bits.cf.cf.instr)
  dt_ic.io.special := 0.U
  dt_ic.io.isRVC := 0.U
  dt_ic.io.skip := RegNext(wb_in.bits.lsAddr < "h80000000".U || wb_in.bits.lsAddr > "h90000000".U)&& RegNext(wb_in.bits.isLoad && io.wb.valid && io.wb.bits.wen || wb_in.bits.isStore) 
  dt_ic.io.scFailed := false.B
  dt_ic.io.wen := RegNext(io.wb.bits.wen)
  dt_ic.io.wpdest := RegNext(io.wb.bits.rd)
  dt_ic.io.wdest := RegNext(io.wb.bits.rd)

  val rf_a0 = WireInit(0.U(64.W))
  BoringUtils.addSink(rf_a0, "rf_a0")

  dt_iw.io.clock := clock
  dt_iw.io.coreid := 0.U
  dt_iw.io.valid := RegNext(io.wb.valid && io.wb.bits.wen)
  dt_iw.io.dest := RegNext(io.wb.bits.rd)
  dt_iw.io.data := RegNext(io.wb.bits.data)

  dt_irs.io.gpr <> io.gpr
  dt_irs.io.clock := clock
  dt_irs.io.coreid := 0.U

  dt_cs.io.clock := clock
  dt_cs.io.coreid := 0.U
  dt_cs.io.priviledgeMode := 3.U
  dt_cs.io.mstatus := 0.U
  dt_cs.io.sstatus := 0.U
  dt_cs.io.mepc := 0.U
  dt_cs.io.sepc := 0.U
  dt_cs.io.mtval := 0.U
  dt_cs.io.stval := 0.U
  dt_cs.io.mtvec := 0.U
  dt_cs.io.stvec := 0.U
  dt_cs.io.mcause := 0.U
  dt_cs.io.scause := 0.U
  dt_cs.io.satp := 0.U
  dt_cs.io.mip := 0.U
  dt_cs.io.mie := 0.U
  dt_cs.io.mscratch := 0.U
  dt_cs.io.sscratch := 0.U
  dt_cs.io.mideleg := 0.U
  dt_cs.io.medeleg := 0.U

  val cycle_cnt = RegInit(0.U(64.W))
  cycle_cnt := cycle_cnt + 1.U
  val instr_cnt = RegInit(0.U(64.W))
  when(io.wb.fire) {
    instr_cnt := instr_cnt + 1.U
  }

  dt_te.io.clock := clock
  dt_te.io.cycleCnt := cycle_cnt
  dt_te.io.instrCnt := instr_cnt
  dt_te.io.valid := RegNext(trap_cond)

}
