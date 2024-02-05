package tile.backend

import chisel3._, utils._, util._
import bus._
import freechips.rocketchip.config._
import tile._
import top.Setting
import frontend._

class Jump(implicit p: Parameters) extends Module with Setting {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Bundle {
      val cf = new CfCtrl
      val src = Vec(2,UInt(XLEN.W))
    }))
    val out = Decoupled(new Bundle {
      val redirect = UInt(64.W)
    })
  })

  io.out.valid := io.in.valid
  io.in.ready := io.out.ready

  io.out.bits.redirect := io.in.bits.src(0) + io.in.bits.src(1)

}
