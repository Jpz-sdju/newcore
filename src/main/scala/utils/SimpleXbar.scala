package utils

import chisel3._, utils._, util._
import bus._
import freechips.rocketchip.config._
import tile._
import top.Setting
import frontend._


class SimpleXbar()(implicit p: Parameters) extends Module{

}

object originnal {
  def apply[T <: Data](left: DecoupledIO[T], right: DecoupledIO[T], rightOutFire: Bool, isFlush: Bool) = {
    val valid = RegInit(false.B)
    when (rightOutFire) { valid := false.B }
    when (left.valid && right.ready) { valid := true.B }
    when (isFlush) { valid := false.B }

    left.ready := right.ready
    right.bits := RegEnable(left.bits,0.U.asTypeOf(left.bits), left.valid && right.ready)
    right.valid := valid //&& !isFlush
  }
}

object xbar {
  def apply[T <: Data](left: Vec[DecoupledIO[T]], right: DecoupledIO[T], rightOutFire: Bool, isFlush: Bool) = {
    val in0 = left(0)
    val in1 = left(1)

    val select = Mux(in0.valid, false.B, true.B)

    right.valid := left(select).valid
    right.bits := left(select).bits
    left(select).ready := right.ready
  }
}
