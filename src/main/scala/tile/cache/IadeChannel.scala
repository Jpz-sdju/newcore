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

class iadeChannel(edge: TLEdgeOut) extends Module with Setting {
  val io = IO(new Bundle {
    val acquire_req = Flipped(Decoupled(new ReadReq))
    //data to FSM,and some info need to transmit 
    val acquire_resp = Decoupled(new ReadRespFromDown)
    val acquire_grant_first = Output(Bool())
    val acquire_grant_done = Output(Bool())


    val sourceA = DecoupledIO(new TLBundleA(edge.bundle))
    val sinkD = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))
    val sourceE = DecoupledIO(new TLBundleE(edge.bundle))
  })
  val req = io.acquire_req.bits
  val req_valid = RegInit(false.B)
  val req_reg = Reg(new ReadReq)

  // register this req
  when(io.acquire_req.valid) {
    req_reg := req
    req_valid := true.B
  }

  val acqu = edge
    .AcquireBlock(
      fromSource = 0.U,
      toAddress = req_reg.addr,
      lgSize = 6.U,
      growPermissions = 0.U
    )
    ._2

  // When Get,Put or Acquire is issued,CAN NOT REMAIN VALID!
  val is_issued = Reg(Bool())
  when(io.sourceA.fire) {
    is_issued := true.B
  }
  io.sourceA.bits := acqu
  io.sourceA.valid := req_valid && !is_issued
  io.acquire_req.ready := io.sourceA.ready


  val (grant_first, _, grant_done, grant_count) = edge.count(io.sinkD)
  //Info need to output to FSM!
  io.acquire_grant_first := grant_first
  io.acquire_grant_done:= grant_done

  //Grant Ack must to free cacheCork IDPool!!
  val sinkD = RegEnable(io.sinkD.bits.sink, io.sinkD.fire)
  val grantAck = edge.GrantAck(
    toSink = sinkD
  )
  io.sourceE.bits := grantAck
  io.sourceE.valid := io.acquire_resp.fire && grant_first


  // When resp is fire,could accept next req
  when(io.acquire_resp.fire) {
    req_valid := false.B
    is_issued := false.B
    req_reg := (0.U).asTypeOf(new ReadReq)
  }

  // icache out to frontend
  io.sinkD.ready := true.B
  io.acquire_resp.bits.data := io.sinkD.bits.data
  io.acquire_resp.valid := io.sinkD.valid

}
