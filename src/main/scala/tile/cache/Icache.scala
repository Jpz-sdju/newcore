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

  val f = Module(new fake(edge))
  f.io.iread_req <> io.read_req
  f.io.iread_resp <> io.read_resp
  f.io.mem_getPutAcquire <> bus.a
  f.io.mem_grantReleaseAck <> bus.d


}
class fake(edge: TLEdgeOut) extends Module {
  val io = IO(new Bundle {
    val iread_req = Flipped(Decoupled(new ReadReq))
    val iread_resp = (DecoupledIO(new ReadRespWithReqInfo))
    val mem_getPutAcquire = DecoupledIO(new TLBundleA(edge.bundle))
    val mem_grantReleaseAck = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))
  })
  val req = io.iread_req.bits
  val req_valid = RegInit(false.B)
  val req_reg = Reg(new ReadReq)

  when(io.iread_req.valid){
    req_reg := io.iread_req.bits
    req_valid := true.B
  }

  val acqu = edge
    .AcquireBlock(
      fromSource = 0.U,
      toAddress = req_reg.addr,
      lgSize = 2.U,
      growPermissions = 0.U
    )
    ._2
  io.mem_getPutAcquire.bits := acqu
  io.mem_getPutAcquire.valid := req_valid

  io.iread_req.ready := io.mem_getPutAcquire.ready
  // io.iread_req.ready := io.mem_grantReleaseAck.valid
  
  when(io.iread_resp.fire){
    req_valid := false.B
    req_reg := (0.U).asTypeOf(new ReadReq)
  }

  //icache out to frontend 
  io.mem_grantReleaseAck.ready := true.B
  io.iread_resp.bits.req.addr := req_reg.addr
  io.iread_resp.bits.req.size := req_reg.size
  io.iread_resp.bits.resp.data := io.mem_grantReleaseAck.bits.data
  io.iread_resp.valid := io.mem_grantReleaseAck.valid
  
}
