package top

import chisel3._
import freechips.rocketchip.config._

class Top()(implicit p: Parameters) extends Module {

    val s = VecInit(Seq(1.U,1.U,1.U,1.U))
    dontTouch(s)
    println(s.init)
}
