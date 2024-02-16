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

class IadeChannel(edge: TLEdgeOut) extends Module with Setting {
  val io = IO(new Bundle {
    val req_from_fsm = Flipped(Decoupled(new CacheReq))

    val sourceA = DecoupledIO(new TLBundleA(edge.bundle))
    val sinkD = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))
    val sourceE = DecoupledIO(new TLBundleE(edge.bundle))

    val array_write_req = Decoupled(new ArrayWriteBundle)
    //info from fsm
    val array_write_way = Input(Vec(4,Bool()))
    //miss read data to fsm
    val resp_to_fsm = Output(Valid(UInt(64.W)))



  })
  val req = io.req_from_fsm.bits
  val req_valid = RegInit(false.B)
  val req_reg = Reg(new CacheReq)

  val resp = io.sinkD

  // register this req
  when(io.req_from_fsm.valid) {
    req_reg := req
    req_valid := true.B
  }

  val acqu = edge
    .AcquireBlock(
      fromSource = 1.U,
      toAddress = Cat(req_reg.addr(31,6), 0.U(6.W)),
      lgSize = 6.U,
      growPermissions = 0.U
    )
    ._2

  // When Get,Put or Acquire is issued,CAN NOT REMAIN VALID!
  val is_issued = RegInit(false.B)
  when(io.sourceA.fire) {
    is_issued := true.B
  }
  io.sourceA.bits := acqu
  io.sourceA.valid := req_valid && !is_issued
  io.req_from_fsm.ready := io.sourceA.ready


  val (grant_first, _, grant_done, grant_count) = edge.count(io.sinkD)


  //Grant Ack must to free cacheCork IDPool!!
  val sinkD = RegEnable(io.sinkD.bits.sink, io.sinkD.fire)
  val grantAck = edge.GrantAck(
    toSink = sinkD
  )
  io.sourceE.bits := grantAck
  io.sourceE.valid := io.array_write_req.fire && grant_done


  // When resp is fire,could accept next req
  when(io.array_write_req.fire) {
    req_valid := false.B
    is_issued := false.B
    req_reg := (0.U).asTypeOf(new CacheReq)
  }

  /* 
    RECEIVE RESPONSE from downward
   */
  val is_write = req_reg.cmd && req_valid
  val is_read = !req_reg.cmd && req_valid
  val wdata = req_reg.wdata
  val wmask = req_reg.wmask
  val addr =req_reg.addr

  val oridata = io.sinkD.bits.data
  val write_miss_data = LookupTree(
    addr(4, 3),
    List(
      "b00".U -> Cat(
        oridata(255, 64),
        MaskData(oridata(63, 0), wdata, MaskExpand(wmask))
      ),
      "b01".U -> Cat(
        oridata(255, 128),
        MaskData(oridata(127, 64), wdata, MaskExpand(wmask)),
        oridata(63, 0)
      ),
      "b10".U -> Cat(
        oridata(255, 192),
        MaskData(oridata(191, 128), wdata, MaskExpand(wmask)),
        oridata(127, 0)
      ),
      "b11".U -> Cat(
        MaskData(oridata(255, 192), wdata, MaskExpand(wmask)),
        oridata(191, 0)
      )
    )
  )
  val this_is_first_grant = addr(5)
  val this_word = addr(4, 3)

  val refill_time = (grant_first || grant_done) && resp.valid
  val refill_bank_mask = Mux(grant_first, "b00001111".U, "b11110000".U)

  val write_req = io.array_write_req 
  
  write_req.valid := refill_time
  write_req.bits.addr := Cat(addr(31, 6), 0.U(6.W))

  write_req.bits.data := Mux(is_write, write_miss_data, oridata)
  write_req.bits.tag := addr(31, 12)
  write_req.bits.meta := Mux(is_write, "b11".U,"b10".U)

  write_req.bits.bank_mask := refill_bank_mask.asBools
  write_req.bits.way_mask := io.array_write_way
  


  val first_grant_data = RegEnable(resp.bits.data, resp.valid && grant_first)
  val first_word = first_grant_data >> (this_word << log2Up(XLEN))
  val sec_word =
    (resp.bits.data & Fill(256, grant_done)) >> (this_word << log2Up(XLEN))
  val word = Mux(this_is_first_grant, first_word, sec_word)

  io.resp_to_fsm.bits := word
  io.resp_to_fsm.valid := grant_done
  
  // icache out to frontend
  io.sinkD.ready := true.B

}
