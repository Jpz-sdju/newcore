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
    val read_req = Flipped(DecoupledIO(new ReadReq))
    val read_resp = DecoupledIO((new ReadResp))
  })

  val (bus, edge) = outer.node.out.head

  val f = Module(new fake(edge))
  f.io.read_req <> io.read_req
  f.io.read_resp <> io.read_resp
  f.io.sourceA <> bus.a
  f.io.sinkD <> bus.d
  f.io.sourceE <> bus.e

}
class fake(edge: TLEdgeOut) extends Module with Setting{
  val io = IO(new Bundle {
    val read_req = Flipped(Decoupled(new ReadReq))
    val read_resp = (DecoupledIO(new ReadResp))
    val sourceA = DecoupledIO(new TLBundleA(edge.bundle))
    val sinkD = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))
    val sourceE = DecoupledIO(new TLBundleE(edge.bundle))
  })
  val req = io.read_req.bits
  val req_valid = RegInit(false.B)
  val req_reg = Reg(new ReadReq)

  //register this req
  when(io.read_req.valid) {
    req_reg := req
    req_valid := true.B
  }

  val acqu = edge
    .AcquireBlock(
      fromSource = 1.U,
      toAddress = req_reg.addr,
      lgSize = 2.U,
      growPermissions = 0.U
    )
    ._2
  //grant ack
  val sinkD = RegEnable(io.sinkD.bits.sink, io.sinkD.fire)
  val grantAck = edge.GrantAck(
    toSink = sinkD
  )

  //When Get,Put or Acquire is issued,CAN NOT REMAIN VALID!
  val is_issued = Reg(Bool())
  when(io.sourceA.fire){
    is_issued := true.B
  }
  io.sourceA.bits := acqu
  io.sourceA.valid := req_valid && !is_issued
  io.read_req.ready := io.sourceA.ready

  //
  io.sourceE.bits := grantAck
  io.sourceE.valid := io.read_resp.fire

  //When resp is fire,could accept next req
  when(io.read_resp.fire) {
    req_valid := false.B
    is_issued := false.B
    req_reg := (0.U).asTypeOf(new ReadReq)
  }

  //With 32bits fetch size,WE MUST Select data!

  val out_data = Mux(req_reg.addr(2),io.sinkD.bits.data(XLEN-1, 32), io.sinkD.bits.data(31, 0))
  // Dcache out to frontend
  io.sinkD.ready := true.B

  io.read_resp.bits.data := out_data
  io.read_resp.valid := io.sinkD.valid

}
