package tile.cache.dcache

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

class DCacheFSM()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req_from_lsu = Flipped(Decoupled(new CacheReq))
    // if miss
    val req_to_Achannel = Decoupled(new ReadReq)

    // data from downward refill
    val resp_from_Achannel = Flipped(DecoupledIO(new ReadRespFromDown))
    // Essential Info
    val resp_grant_first = Input(Bool())
    val resp_grant_done = Input(Bool())

    // data to lsu
    val data_to_lsu = (DecoupledIO(new ReadResp))
  })

  // LSU req Info
  val req = io.req_from_lsu  
  //reg this req
  val req_reg = RegEnable(req.bits, req.fire)
  val req_valid = RegEnable(req.valid, req.fire)
  val req_is_write = req_reg.cmd 
  val req_write_data = req_reg.wdata
  val req_write_size = req_reg.wsize

  //A channel Resp Info
  val resp = io.resp_from_Achannel
  val first = io.resp_grant_first
  val done = io.resp_grant_done

  val (s_idle :: s_checking
    :: s_send_down :: s_wating :: s_refilling :: Nil) =
    Enum(5)
  val state = RegInit(s_idle)

  // assign array to first read,from LSU
  val array = Module(new DcacheArray)
  array.io.read_req.bits.size := DontCare
  array.io.read_req.bits.addr := req.bits.addr
  array.io.read_req.valid := req.valid

  // array read result
  val array_resp = array.io.array_read_resp
  array_resp.ready := true.B

  val data = array_resp.bits.data
  val meta = array_resp.bits.meta
  val tag = array_resp.bits.tag

  //info valid MUST AT array read resp valid!!
  val array_resp_valid = array_resp.valid
  val meta_hit = VecInit(meta.map(_ === "b11".U))
  val tag_hit = VecInit(tag.map(_ === req_reg.addr(31, 12)))
  val res_hit = VecInit(
    (meta_hit.zip(tag_hit).map { case (a, b) => (a && b).asBool })
  )

  val miss = !res_hit.asUInt.orR && (req_valid ) && !RegNext(done) || (state === s_refilling)
  dontTouch(miss)
  
  // when suocun de valid
  when(req_valid) {
    // when reg_valid,if miss
    when(miss) {
      when(state === s_idle ) {
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
  io.req_from_lsu.ready := (state === s_idle) && !miss

  //NOTE!:must clean low 6bits,clear in DadeChannel.scala
  val this_is_first_grant = !req_reg.addr(5)
  val this_word = req_reg.addr(4,2)
  /* 
    REQ to A channel 
   */
  io.req_to_Achannel.bits.addr := req_reg.addr
  io.req_to_Achannel.bits.size := DontCare
  io.req_to_Achannel.valid := state === s_send_down


  /* 
    ARRAY WRITE REGION  
   */
  val array_write = array.io.array_write_req
  //write is at: 1.req is read, miss,refill 2,req is write ,miss, merge refill. 3, req is write hit,merge refill
  val need_refill = (first || done) && resp.valid
  val need_write_merge = miss && req_is_write && (this_is_first_grant && first || !this_is_first_grant && done)
  val write_valid = Mux(miss, need_refill, req_is_write )

  val merge_data = MaskData(SelectDword(resp.bits.data, req_reg.addr(4,3)),req_reg.wdata, req_reg.wmask)
  val write_data = Mux(need_write_merge, merge_data, resp.bits.data)
  dontTouch(need_write_merge)

  array_write.bits.bank_mask := Mux(first, "b00001111".U, "b11110000".U).asBools
  array_write.bits.way_mask := ("b0001".U(4.W)).asBools
  array_write.bits.data := write_data
  array_write.bits.tag := req_reg.addr(31,12)
  array_write.bits.meta := "b11".U
  
  array_write.valid := write_valid
  dontTouch(array_write)

  //read word idx,compiatble for now newcore
  val word_idx = req_reg.addr(2)
  val muxWord = Mux(word_idx, array_resp.bits.data(0)(63,32), array_resp.bits.data(0)(31,0))

  //need to reg first grant data
  //mux grant data
  val first_grant_data = RegEnable(resp.bits.data, resp.valid && first)
  val first_word = first_grant_data >> (this_word << 5)
  val sec_word = (resp.bits.data & Fill(256,done) ) >> (this_word << 5)
  val word = Mux(this_is_first_grant,first_word,sec_word)

  //temp use data to lsu.valid as store instr compelete sig
  io.data_to_lsu.bits.data := Mux(miss, word, muxWord)
  io.data_to_lsu.valid := Mux(miss, resp.valid && done, array_resp_valid)
  io.resp_from_Achannel.ready := io.data_to_lsu.ready
}
