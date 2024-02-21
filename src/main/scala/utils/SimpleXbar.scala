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
class SimpleBusCrossbar1toN(addressSpace: List[(Long, Long)]) extends Module{
  val io = IO(new Bundle {
    val in = Flipped(new LsuBus)
    val out = Vec(addressSpace.length, new LsuBus)
  })

  val s_idle :: s_resp :: s_error :: Nil = Enum(3)
  val state = RegInit(s_idle)

  // select the output channel according to the address
  val addr = io.in.req.bits.addr
  val outSelVec = VecInit(addressSpace.map(
    range => (addr >= range._1.U && addr < (range._1 + range._2).U)))
  //选择的通道的idx
  val outSelIdx = PriorityEncoder(outSelVec)
  //选择的通道的内容
  val outSel = io.out(outSelIdx)
  val outSelIdxResp = RegEnable(outSelIdx, outSel.req.fire && (state === s_idle))
  val outSelVecResp = RegEnable(outSelVec, outSel.req.fire && (state === s_idle))
  val outSelResp = io.out(outSelIdxResp)
  //进入的req addr是无效地址（不在范围内）
  // val reqInvalidAddr = io.in.req.valid && !outSelVec.asUInt.orR
  val reqInvalidAddr =  !outSelVec.asUInt.orR



  // bind out.req channel
  (io.out zip (outSelVec zip outSelVecResp)).map { case (o, (v, r)) => {
    o.req.bits := io.in.req.bits
    //o.req.valid := v && (io.in.req.valid && (state === s_idle))
    o.req.valid := v && io.in.req.valid && ((state === s_idle) || (r === v && state === s_resp))
    o.resp.ready := v
  }}

  switch (state) {
    is (s_idle) {
      when (outSel.req.fire) { state := s_resp }
      when (reqInvalidAddr) { state := s_error }
    }
    //is (s_resp) { when (outSelResp.resp.fire) { state := s_idle } }
    is (s_resp) {
      when (outSelResp.resp.fire) {
        //如果这次选择的通道和上一个通道一样，并且也成功握手了，直接保持在s_resp
        when (outSelIdx === outSelIdxResp && outSel.req.fire) {
          state := s_resp
        }.otherwise{
          state := s_idle
        }
      }
    }
    is (s_error) { when(io.in.resp.fire){ state := s_idle } }
  }
  // val outError = Wire(new ReadResp)
  // outError.cmd := "b0110".U
  // outError.rdata := 0.U

  io.in.resp.valid := outSelResp.resp.fire || state === s_error
  io.in.resp.bits <> outSelResp.resp.bits
  // io.in.resp.bits.exc.get := state === s_error
  outSelResp.resp.ready := io.in.resp.ready
  io.in.req.ready := outSel.req.ready || reqInvalidAddr

}