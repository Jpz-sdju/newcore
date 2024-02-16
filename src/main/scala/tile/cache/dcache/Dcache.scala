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

class Dcache()(implicit p: Parameters) extends LazyModule {
  val clientParameters = TLMasterPortParameters.v1(
    Seq(
      TLMasterParameters.v1(
        name = "Dcache",
        sourceId = IdRange(0, 1 << 1),
        supportsProbe = TransferSizes(64)
        // supportsGet = TransferSizes(LineSize),
        // supportsPutFull = TransferSizes(LineSize),
        // supportsPutPartial = TransferSizes(LineSize)
      )
    ),
    requestFields = Seq(),
    echoFields = Seq()
  )

  val node = TLClientNode(Seq(clientParameters))

  lazy val module = new DcacheImpl(this)
}
class DcacheImpl(outer: Dcache)(implicit p: Parameters)
    extends LazyModuleImp(outer) {
  val io = IO(new Bundle {
    val req_from_lsu = Flipped(DecoupledIO(new CacheReq))
    val read_resp = DecoupledIO((new ReadResp))
  })

  val (bus, edge) = outer.node.out.head
  val acquire_ops = Module(new AcquireTransfer(edge,1))
  // val release_ops = Module(new ReleaseTransfer(edge))
  val fsm = Module(new CacheFSM)
  fsm.io.req_from_lsu <> io.req_from_lsu
  fsm.io.req_to_Achannel <> acquire_ops.io.req_from_fsm
  fsm.io.resp_from_Achannel <> acquire_ops.io.resp_to_fsm
  acquire_ops.io.array_write_way := fsm.io.array_write_way
  
  
  
  /*
  DataArray read region
  */
  
  // assign array to first read,from LSU
  val array = Module(new DcacheArray)
  array.io.data_read_bus <> fsm.io.data_read_bus
  array.io.meta_read_bus <> fsm.io.meta_read_bus
  array.io.tag_read_bus <> fsm.io.tag_read_bus
  
  // data to lsu
  fsm.io.resp_to_lsu <> io.read_resp

  // acquire_ops connect
  acquire_ops.io.sourceA <> bus.a
  acquire_ops.io.sinkD <> bus.d
  acquire_ops.io.sourceE <> bus.e
  
  
  /* 
  RELEASE REGION
  */
  
  

  //   val isGrant = io.mem_grantReleaseAck.bits.opcode === TLMessages.Grant || io.mem_grantReleaseAck.bits.opcode === TLMessages.GrantData
  // val isRelAck = io.mem_grantReleaseAck.bits.opcode === TLMessages.ReleaseAck
  // io.mem_grantReleaseAck.ready := Mux(isGrant, acquireAccess.io.mem_grantAck.ready, Mux(isRelAck, release.io.mem_releaseAck.ready, false.B))



  // Dcache data refill
  val arb = Module(new Arbiter(new ArrayWriteBundle, 2))
  arb.io.in(0) <> fsm.io.array_write_req
  arb.io.in(1) <> acquire_ops.io.array_write_req
  arb.io.out <> array.io.array_write_req
}
