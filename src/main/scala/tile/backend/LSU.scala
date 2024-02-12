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
import utils._

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

  io.read_resp.ready := true.B

  val in = io.in.bits
  val load = in.isLoad
  val store = in.isStore
  val need_op = (load || store) && io.in.valid


  io.d_req.valid := need_op
  io.d_req.bits.cmd := store // 1 is write,0 is read
  io.d_req.bits.addr := in.lsAddr
  io.d_req.bits.wdata := genWdata(in.storeData, in.lsSize)
  io.d_req.bits.wsize := in.lsSize
  io.d_req.bits.wmask := genWmask(in.lsAddr, in.lsSize)

  /*
  WB REGION
   */
  val had_inflight = RegInit(false.B)
  when(need_op){
    had_inflight := true.B
  }.elsewhen(io.read_resp.valid){
    had_inflight := false.B
  }
  io.out <> io.in
  // only when lsu out to wb,could let more in
  io.in.ready := io.d_req.ready
  // when is l/s instr and resp valid OR alu/other in.valid is true!!
  io.out.valid := io.read_resp.valid && had_inflight || (io.in.valid && !need_op)



  val read_data_sel = LookupTree(in.lsAddr(2, 0), List(
    "b000".U -> io.read_resp.bits.data(63, 0),
    "b001".U -> io.read_resp.bits.data(63, 8),
    "b010".U -> io.read_resp.bits.data(63, 16),
    "b011".U -> io.read_resp.bits.data(63, 24),
    "b100".U -> io.read_resp.bits.data(63, 32),
    "b101".U -> io.read_resp.bits.data(63, 40),
    "b110".U -> io.read_resp.bits.data(63, 48),
    "b111".U -> io.read_resp.bits.data(63, 56)
  ))
  val read_data_ext = LookupTree(in.cf.ctrl.fuOpType, List(
    LSUOpType.lb   -> SignExt(read_data_sel(7, 0) , XLEN),
    LSUOpType.lh   -> SignExt(read_data_sel(15, 0), XLEN),
    LSUOpType.lw   -> SignExt(read_data_sel(31, 0), XLEN),
    LSUOpType.lbu  -> ZeroExt(read_data_sel(7, 0) , XLEN),
    LSUOpType.lhu  -> ZeroExt(read_data_sel(15, 0), XLEN),
    LSUOpType.lwu  -> ZeroExt(read_data_sel(31, 0), XLEN),
    LSUOpType.ld   -> read_data_sel
  ))
  io.out.bits.WRITE_BACK := Mux(
    had_inflight,
    read_data_ext,
    io.in.bits.WRITE_BACK
  )

  dontTouch(io.in)
}
