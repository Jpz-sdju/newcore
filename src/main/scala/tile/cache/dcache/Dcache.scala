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
  val ade = Module(new DadeChannel(edge))
  val fsm = Module(new DCacheFSM)
  fsm.io.req_from_lsu <> io.req_from_lsu
  fsm.io.req_to_Achannel <> ade.io.acquire_req
  fsm.io.resp_from_Achannel <> ade.io.acquire_resp
  //Essential INFO
  fsm.io.resp_grant_first := ade.io.acquire_grant_first
  fsm.io.resp_grant_done := ade.io.acquire_grant_done
  //data to lsu
  fsm.io.data_to_lsu <> io.read_resp

  
  // ade connect
  ade.io.sourceA <> bus.a
  ade.io.sinkD <> bus.d
  ade.io.sourceE <> bus.e

  // Dcache data refill

}


