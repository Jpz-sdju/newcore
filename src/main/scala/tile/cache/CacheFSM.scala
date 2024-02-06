package tile.cache

import chisel3._
import utils._
import util._
import bus._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4._
import device._
import top.Setting
import dataclass.data
import freechips.rocketchip.diplomaticobjectmodel.model.U
import os.stat

class CacheFSM()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val iread_req = Flipped(Decoupled(new ReadReq))
    // if miss
    val req_to_achannel = Decoupled(new ReadReq)

    // data from downward refill
    val resp_from_achannel = Flipped(DecoupledIO(new ReadRespWithReqInfo))

    // data to frontedn
    val data_to_frontend = (DecoupledIO(new ReadRespWithReqInfo))
  })

  val req = io.iread_req
  val req_reg = RegNext(req.bits)
  val req_valid = RegNext(req.valid)

  val (s_idle :: s_checking
    :: s_send_down :: s_refilling :: Nil) =
    Enum(4)
  val state = RegInit(s_idle)

  // assign array to first read,from frontend
  val array = Module(new IcacheArray)
  array.io.iread_req.bits.addr := req.bits.addr
  array.io.iread_req.bits.size := req.bits.size
  array.io.iread_req.valid := req.valid

  array.io.array_write_req <> DontCare

  // array read result
  val array_resp = array.io.array_read_resp
  array_resp.ready := true.B

  val meta = array_resp.bits.meta
  val data = array_resp.bits.data
  val tag = array_resp.bits.tag

  val meta_hit = VecInit(meta.map(_ === "b11".U))
  val tag_hit = VecInit(tag.map(_ === req_reg.addr(31, 12)))
  val res_hit = VecInit(
    (meta_hit.zip(tag_hit).map { case (a, b) => (a && b).asBool })
  )

  io.req_to_achannel.bits := req_reg
  //only when state is idle,could let more req in!
  io.iread_req.ready := (state === s_idle)
  when(req_valid && !(res_hit.asUInt.orR)) {
    io.req_to_achannel.valid := true.B
    state := s_send_down
  }.otherwise {
    io.req_to_achannel.valid := false.B
  }
  dontTouch(meta_hit)
  dontTouch(tag_hit)

  io.resp_from_achannel <> io.data_to_frontend
}
