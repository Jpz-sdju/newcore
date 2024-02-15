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

class DCacheFSM()(implicit p: Parameters) extends Module with Setting {
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
    val resp_to_lsu = (DecoupledIO(new ReadResp))
  })

  // LSU s1 req Info
  val s1_req = io.req_from_lsu
  // reg  s1_req -> s2_req
  val s2_req = RegEnable(s1_req.bits, (0.U).asTypeOf(new CacheReq), s1_req.fire)
  val s2_req_valid = RegEnable(s1_req.valid, false.B, s1_req.fire)
  when(io.resp_to_lsu.fire) {
    s2_req_valid := false.B
  }
  val s2_is_write = s2_req.cmd && s2_req_valid
  val s2_is_read = !s2_req.cmd && s2_req_valid
  val s2_write_data = s2_req.wdata
  val s2_write_size = s2_req.wsize

  // A channel Resp Info
  val resp = io.resp_from_Achannel
  val first = io.resp_grant_first
  val done = io.resp_grant_done

  val (s_idle :: s_checking
    :: s_send_down :: s_wating :: s_refilling :: Nil) =
    Enum(5)
  val state = RegInit(s_idle)

  // assign array to first read,from LSU
  val array = Module(new DcacheArray)
  array.io.data_read_bus.req.bits.setIdx := s1_req.bits.getDataIdx(s1_req.bits.addr)
  array.io.data_read_bus.req.valid := s1_req.valid
  
  array.io.tag_read_bus.req.valid := s1_req.valid
  array.io.tag_read_bus.req.bits.setIdx := s1_req.bits.getTagMetaIdx(s1_req.bits.addr)

  array.io.meta_read_bus.req.bits.setIdx := s1_req.bits.getTagMetaIdx(s1_req.bits.addr)
  array.io.meta_read_bus.req.valid := s1_req.valid

  // array read result
  val data = array.io.data_read_bus.resp.data
  val tag = array.io.tag_read_bus.resp.data
  val meta = array.io.meta_read_bus.resp.data
  dontTouch(data)
  dontTouch(tag)
  dontTouch(meta)

  val meta_hit = VecInit(meta.map {
    case b => (b === "b11".U || b === "b10".U)
  }) // temp define: 11 is dirty ,10 is clean
  val tag_hit = VecInit(tag.map(_ === s2_req.addr(31, 12)))
  val res_hit = VecInit(
    (meta_hit.zip(tag_hit).map { case (a, b) => (a && b).asBool })
  )
  val miss = !res_hit.asUInt.orR && (s2_req_valid) && !RegNext(done) || (state === s_refilling)
  //1.if has avaliable space,refill
  val ava = VecInit(meta.map {
    case b => (b === "b00".U)
  })
  val ava_mask = PriorityMux(ava, Seq(1.U,2.U,4.U,8.U))
  val has_ava = ava.asUInt.orR

  //2.if not,

  val miss_and_occupied = miss && meta_hit.asUInt.orR
  when(miss_and_occupied && tag(OHToUInt(meta_hit)) === "h8000a".U) {
    printf("tag is %x,meta is 11\n", tag(OHToUInt(meta_hit)) << 12)
  }
  dontTouch(miss)

  // when suocun de valid
  when(s2_req_valid) {
    // when reg_valid,if miss
    when(miss) {
      when(state === s_idle) {
        state := s_send_down
      }
      when(state === s_send_down && io.req_to_Achannel.fire) {
        state := s_wating
      }
      when(state === s_wating && resp.fire && first) {
        state := s_refilling
      }
      when(state === s_refilling && resp.fire && done) {
        state := s_idle
      }
    }

  }

  // only when state is idle,could let more req in!
  s1_req.ready := (state === s_idle) && !miss && array.io.data_read_bus.req.ready

  // NOTE!:must clean low 6bits,clear in DadeChannel.scala
  val this_is_first_grant = !s2_req.addr(5)
  val this_word = s2_req.addr(4, 3)
  /*
    REQ to A channel
   */
  io.req_to_Achannel.bits.addr := s2_req.addr
  io.req_to_Achannel.bits.size := DontCare
  io.req_to_Achannel.valid := state === s_send_down

  /*
    ARRAY WRITE REGION
   */
  val array_write = array.io.array_write_req
  // write is at:
  // 1. req is read, miss, refill
  // 2, req is write, miss, merge refill.
  // 3, req is write, hit, merge refill
  val read_miss = s2_is_read && miss
  val write_miss = s2_is_write && miss
  val write_hit = s2_is_write && !miss

  val refill_time = (first || done) && resp.valid
  val write_valid =
    (read_miss && refill_time) || (write_miss && refill_time) || write_hit

  val need_big_refill =
    (read_miss || write_miss) 

  val oridata = resp.bits.data
  val write_miss_data = LookupTree(
    s2_req.addr(4, 3),
    List(
      "b00".U -> Cat(
        oridata(255, 64),
        MaskData(oridata(63, 0), s2_req.wdata, MaskExpand(s2_req.wmask))
      ),
      "b01".U -> Cat(
        oridata(255, 128),
        MaskData(oridata(127, 64), s2_req.wdata, MaskExpand(s2_req.wmask)),
        oridata(63, 0)
      ),
      "b10".U -> Cat(
        oridata(255, 192),
        MaskData(oridata(191, 128), s2_req.wdata, MaskExpand(s2_req.wmask)),
        oridata(127, 0)
      ),
      "b11".U -> Cat(
        MaskData(oridata(255, 192), s2_req.wdata, MaskExpand(s2_req.wmask)),
        oridata(191, 0)
      )
    )
  )

  val write_hit_data = LookupTree(
    s2_req.addr(4, 3),
    List(
      "b00".U -> Cat(
        0.U(192.W),
        MaskData(
          data(OHToUInt(res_hit)),
          s2_req.wdata,
          MaskExpand(s2_req.wmask)
        )
      ),
      "b01".U -> Cat(
        0.U(128.W),
        MaskData(
          data(OHToUInt(res_hit)),
          s2_req.wdata,
          MaskExpand(s2_req.wmask)
        ),
        0.U(64.W)
      ),
      "b10".U -> Cat(
        0.U(64.W),
        MaskData(
          data(OHToUInt(res_hit)),
          s2_req.wdata,
          MaskExpand(s2_req.wmask)
        ),
        0.U(128.W)
      ),
      "b11".U -> Cat(
        MaskData(
          data(OHToUInt(res_hit)),
          s2_req.wdata,
          MaskExpand(s2_req.wmask)
        ),
        0.U(192.W)
      )
    )
  )
  val write_data = Mux(write_miss, Mux(this_is_first_grant && first || !this_is_first_grant && done,write_miss_data, oridata), Mux(write_hit, write_hit_data, oridata))

  val refill_bank_mask = Mux(first, "b00001111".U, "b11110000".U)
  val write_hit_bank_mask = UIntToOH(s2_req.addr(5, 3))

  array_write.bits.bank_mask := Mux( need_big_refill , refill_bank_mask, write_hit_bank_mask ).asBools
  // array_write.bits.way_mask := ("b0001".U(4.W)).asBools
  array_write.bits.way_mask := Mux(miss,Mux(has_ava, ava_mask, "b0001".U(4.W)), res_hit.asUInt).asBools
  array_write.bits.data := write_data
  array_write.bits.tag := s2_req.addr(31, 12)
  array_write.bits.meta := "b11".U
  array_write.bits.addr := Cat(s2_req.addr(31, 6), 0.U(6.W)) // temporary

  array_write.valid := write_valid

  when(write_valid && s2_req.addr === "h8000af60".U){
    printf("is write req,addr is %x, data is %x\n", s2_req.addr, s2_req.wdata)
  }

  // need to reg first grant data
  // mux grant data
  val first_grant_data = RegEnable(resp.bits.data, resp.valid && first)
  val first_word = first_grant_data >> (this_word << log2Up(XLEN))
  val sec_word =
    (resp.bits.data & Fill(256, done)) >> (this_word << log2Up(XLEN))
  val word = Mux(this_is_first_grant, first_word, sec_word)

  // temp use data to lsu.valid as store instr compelete sig
  io.resp_to_lsu.bits.data := Mux(miss, word, data(OHToUInt(res_hit)))
  io.resp_to_lsu.valid := Mux(miss, resp.valid && done, s2_req_valid)
  io.resp_from_Achannel.ready := io.resp_to_lsu.ready
}
