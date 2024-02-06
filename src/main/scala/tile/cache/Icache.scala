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

  // ade connect
  val ade = Module(new iadeChannel(edge))
  ade.io.iread_req <> io.read_req // in
  ade.io.iread_resp <> io.read_resp // out
  ade.io.sourceA <> bus.a
  ade.io.sinkD <> bus.d
  ade.io.sourceE <> bus.e

  // icache data refill
  val array = Module(new IcacheArray)
  array.io.iread_resp <> DontCare
  array.io.iread_req <> DontCare
  array.io.iwrite_tag_req <> DontCare
  array.io.iwrite_data_req <> DontCare
  array.io.iwrite_meta_req <> DontCare
}

class IcacheArray() extends Module {
  val io = IO(new Bundle {
    // set 16kb,4ways,not banked,linesize = 64B
    val iread_req = Flipped(Decoupled(new ReadReq))
    val iread_resp = DecoupledIO(new ReadResp)

    val iwrite_tag_req = Flipped(DecoupledIO(new WriteBus(new tagBundle)))
    val iwrite_data_req = Flipped(DecoupledIO(new WriteBus(new dataBundle)))
    val iwrite_meta_req = Flipped(DecoupledIO(new WriteBus(new metaBundle)))

  })
  io.iread_resp <> DontCare
  io.iread_req <> DontCare
  io.iwrite_tag_req <> DontCare
  io.iwrite_data_req <> DontCare
  io.iwrite_meta_req <> DontCare

  val dataArray = Mem((16 / 4) * 1024, Vec(4, UInt((64 * 8).W)))
  val tagArray = Mem((16 / 4) * 1024, Vec(2, UInt((32 - 6 - 6).W)))
  val metaArray = Mem((16 / 4) * 1024, Vec(2, UInt((2).W)))

  val s = dataArray.read(1.U)
  dontTouch(s)
}
class iadeChannel(edge: TLEdgeOut) extends Module with Setting {
  val io = IO(new Bundle {
    val iread_req = Flipped(Decoupled(new ReadReq))
    val iread_resp = (DecoupledIO(new ReadRespWithReqInfo))
    val sourceA = DecoupledIO(new TLBundleA(edge.bundle))
    val sinkD = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))
    val sourceE = DecoupledIO(new TLBundleE(edge.bundle))
  })
  val req = io.iread_req.bits
  val req_valid = RegInit(false.B)
  val req_reg = Reg(new ReadReq)

  // register this req
  when(io.iread_req.valid) {
    req_reg := req
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
  // grant ack
  val sinkD = RegEnable(io.sinkD.bits.sink, io.sinkD.fire)
  val grantAck = edge.GrantAck(
    toSink = sinkD
  )

  // When Get,Put or Acquire is issued,CAN NOT REMAIN VALID!
  val is_issued = Reg(Bool())
  when(io.sourceA.fire) {
    is_issued := true.B
  }
  io.sourceA.bits := acqu
  io.sourceA.valid := req_valid && !is_issued
  io.iread_req.ready := io.sourceA.ready

  //
  io.sourceE.bits := grantAck
  io.sourceE.valid := io.iread_resp.fire

  // When resp is fire,could accept next req
  when(io.iread_resp.fire) {
    req_valid := false.B
    is_issued := false.B
    req_reg := (0.U).asTypeOf(new ReadReq)
  }

  // With 32bits fetch size,WE MUST Select data!

  val out_data = Mux(
    req_reg.addr(2),
    io.sinkD.bits.data(XLEN - 1, 32),
    io.sinkD.bits.data(31, 0)
  )
  // icache out to frontend
  io.sinkD.ready := true.B
  io.iread_resp.bits.req.addr := req_reg.addr
  io.iread_resp.bits.req.size := req_reg.size
  io.iread_resp.bits.resp.data := out_data
  io.iread_resp.valid := io.sinkD.valid

}
