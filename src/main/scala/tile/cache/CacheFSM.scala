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

class CacheFSM()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val iread_req = Flipped(Decoupled(new ReadReq))
    // if miss
    val req_to_Achannel = Decoupled(new ReadReq)

    // data from downward refill
    val resp_from_Achannel = Flipped(DecoupledIO(new ReadRespFromDown))
    // Essential Info
    val resp_grant_first = Input(Bool())
    val resp_grant_done = Input(Bool())

    // data to frontedn
    val data_to_frontend = (DecoupledIO(new ReadRespWithReqInfo))
  })

  val req = io.iread_req
  val req_reg = RegEnable(req.bits, req.fire)
  val req_valid = RegEnable(req.valid, req.fire)

  //A channel Resp Ino
  val resp = io.resp_from_Achannel
  val first = io.resp_grant_first
  val done = io.resp_grant_done

  val (s_idle :: s_checking
    :: s_send_down :: s_wating :: s_refilling :: Nil) =
    Enum(5)
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

  io.req_to_Achannel.bits := req_reg
  // only when state is idle,could let more req in!
  io.iread_req.ready := (state === s_idle)

  // when suocun de valid

  when(req_valid) {
    // when reg_valid,if miss
    when(!(res_hit.asUInt.orR)) {
      when(state === s_idle && !resp.fire) {
        state := s_send_down
      }
      when(state === s_send_down && io.req_to_Achannel.fire) {
        state := s_wating
      }
      when(state === s_wating && resp.fire) {
        state := s_idle
      }
    }

  }

  io.req_to_Achannel.valid := state === s_send_down

  dontTouch(meta_hit)
  dontTouch(tag_hit)

  io.data_to_frontend.bits.req := req_reg
  io.data_to_frontend.bits.resp.data := io.resp_from_Achannel.bits.data(31, 0)
  io.data_to_frontend.valid := io.req_to_Achannel.valid
  io.resp_from_Achannel.ready := io.data_to_frontend.ready
}
