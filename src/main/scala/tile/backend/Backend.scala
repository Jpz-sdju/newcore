package tile.backend

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import bus._
import tile._
import freechips.rocketchip.util.DontTouch
import tile.frontend.FuType

class Backend()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Bundle{
      val cf = new CfCtrl
      val src = Vec(2, UInt(64.W))
    }))
  })

  val alu = Module(new ALU)
  val jump = Module(new Jump)
  alu.io.in <> io.in
  jump.io.in <> io.in
  alu.io.in.valid := !FuType.isJumpExu(io.in.bits.cf.ctrl.fuType)
  jump.io.in.valid := FuType.isJumpExu(io.in.bits.cf.ctrl.fuType)


  alu.io.out <> DontCare
  jump.io.out <> DontCare
  alu.io.out.ready := true.B
  jump.io.out.ready := true.B  
  dontTouch(alu.io.out)
  dontTouch(jump.io.out)
}
