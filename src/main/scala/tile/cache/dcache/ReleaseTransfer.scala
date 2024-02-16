package tile.cache.dcache

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import chisel3.experimental.IO
import utils._
import bus._
import freechips.rocketchip.tilelink._
import chipsalliance.rocketchip.config._
import freechips.rocketchip.tilelink.MemoryOpCategories._
import top._

class ReleaseTransfer(edge: TLEdgeOut)(implicit val p: Parameters) extends Module with Setting {
  val io = IO(new Bundle {
    val req_from_fsm = Flipped(Decoupled(new CacheReq))


    // read bus * 8 is for 8 banks
    val data_read_bus = Vec(banks, new SRAMReadBus(gen = UInt(64.W), set = 64 , way = ways))
    val tag_read_bus = new SRAMReadBus(gen = UInt((32 - 6 - 6).W), set = 64, way = ways)
    val meta_read_bus = new SRAMReadBus(gen = UInt(2.W), set = 64, way = ways)

    val sourceC = DecoupledIO(new TLBundleC(edge.bundle))
    val sinkD = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))

    val releace_done = Output(Bool())
  }) 

  val req = io.req_from_fsm
  val req_valid = RegInit(false.B)
  val req_reg = Reg(new CacheReq)
  val cnt = RegInit(false.B)

  // register this req
  when(io.req_from_fsm.valid) {
    req_reg := req.bits
    req_valid := true.B
    cnt := false.B
  }
  when(io.data_read_bus(0).req.fire){
    cnt := true.B
  }

  val s_idle :: s_release :: s_releaseD :: s_releaseA :: Nil = Enum(4)
  val state = RegInit(s_idle)
  val (rel_first, _, rel_done, rel_count) = edge.count(io.sourceC)
  req.ready := state === s_idle


  // assign array to first read,from LSU
  for(i <- 0 until 4){
      io.data_read_bus(i).req.bits.setIdx := req_reg.getTagMetaIdx(req_reg.addr)
      io.data_read_bus(i).req.valid := req_valid && !cnt
  }
  for(i <- 4 until 8){
      io.data_read_bus(i).req.bits.setIdx := req_reg.getTagMetaIdx(req_reg.addr)
      io.data_read_bus(i).req.valid := req_valid && cnt
  }
  
  io.tag_read_bus.req.valid := req_valid
  io.tag_read_bus.req.bits.setIdx := req_reg.getTagMetaIdx(req_reg.addr)

  io.meta_read_bus.req.bits.setIdx := req_reg.getTagMetaIdx(req_reg.addr)
  io.meta_read_bus.req.valid := req_valid

  val meta = io.meta_read_bus.resp.data
  val tag = io.tag_read_bus.resp.data


  val isRelAck = io.sinkD.bits.opcode === TLMessages.ReleaseAck
  val victimCoh = meta(0).asTypeOf(new ClientMetadata)
  val (release_has_dirty_data, release_shrink_param, release_new_coh) = victimCoh.onCacheControl(M_FLUSH)

  //temp !! DEFAULT 0 WAY!!!
  val four_bank_data = Cat(io.data_read_bus(3).resp.data(0.U),io.data_read_bus(2).resp.data(0.U),io.data_read_bus(1).resp.data(0.U),io.data_read_bus(0).resp.data(0.U))
  val four_bank_data1 = Cat(io.data_read_bus(7).resp.data(0.U),io.data_read_bus(6).resp.data(0.U),io.data_read_bus(5).resp.data(0.U),io.data_read_bus(4).resp.data(0.U))


  val release_data = Mux(!cnt, four_bank_data, four_bank_data1)
  val release_addr = Cat(tag(0), req_reg.addr(11,6), 0.U(6.W))
  val release = edge.Release(
    fromSource = 1.U, 
    toAddress = release_addr, 
    lgSize = log2Ceil(LineSize).U, 
    shrinkPermissions = release_shrink_param)._2

  val releaseData = edge.Release(
    fromSource = 1.U, 
    toAddress = release_addr, 
    lgSize = log2Ceil(LineSize).U, 
    shrinkPermissions = release_shrink_param, 
    data = release_data)._2


  switch(state){
    is(s_idle){
        when(io.req_from_fsm.valid){
            state := Mux(release_has_dirty_data, s_releaseD, s_release)
        }
    }
    is (s_release) {
      when (io.sourceC.fire) {
        state := s_releaseA
      }
    }
    is (s_releaseD) {
      when (rel_done) {
        state := s_releaseA
      }
    }
    is (s_releaseA) {
      when (io.sinkD.fire && isRelAck) {
        state := s_idle
        req_reg := (0.U).asTypeOf(new CacheReq)
        req_valid := false.B
      }
    }
  }
  io.sourceC.bits := Mux(meta(0) === "b11".U, releaseData, release)
  io.sourceC.valid := state === s_release || state === s_releaseD
  io.sinkD.ready := state === s_releaseA

  io.releace_done := state === s_releaseA
}