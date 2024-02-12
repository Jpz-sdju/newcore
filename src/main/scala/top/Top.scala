package top

import chisel3._, util._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import other._
import freechips.rocketchip.tilelink._
import huancun._
import device._
import freechips.rocketchip.amba.axi4._
import tile._
import difftest._
import _root_.utils._
class Top1()(implicit p: Parameters) extends Module with Setting {
  val io = IO(new Bundle {})
  val sized_resp_data = "h00000008_88000000".U
  val ss = LookupTree(
    "b10".U(2.W),
    List(
      "b00".U(2.W) -> SignExt(sized_resp_data(7, 0),64),
      "b01".U -> sized_resp_data(15, 0),
      "b10".U -> sized_resp_data(31, 0),
      "b11".U -> sized_resp_data(63, 0)
    )
  )
  // val signed_resp_data = SignExt(ss,XLEN)
  dontTouch(ss)
  // dontTouch(signed_resp_data)
  println(ss)
}

import bus._
class UARTIO extends Bundle {
  val out = new Bundle {
    val valid = Output(Bool())
    val ch = Output(UInt(8.W))
  }
  val in = new Bundle {
    val valid = Output(Bool())
    val ch = Input(UInt(8.W))
  }
}
class SimTop()(implicit p: Parameters) extends Module {

  val io = IO(new Bundle {
    val ot = Output(UInt(4.W))
    val it = Input(UInt(4.W))
    val logCtrl = new LogCtrlIO
    val perfInfo = new PerfInfoIO
    val uart = new UARTIO
  })

  val core = Module(new Core())
  io.ot := DontCare

  io.uart.out.valid := false.B
  io.uart.out.ch := 0.U

  io.uart.in.valid := false.B

}
