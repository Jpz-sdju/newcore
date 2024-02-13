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
  val ade = Module(new iadeChannel(edge))
  val fsm = Module(new DCacheFSM)
  fsm.io.req_from_lsu.bits.addr := io.read_req.bits.addr
  fsm.io.req_from_lsu.bits.cmd := 0.U
  fsm.io.req_from_lsu.bits.wdata := 0.U
  fsm.io.req_from_lsu.bits.wsize := 0.U
  fsm.io.req_from_lsu.bits.wmask := 0.U
  fsm.io.req_from_lsu.valid := io.read_req.valid
  io.read_req.ready := fsm.io.req_from_lsu.ready

  fsm.io.req_to_Achannel <> ade.io.acquire_req
  fsm.io.resp_from_Achannel <> ade.io.acquire_resp
  //Essential INFO
  fsm.io.resp_grant_first := ade.io.acquire_grant_first
  fsm.io.resp_grant_done := ade.io.acquire_grant_done
  //data to frontend
  io.read_resp.bits.req := io.read_req.bits
  io.read_resp.bits.resp.data := fsm.io.resp_to_lsu.bits.data
  io.read_resp.valid := fsm.io.resp_to_lsu.valid
  fsm.io.resp_to_lsu.ready := io.read_resp.ready
  // fsm.io.data_to_lsu <> io.read_resp

  
  // ade connect
  ade.io.sourceA <> bus.a
  ade.io.sinkD <> bus.d
  ade.io.sourceE <> bus.e

  // icache data refill

}


