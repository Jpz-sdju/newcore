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

    
  })
    io.in.ready := true.B
    io.read_resp.ready := true.B

    val in = io.in.bits
    val load = in.isLoad
    val store = in.isStore
    val need_op = (load || store) && io.in.valid
    val ori = Seq("b00".U, "b01".U,"b10".U,"b11".U).zip(Seq("b0000".U,"b0010".U,"b0100".U,"b1000".U))
    val size = LookupTree(in.lsSize,ori)

    io.d_req.valid := need_op
    io.d_req.bits.cmd := store//1 is write,0 is read
    io.d_req.bits.addr := in.lsAddr
    io.d_req.bits.wdata := in.storeData
    io.d_req.bits.wsize := size



    dontTouch(io.in)
}
