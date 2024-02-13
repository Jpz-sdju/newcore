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

  // LSU req Info
  val req = io.req_from_lsu
  // reg this req
  val req_reg = RegEnable(req.bits, (0.U).asTypeOf(new CacheReq), req.fire)
  val req_valid = RegEnable(req.valid, req.fire)
  when(io.resp_to_lsu.fire) {
    req_valid := false.B
  }
  val req_is_write = req_reg.cmd && req_valid
  val req_write_data = req_reg.wdata
  val req_write_size = req_reg.wsize

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
  array.io.data_read_bus.req.bits.setIdx := req.bits.addr(11, 3)
  array.io.data_read_bus.req.valid := req.valid

  array.io.tag_read_bus.req.bits.setIdx := req.bits.addr(11, 6)
  array.io.tag_read_bus.req.valid := req.valid

  array.io.meta_read_bus.req.bits.setIdx := req.bits.addr(11, 6)
  array.io.meta_read_bus.req.valid := req.valid

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
  val tag_hit = VecInit(tag.map(_ === req_reg.addr(31, 12)))
  val res_hit = VecInit(
    (meta_hit.zip(tag_hit).map { case (a, b) => (a && b).asBool })
  )

  val miss = !res_hit.asUInt.orR && (req_valid) && !RegNext(
    done
  ) || (state === s_refilling)
  val miss_and_occupied = miss && meta_hit.asUInt.orR
  when(miss_and_occupied) {
    printf("tag is %x,meta is 11\n", tag(OHToUInt(meta_hit)) << 12)
  }
  dontTouch(miss)

  // when suocun de valid
  when(req_valid) {
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
  req.ready := (state === s_idle) && !miss && array.io.data_read_bus.req.ready

  // NOTE!:must clean low 6bits,clear in DadeChannel.scala
  val this_is_first_grant = !req_reg.addr(5)
  val this_word = req_reg.addr(4, 3)
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
  // write is at:
  // 1. req is read, miss, refill
  // 2, req is write, miss, merge refill.
  // 3, req is write, hit, merge refill
  val read_miss = !req_reg.cmd && req_valid && miss
  val write_miss = req_is_write && miss
  val write_hit = req_is_write && !miss

  val refill_time = (first || done) && resp.valid
  val write_valid =
    (read_miss && refill_time) || (write_miss && refill_time) || write_hit

  val need_write_merge =
    miss && req_is_write && (this_is_first_grant && first || !this_is_first_grant && done)

  val oridata = resp.bits.data
  val write_miss_data = LookupTree(req_reg.addr(4,3),List(
    "b00".U -> Cat(
      oridata(255, 64),
      MaskData(oridata(63, 0), req_reg.wdata, MaskExpand(req_reg.wmask))
    ),
    "b01".U -> Cat(
      oridata(255, 128),
      MaskData(oridata(127, 64), req_reg.wdata, MaskExpand(req_reg.wmask)),
      oridata(63,0)
    ),
    "b10".U -> Cat(
      oridata(255, 192),
      MaskData(oridata(191, 128), req_reg.wdata, MaskExpand(req_reg.wmask)),
      oridata(127,0)
    ),
    "b11".U -> Cat(
      MaskData(oridata(255, 192), req_reg.wdata, MaskExpand(req_reg.wmask)),
      oridata(191,0)
    )
  ))

  val write_hit_data = LookupTree(req_reg.addr(4,3) ,List(
    "b00".U -> Cat(
      oridata(255, 64),
      MaskData(data(OHToUInt(res_hit)), req_reg.wdata, MaskExpand(req_reg.wmask))
    ),
    "b01".U -> Cat(
      oridata(255, 128),
      MaskData(data(OHToUInt(res_hit)), req_reg.wdata, MaskExpand(req_reg.wmask)),
      oridata(63,0)
    ),
    "b10".U -> Cat(
      oridata(255, 192),
      MaskData(data(OHToUInt(res_hit)), req_reg.wdata, MaskExpand(req_reg.wmask)),
      oridata(127,0)
    ),
    "b11".U -> Cat(
      MaskData(data(OHToUInt(res_hit)), req_reg.wdata, MaskExpand(req_reg.wmask)),
      oridata(191,0)
    )
  ))
  val write_data = Mux(need_write_merge, write_miss_data, Mux(write_hit, write_hit_data, oridata))

  val refill_bank_mask = Mux(first, "b00001111".U, "b11110000".U)
  val write_hit_bank_mask = UIntToOH(req_reg.addr(5,3))

  array_write.bits.bank_mask := Mux(need_write_merge, refill_bank_mask, write_hit_bank_mask).asBools
  array_write.bits.way_mask := ("b0001".U(4.W)).asBools
  array_write.bits.data := write_data
  array_write.bits.tag := req_reg.addr(31, 12)
  array_write.bits.meta := "b11".U
  array_write.bits.addr := Cat(req_reg.addr(31, 6), 0.U(6.W)) // temporary

  array_write.valid := write_valid
  dontTouch(array_write)

  // need to reg first grant data
  // mux grant data
  val first_grant_data = RegEnable(resp.bits.data, resp.valid && first)
  val first_word = first_grant_data >> (this_word << log2Up(XLEN))
  val sec_word =
    (resp.bits.data & Fill(256, done)) >> (this_word << log2Up(XLEN))
  val word = Mux(this_is_first_grant, first_word, sec_word)

  // temp use data to lsu.valid as store instr compelete sig
  io.resp_to_lsu.bits.data := Mux(miss, word, data(0))
  io.resp_to_lsu.valid := Mux(miss, resp.valid && done, req_valid)
  io.resp_from_Achannel.ready := io.resp_to_lsu.ready
}
