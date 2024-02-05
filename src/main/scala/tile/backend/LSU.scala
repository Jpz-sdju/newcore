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

class LSU()(implicit p: Parameters) extends Module with Setting {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Bundle {
      val cf = new CfCtrl
      val src = Vec(2, UInt(64.W))
    }))

  })

}
