package tile.backend

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import bus._
import tile._
import freechips.rocketchip.util.DontTouch
import top.Setting
import utils.PipelineConnect
import _root_.tile.frontend._

class Backend()(implicit p: Parameters) extends Module with Setting {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new PipelineBundle))

    val redirect = (DecoupledIO(UInt(XLEN.W)))

    val d_req = Decoupled(new CacheReq)
    val read_resp = Flipped(Decoupled(new ReadResp))

    val wb = Decoupled(new WBundle)
  })
  val in = io.in.bits
  val pc = io.in.bits.cf.cf.pc

  /* 
  IDU TO EXU
  */
  
  val alu = Module(new ALU)
  alu.io.in.bits.cf :=  in.cf
  alu.io.in.bits.src(0) := in.Src1
  alu.io.in.bits.src(1) := in.Src2
  alu.io.in.valid := in.isAlu 
  //assign redirect
  val redirect = Wire(DecoupledIO(UInt(XLEN.W)))
  val jal = in.isJmp && in.cf.ctrl.fuOpType === JumpOpType.jal
  val jalr = in.isJmp && in.cf.ctrl.fuOpType === JumpOpType.jalr
  val pc_with_offset = pc + in.Imm
  val jmp_target = Mux(jal, pc_with_offset, in.Src1 + in.Imm )
  redirect.bits := Mux(in.isBranch, pc_with_offset, jmp_target )
  redirect.valid := io.in.valid &&( jal || jalr || alu.io.taken_branch)
  io.redirect <> redirect
  
  
  //assign to exu out,FILL lsAddr!
  val exu_out = Wire(Decoupled(new PipelineBundle))
  val mem_in = Wire(Decoupled(new PipelineBundle))
  val mem_out = Wire(Decoupled(new PipelineBundle))
  val wb_in = Wire(Decoupled(new PipelineBundle))
  
  exu_out.bits := io.in.bits
  exu_out.bits.lsAddr := in.Src1 + in.Imm //init
  exu_out.bits.WRITE_BACK := Mux(in.isAlu, alu.io.out.bits.result,Mux(jal || jalr, pc+4.U, Mux( in.isAuipc, pc_with_offset, 0.U)))
  exu_out.valid := io.in.valid

  alu.io.out.ready := exu_out.ready  
  //ready transmit to frontedn
  io.in.ready := exu_out.ready
  /* 
  EX to DCACHE
  */
  PipelineConnect(exu_out, mem_in, mem_in.fire,false.B)
  val lsu = Module(new LSU)
  lsu.io.in <> mem_in
  lsu.io.d_req <> io.d_req
  lsu.io.read_resp <> io.read_resp

  /* 
    Dcache to write back
   */
  mem_out <> lsu.io.out

  PipelineConnect(mem_out, wb_in, mem_in.fire,false.B)

  io.wb.valid := wb_in.valid
  io.wb.bits.rd := wb_in.bits.rd
  io.wb.bits.data := wb_in.bits.WRITE_BACK
  wb_in.ready := io.wb.ready

  alu.io.out.ready := true.B
  dontTouch(io.in)
  dontTouch(exu_out)
  dontTouch(mem_in)
}
