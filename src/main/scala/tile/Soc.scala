package tile

import chisel3._
import utils._
import util._
import bus._
import frontend._
import backend._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4._
import device._
import sim._
import difftest._


class Soc()(implicit p: Parameters) extends Module {
    val io = IO(new Bundle{
    // val logCtrl = new LogCtrlIO
    // val perfInfo = new PerfInfoIO
    val uart = new UARTIO
    //val test = Input(Bool())
  })
  val l_core = LazyModule(new CoreWithL1())
  val core = Module(l_core.module)

  core.iread_req := DontCare
  core.iread_resp := DontCare

  val l_simAXIMem = AXI4MemorySlave(
    l_core.memAXI4SlaveNode,
    128 * 1024 * 1024,
    useBlackBox = true,
    dynamicLatency = false
  )
  val simAxi = Module(l_simAXIMem.module)
  core.memory <> l_simAXIMem.io_axi4

  val l_mmio = LazyModule(new SimMMIO(l_core.peripheralNode.in.head._2))
  val mmio = Module(l_mmio.module)
  l_mmio.io_axi4 <> core.peripheral

  io.uart <> mmio.io.uart

}
