package frontend

import chisel3._
import utils._
import util._
import bus._
import frontend._
import backend._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4._
import device._

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
    val read_resp = DecoupledIO((new ReadResp))
  })

  val (bus, edge) = outer.node.out.head

  val f = Module(new fake(edge))
  f.io.iread <> io.read_req

  f.io.mem_getPutAcquire <> bus.a
  f.io.mem_grantReleaseAck <> bus.d

}
class fake(edge: TLEdgeOut) extends Module {
  val io = IO(new Bundle {
    val iread = Flipped(Decoupled(new ReadReq))
    val mem_getPutAcquire = DecoupledIO(new TLBundleA(edge.bundle))
    val mem_grantReleaseAck = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))
  })

  val acqu = edge
    .AcquireBlock(
      fromSource = 0.U,
      toAddress = io.iread.bits.addr(4, 0),
      lgSize = 2.U,
      growPermissions = 0.U
    )
    ._2
  io.mem_getPutAcquire.bits := acqu
  io.mem_getPutAcquire.valid := io.iread.valid
  io.iread.ready := io.mem_getPutAcquire.ready

  io.mem_grantReleaseAck.ready := true.B
}
