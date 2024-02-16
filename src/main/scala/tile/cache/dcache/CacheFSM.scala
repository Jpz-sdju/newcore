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

class CacheFSM()(implicit p: Parameters) extends Module with Setting {
  val io = IO(new Bundle {
    val req_from_lsu = Flipped(Decoupled(new CacheReq))
    // if miss
    val req_to_Achannel = Decoupled(new CacheReq)
    val resp_from_Achannel = Flipped(Valid(UInt(64.W)))
    // if replace
    val req_to_Cchannel = Decoupled(new CacheReq)
    val release_done = Input(Bool())
    
    // read bus * 8 is for 8 banks
    val data_read_bus = Vec(banks, new SRAMReadBus(gen = UInt(64.W), set = 64 * 8, way = ways))
    val tag_read_bus = new SRAMReadBus(gen = UInt((32 - 6 - 6).W), set = 64, way = ways)
    val meta_read_bus = new SRAMReadBus(gen = UInt(2.W), set = 64, way = ways)
    
    // hit write bus
    val array_write_req = Decoupled(new ArrayWriteBundle)
    //to ade,indicate way to refill
    val array_write_way = Output(Vec(4,Bool()))

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
  val done = io.resp_from_Achannel.valid

  val (s_idle :: s_release
    :: s_acquire :: s_refilling :: Nil) =
    Enum(4)
  val state = RegInit(s_idle)

  // assign array to first read,from LSU
  for(i <- 0 until 8 ){
    io.data_read_bus(i).req.bits.setIdx := s1_req.bits.getTagMetaIdx(s1_req.bits.addr)
    io.data_read_bus(i).req.valid := s1_req.valid 
  }
  
  io.tag_read_bus.req.valid := s1_req.valid
  io.tag_read_bus.req.bits.setIdx := s1_req.bits.getTagMetaIdx(s1_req.bits.addr)

  io.meta_read_bus.req.bits.setIdx := s1_req.bits.getTagMetaIdx(s1_req.bits.addr)
  io.meta_read_bus.req.valid := s1_req.valid

  // array read result
  // val bank_idx = UIntToOH(s2_req.addr(5,3))
  val bank_idx = s2_req.addr(5,3)
  
  // val data = Mux1H(bank_idx, io.data_read_bus).resp.data
  val data =  io.data_read_bus(bank_idx).resp.data
  val tag = io.tag_read_bus.resp.data
  val meta = io.meta_read_bus.resp.data
  // dontTouch(data)
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
  val all_clean = VecInit(meta.map(_ === "b10".U)).asUInt.andR
  io.array_write_way := Mux(miss && has_ava, ava_mask, "b0001".U).asBools

  //2.if not,choose victim way,default way 0001!
  val victim_way = "b0001".U
  //need to read 8 banks data twice,
  // val victim_data_read_req = 

  val miss_and_occupied = miss && meta_hit.asUInt.orR
  dontTouch(miss)

  // when suocun de valid
  when(s2_req_valid) {
    // when reg_valid,if miss
    when(miss) {
      when(state === s_idle) {
        //not avaliable way,need to release
        when(!has_ava && !all_clean){
          state := s_release
        }.otherwise{
          state := s_acquire
        }
      }
      when(state === s_release){
        when(io.release_done){
          state := s_acquire
        }
      }
      when(state === s_acquire && io.req_to_Achannel.fire) {
        state := s_refilling
      }
      when(state === s_refilling && done) {
        state := s_idle
      }


    }

  }

  // only when state is idle,could let more req in!
  s1_req.ready := (state === s_idle) && !miss && io.data_read_bus(0).req.ready

  /*
    REQ to A channel
   */
  io.req_to_Achannel.bits.cmd := s2_req.cmd
  io.req_to_Achannel.bits.addr := s2_req.addr
  io.req_to_Achannel.bits.wdata := s2_req.wdata
  io.req_to_Achannel.bits.wsize := s2_req.wsize
  io.req_to_Achannel.bits.wmask := s2_req.wmask
  io.req_to_Achannel.valid := s2_req_valid && state === s_acquire

  /* 
    REQ to C chaneel
   */
  io.req_to_Cchannel.bits := s2_req
  io.req_to_Cchannel.valid := s2_req_valid && state === s_release

  /*
    ARRAY WRITE REGION
   */
  val array_write = io.array_write_req
  // write is at: ONLY HIT!!
  val write_hit = s2_is_write && !miss

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
  val write_data =  write_hit_data

  val tar_meta = meta(OHToUInt(res_hit)).asTypeOf(new ClientMetadata)
  val write_hit_bank_mask = UIntToOH(s2_req.addr(5, 3))

  array_write.bits.bank_mask :=  write_hit_bank_mask.asBools
  array_write.bits.way_mask :=  res_hit
  array_write.bits.data := write_data
  array_write.bits.tag := s2_req.addr(31, 12)
  // array_write.bits.meta := Mux(s2_is_write, "b11".U,"b10".U)
  array_write.bits.meta := (tar_meta.onAccess(Mux(s2_is_write,"b11".U, "b00".U))._3).state
  array_write.bits.addr := Cat(s2_req.addr(31, 6), 0.U(6.W)) // temporary
  array_write.valid := write_hit

  // when(write_valid && s2_req.addr === "h8000af60".U){
  //   printf("is write req,addr is %x, data is %x\n", s2_req.addr, s2_req.wdata)
  // }


  io.resp_to_lsu.bits.data := Mux(miss, io.resp_from_Achannel.bits, data(OHToUInt(res_hit)))
  io.resp_to_lsu.valid := Mux(miss, io.resp_from_Achannel.valid, s2_req_valid)

}
