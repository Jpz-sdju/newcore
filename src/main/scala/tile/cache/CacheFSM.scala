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


  // array read result
  val array_resp = array.io.array_read_resp
  array_resp.ready := true.B

  val meta = array_resp.bits.meta
  val data = array_resp.bits.data
  val tag = array_resp.bits.tag

  //info valid MUST AT array read resp valid!!
  val array_resp_valid = array_resp.valid
  val meta_hit = VecInit(meta.map(_ === "b11".U))
  val tag_hit = VecInit(tag.map(_ === req_reg.addr(31, 12)))
  val res_hit = VecInit(
    (meta_hit.zip(tag_hit).map { case (a, b) => (a && b).asBool })
  )

  val miss = !res_hit.asUInt.orR && req_valid && !RegNext(done) || (state === s_refilling)
  dontTouch(miss)
  
  
  // when suocun de valid
  when(req_valid) {
    // when reg_valid,if miss
    when(miss) {
      when(state === s_idle && !resp.fire) {
        state := s_send_down
      }
      when(state === s_send_down && io.req_to_Achannel.fire) {
        state := s_wating
      }
      when(state === s_wating && resp.fire && first) {
        state := s_refilling
      }
      when(state === s_refilling && resp.fire && done){
        state := s_idle
      }
    }
    
  }
  
  // only when state is idle,could let more req in!
  io.iread_req.ready := (state === s_idle)

  io.req_to_Achannel.bits := req_reg
  io.req_to_Achannel.valid := state === s_send_down


  /* 
    ARRAY WRITE REGION  
   */
  val array_write = array.io.array_write_req
  array_write.bits.bank_mask := Mux(first, "b00001111".U, "b11110000".U).asBools
  array_write.bits.way_mask := ("b0001".U(4.W)).asBools
  array_write.bits.data := io.resp_from_Achannel.bits.data
  array_write.bits.tag := req_reg.addr(31,12)
  array_write.bits.meta := "b11".U
  array_write.valid := (first || done) && resp.valid
  dontTouch(array_write)

  //need to reg first grant
  val first_grant_data = RegEnable(io.resp_from_Achannel.bits.data(31, 0), resp.valid && first)
  //read word idx,compiatble for now newcore
  val word_idx = req_reg.addr(2)
  val muxWord = Mux(word_idx, array_resp.bits.data(0)(63,32), array_resp.bits.data(0)(31,0))

  io.data_to_frontend.bits.req := req_reg
  io.data_to_frontend.bits.resp.data := Mux(miss,first_grant_data, muxWord)
  io.data_to_frontend.valid := Mux(miss, resp.valid && done, array_resp_valid)
  io.resp_from_Achannel.ready := io.data_to_frontend.ready
}
