package utils

import chisel3._, utils._, util._
import bus._
import freechips.rocketchip.config._
import tile._
import top.Setting
import frontend._

class SimpleXbar()(implicit p: Parameters) extends Module {}

object originnal {
  def apply[T <: Data](
      left: DecoupledIO[T],
      right: DecoupledIO[T],
      rightOutFire: Bool,
      isFlush: Bool
  ) = {
    val valid = RegInit(false.B)
    when(rightOutFire) { valid := false.B }
    when(left.valid && right.ready) { valid := true.B }
    when(isFlush) { valid := false.B }

    left.ready := right.ready
    right.bits := RegEnable(
      left.bits,
      0.U.asTypeOf(left.bits),
      left.valid && right.ready
    )
    right.valid := valid // && !isFlush
  }
}

object xbar {
  def apply[T <: Data](
      left: Vec[DecoupledIO[T]],
      right: DecoupledIO[T],
      rightOutFire: Bool,
      isFlush: Bool
  ) = {
    val in0 = left(0)
    val in1 = left(1)

    val select = Mux(in0.valid, false.B, true.B)

    right.valid := left(select).valid
    right.bits := left(select).bits
    left(select).ready := right.ready
  }
}

object CacheXbar {
  def apply(
      in0: Vec[SRAMReadBus[UInt]],
      in1: Vec[SRAMReadBus[UInt]],
      right: Vec[SRAMReadBus[UInt]]
  ) = {

    // in1 is 8 bank read
    val sel = VecInit(in1.map(_.req.valid)).asUInt.orR
    val select = Mux(sel, true.B, false.B)

    for (i <- 0 until 8) {
      when(select) {
        right(i).req <> in1(i).req
        right(i).resp <> in1(i).resp
        in0(i).req <> DontCare
        in0(i).resp <> DontCare
      }.otherwise {
        right(i).req <> in0(i).req
        right(i).resp <> in0(i).resp
        in1(i).req <> DontCare
        in1(i).resp <> DontCare
      }
    }

  }
  def apply(
      in0: SRAMReadBus[UInt],
      in1: SRAMReadBus[UInt],
      right: SRAMReadBus[UInt]
  ) = {

    // in1 is 8 bank read
    val sel = VecInit(in1.req.valid).asUInt.orR
    val select = Mux(sel, true.B, false.B)

    when(select) {
      right.req <> in1.req
      right.resp <> in1.resp
      in0.req <> DontCare
      in0.resp <> DontCare
    }.otherwise {
      right.req <> in0.req
      right.resp <> in0.resp
      in1.req <> DontCare
      in1.resp <> DontCare
    }

  }
}
