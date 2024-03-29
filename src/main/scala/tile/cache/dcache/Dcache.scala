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
    extends LazyModuleImp(outer) with Setting{
  val io = IO(new Bundle {
    val req_from_lsu = Flipped(new LsuBus)
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
  
  // acquire_ops connect
  acquire_ops.io.sourceA <> bus.a
  acquire_ops.io.sinkD <> bus.d
  acquire_ops.io.sourceE <> bus.e
  
  
  /* 
  RELEASE REGION
  */
  val release = Module(new ReleaseTransfer(edge))

  release.io.req_from_fsm <> fsm.io.req_to_Cchannel
  
  release.io.sourceC <> bus.c
  release.io.sinkD <> bus.d
  fsm.io.release_done := release.io.releace_done
  val isGrant = bus.d.bits.opcode === TLMessages.Grant || bus.d.bits.opcode === TLMessages.GrantData
  val isRelAck = bus.d.bits.opcode === TLMessages.ReleaseAck
  bus.d.ready := Mux(isGrant, acquire_ops.io.sinkD.ready, Mux(isRelAck, release.io.sinkD.ready, false.B))



  // Dcache data read
  CacheXbar(fsm.io.data_read_bus, release.io.data_read_bus, array.io.data_read_bus)
  CacheXbar(fsm.io.tag_read_bus, release.io.tag_read_bus, array.io.tag_read_bus)
  CacheXbar(fsm.io.meta_read_bus, release.io.meta_read_bus, array.io.meta_read_bus)



  // Dcache data refill
  val arb = Module(new Arbiter(new ArrayWriteBundle, 2))
  arb.io.in(0) <> fsm.io.array_write_req
  arb.io.in(1) <> acquire_ops.io.array_write_req
  arb.io.out <> array.io.array_write_req
}
