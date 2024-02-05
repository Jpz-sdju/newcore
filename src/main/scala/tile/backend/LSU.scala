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

    val read_req = Decoupled(new ReadReq)
    // val read_resp = Decoupled(new ReadResp)
  })
    io.in.ready := true.B

    val in = io.in.bits
    val load = in.isLoad
    val store = in.isStore
    val need_op = (load || store) && io.in.valid
    val ori = Seq("b00".U, "b01".U,"b10".U,"b11".U).zip(Seq("b0000".U,"b0010".U,"b0100".U,"b1000".U))
    val size = LookupTree(in.lsSize,ori)

    io.read_req.valid := need_op
    io.read_req.bits.addr := in.lsAddr
    io.read_req.bits.size := size
}
