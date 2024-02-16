// package tile.cache.dcache

// import chisel3._
// import chisel3.util._
// import chisel3.util.experimental.BoringUtils
// import chisel3.experimental.IO
// import utils._
// import bus._
// import freechips.rocketchip.tilelink._
// import chipsalliance.rocketchip.config._
// import freechips.rocketchip.tilelink.MemoryOpCategories._
// import top._

// class ReleaseTransfer(edge: TLEdgeOut)(implicit val p: Parameters) extends Module with Setting {
//   val io = IO(new Bundle {
//     val req_from_fsm = Flipped(Decoupled(new CacheReq))


//     // read bus * 8 is for 8 banks
//     val data_read_bus = new SRAMReadBus(gen = UInt(64.W), set = 64 * 8, way = ways)
//     val tag_read_bus = new SRAMReadBus(gen = UInt((32 - 6 - 6).W), set = 64, way = ways)
//     val meta_read_bus = new SRAMReadBus(gen = UInt(2.W), set = 64, way = ways)
//     val four_bank_read = Output(Valid(Bool()))
//     val victimCoh = Input(new ClientMetadata)

//     val sourceC = DecoupledIO(new TLBundleC(edge.bundle))
//     val sinkD = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))


//   }) 

//   val req = io.req_from_fsm
//   val req_valid = RegInit(false.B)
//   val req_reg = Reg(new CacheReq)
//   val cnt = RegInit(false.B)

//   // register this req
//   when(io.req_from_fsm.valid) {
//     req_reg := req
//     req_valid := true.B
//   }

//   val state = RegInit(s_idle)
//   val s_idle :: s_release :: s_releaseD :: s_releaseA :: Nil = Enum(4)
//   val (rel_first, _, rel_done, rel_count) = edge.count(io.sourceC)


//   // assign array to first read,from LSU
//   io.data_read_bus.req.bits.setIdx := req_reg.getDataIdx(req_reg.addr)
//   io.data_read_bus.req.valid := req_valid
  
//   io.tag_read_bus.req.valid := req_valid
//   io.tag_read_bus.req.bits.setIdx := req_reg.getTagMetaIdx(req_reg.addr)

//   io.meta_read_bus.req.bits.setIdx := req_reg.getTagMetaIdx(req_reg.addr)
//   io.meta_read_bus.req.valid := req_valid
//   io.four_bank_read.bits := state === s_release
//   io.four_bank_read.valid := req_valid


//   val isRelAck = io.sinkD.bits.opcode === TLMessages.ReleaseAck
//   val victimCoh = io.victimCoh
//   val (release_has_dirty_data, release_shrink_param, release_new_coh) = victimCoh.onCacheControl(M_FLUSH)

//   val release = edge.Release(
//     fromSource = 1.U, 
//     toAddress = req_reg.addr, 
//     lgSize = log2Ceil(LineSize).U, 
//     shrinkPermissions = release_shrink_param)._2

//   val releaseData = edge.Release(
//     fromSource = 1.U, 
//     toAddress = req_reg.addr, 
//     lgSize = log2Ceil(LineSize).U, 
//     shrinkPermissions = release_shrink_param, 
//     data = rData)._2

// }