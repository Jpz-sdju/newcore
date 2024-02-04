package tile.backend

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import bus._
import tile._
import freechips.rocketchip.util.DontTouch

class Backend()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Bundle{
      val cf = new CfCtrl
      val src = Vec(2, UInt(64.W))
    }))
  })

  val alu = Module(new ALU)
  alu.io.in <> io.in
  alu.io.out <> DontCare
  alu.io.out.ready := true.B

  dontTouch(alu.io.out)
}
