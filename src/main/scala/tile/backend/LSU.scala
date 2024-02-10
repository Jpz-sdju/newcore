package tile.backend

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import bus._
import tile._
import freechips.rocketchip.util.DontTouch
import top.Setting
import utils.PipelineConnect
import _root_.tile.frontend._
import utils.LookupTree

class LSU()(implicit p: Parameters) extends Module with Setting {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new PipelineBundle))

    val d_req = Decoupled(new CacheReq)
    val read_resp = Flipped(Decoupled(new ReadResp))

    val out = Decoupled(new PipelineBundle)
  })
  def genWmask(addr: UInt, sizeEncode: UInt): UInt = {
    LookupTree(
      sizeEncode,
      List(
        "b00".U -> 0x1.U, // 0001 << addr(2:0)
        "b01".U -> 0x3.U, // 0011
        "b10".U -> 0xf.U, // 1111
        "b11".U -> 0xff.U // 11111111
      )
    ) << addr(2, 0)
  }
  def genWdata(data: UInt, sizeEncode: UInt): UInt = {
    LookupTree(
      sizeEncode,
      List(
        "b00".U -> Fill(8, data(7, 0)),
        "b01".U -> Fill(4, data(15, 0)),
        "b10".U -> Fill(2, data(31, 0)),
        "b11".U -> data
      )
    )
  }
  io.in.ready := true.B
  io.read_resp.ready := true.B

  val in = io.in.bits
  val load = in.isLoad
  val store = in.isStore
  val need_op = (load || store) && io.in.valid
  val ori = Seq("b00".U, "b01".U, "b10".U, "b11".U).zip(
    Seq("b0000".U, "b0010".U, "b0100".U, "b1000".U)
  )
  val size = LookupTree(in.lsSize, ori)

  io.d_req.valid := need_op
  io.d_req.bits.cmd := store // 1 is write,0 is read
  io.d_req.bits.addr := in.lsAddr
  // io.d_req.bits.wdata := genWdata(in.storeData, in.lsSize)
  io.d_req.bits.wdata := 1.U
  io.d_req.bits.wsize := in.lsSize
  io.d_req.bits.wmask := genWmask(in.lsAddr, in.lsSize)


  /* 
    WB REGION
   */

   io.out <> io.in
   io.out.valid := io.read_resp.valid
   io.out.bits.WRITE_BACK := io.read_resp.bits.data

  dontTouch(io.in)
}
