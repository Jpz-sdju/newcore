package tile.backend

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import bus._
import tile._
import freechips.rocketchip.util.DontTouch
import tile.frontend.FuType
import top.Setting
import tile.frontend.ALUOpType
import tile.frontend.JumpOpType

class Backend()(implicit p: Parameters) extends Module with Setting{
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Bundle{
      val cf = new CfCtrl
      val src = Vec(2, UInt(64.W))
    }))

    val redirect = (DecoupledIO(UInt(XLEN.W)))
  })

  val alu = Module(new ALU)
  val jump = Module(new Jump)

  val futype = io.in.bits.cf.ctrl.fuType
  val fuOptype = io.in.bits.cf.ctrl.fuOpType

  alu.io.in <> io.in
  jump.io.in <> io.in
  alu.io.in.valid := !FuType.isJumpExu(futype)
  jump.io.in.valid := FuType.isJumpExu(futype)

  //Define redirect sig
  val is_branch = FuType.isAluExu(futype) && ALUOpType.isBranch(fuOptype)
  val is_jmp = FuType.isJumpExu(futype) && (!JumpOpType.jumpOpisAuipc(fuOptype))
  val branch_target = io.in.bits.cf.cf.pc + io.in.bits.cf.ctrl.imm

  //redirect to frontend
  val redirect_target = Mux(is_jmp, jump.io.redirect.bits, branch_target)
  io.redirect.bits := redirect_target
  io.redirect.valid := io.in.valid && (is_branch || is_jmp)
  jump.io.redirect.ready := io.redirect.ready

  
  alu.io.out <> DontCare
  jump.io.out <> DontCare


  alu.io.out.ready := true.B
  jump.io.out.ready := true.B  
  dontTouch(alu.io.out)
  dontTouch(jump.io.out)
  dontTouch(io.in)
}
