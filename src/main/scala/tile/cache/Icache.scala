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
  array.io.iread_req <> io.read_req
  array.io.array_read_resp <> DontCare
  array.io.array_write_req <> DontCare
}

class IcacheArray() extends Module with Setting {
  val io = IO(new Bundle {
    // set 16kb,4ways,not banked,linesize = 64B
    val iread_req = Flipped(Decoupled(new ReadReq))

    val array_read_resp = DecoupledIO(new ArrayReadBundle)
    val array_write_req = Flipped(Decoupled(new iRefillBundle))
  })
  val req = io.iread_req
  val resp = io.array_read_resp

  // three arrays
  val dataArray = Array.fill(8)(
    SyncReadMem(64, Vec(ways, UInt((64).W)))
  )
  val tagArray = SyncReadMem(64, Vec(ways, UInt((32 - 6 - 6).W)))
  val metaArray = SyncReadMem(64, Vec(ways, UInt((2).W)))

  // 0-6 offset,6 idx,20tag
  val bank_idx = io.iread_req.bits.addr(5, 3)
  val set_idx = io.iread_req.bits.addr(11, 6)
  val read_tag = Wire(Vec(4, UInt(20.W)))
  val read_data = WireInit(VecInit(Seq.fill(ways)(0.U(64.W))))
  val read_meta = Wire(Vec(4, UInt(2.W)))

  for (id <- 0 until 8) {
    val cond = id.U === bank_idx
    when(cond) {
      read_data := dataArray(id).read(set_idx, cond.asBool)
    }
  }
  read_tag := tagArray.read(set_idx)
  read_meta := metaArray.read(set_idx)

  // assign 4 ways RESULT to outer
  resp.bits.data := read_data
  resp.bits.tag := read_tag
  resp.bits.meta := read_meta
  resp.valid := RegNext(req.valid)

  dontTouch(read_data)

  req.ready := true.B
  io.array_read_resp <> DontCare
  io.array_write_req <> DontCare
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
