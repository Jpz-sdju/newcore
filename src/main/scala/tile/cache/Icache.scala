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
import tile.cache.dcache._
class Icache()(implicit p: Parameters) extends LazyModule {
  val clientParameters = TLMasterPortParameters.v1(
    Seq(
      TLMasterParameters.v1(
        name = "icache",
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

  lazy val module = new IcacheImpl(this)
}
class IcacheImpl(outer: Icache)(implicit p: Parameters)
    extends LazyModuleImp(outer) {
  val io = IO(new Bundle {
    val read_req = Flipped(DecoupledIO(new ReadReq))
    val read_resp = DecoupledIO((new ReadRespWithReqInfo))
  })

  val (bus, edge) = outer.node.out.head
  val acquire_ops = Module(new AcquireTransfer(edge,0))
  val fsm = Module(new CacheFSM)
  fsm.io.req_from_lsu.req.bits.addr := io.read_req.bits.addr
  fsm.io.req_from_lsu.req.bits.cmd := 0.U
  fsm.io.req_from_lsu.req.bits.wdata := 0.U
  fsm.io.req_from_lsu.req.bits.wsize := 0.U
  fsm.io.req_from_lsu.req.bits.wmask := 0.U
  fsm.io.req_from_lsu.req.valid := io.read_req.valid
  io.read_req.ready := fsm.io.req_from_lsu.req.ready


  fsm.io.req_to_Achannel <> acquire_ops.io.req_from_fsm
  fsm.io.resp_from_Achannel <> acquire_ops.io.resp_to_fsm
  acquire_ops.io.array_write_way := fsm.io.array_write_way

  fsm.io.req_to_Cchannel <> DontCare
  fsm.io.release_done <> DontCare
  /*
    DataArray read region
  */

  // assign array to first read,from LSU
  val array = Module(new DcacheArray)
  array.io.data_read_bus <> fsm.io.data_read_bus
  array.io.meta_read_bus <> fsm.io.meta_read_bus
  array.io.tag_read_bus <> fsm.io.tag_read_bus

  fsm.io.array_write_req <> DontCare


  // data to frontend
  io.read_resp.bits.req := io.read_req.bits
  io.read_resp.bits.resp.data := fsm.io.req_from_lsu.resp.bits
  io.read_resp.valid := fsm.io.req_from_lsu.resp.valid
  fsm.io.req_from_lsu.resp.ready := io.read_resp.ready

  // acquire_ops connect
  acquire_ops.io.sourceA <> bus.a
  acquire_ops.io.sinkD <> bus.d
  acquire_ops.io.sourceE <> bus.e

  // icache data refill

  acquire_ops.io.array_write_req <> array.io.array_write_req
}
