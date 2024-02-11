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
class Top1()(implicit p: Parameters) extends Module {
  val l_soc = LazyModule(new HuanCunExp())
  val soc = Module(l_soc.module)

  val l_simAXIMem = AXI4MemorySlave(
    l_soc.memAXI4SlaveNode,
    128 * 1024 * 1024,
    useBlackBox = true,
    dynamicLatency = false
  )

  val sfd = Module(l_simAXIMem.module)

  l_simAXIMem.io_axi4 <> soc.memory

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

  val dt_te = Module(new DifftestTrapEvent)
  val cycle_cnt = RegInit(0.U(64.W))
  cycle_cnt := cycle_cnt + 1.U
  dt_te.io.clock := clock
  dt_te.io.cycleCnt := cycle_cnt
}

class HuanCunExp()(implicit p: Parameters) extends LazyModule {

  val s = LazyModule(new IUnCache())
  // axi4ram slave node
  val device = new MemoryDevice
  val memRange =
    AddressSet(0x00000000L, 0xffffffffL).subtract(AddressSet(0x0L, 0x7fffffffL))
  val memAXI4SlaveNode = AXI4SlaveNode(
    Seq(
      AXI4SlavePortParameters(
        slaves = Seq(
          AXI4SlaveParameters(
            address = memRange,
            regionType = RegionType.UNCACHED,
            executable = true,
            supportsRead = TransferSizes(1, 64),
            // supportsRead = TransferSizes(1, 8),
            supportsWrite = TransferSizes(1, 64),
            // supportsWrite = TransferSizes(1, 8),
            interleavedId = Some(0),
            resources = device.reg("mem")
          )
        ),
        beatBytes = 8
        // beatBytes = 32
      )
    )
  )

  val l3cache = LazyModule(
    new HuanCun()(new Config((_, _, _) => { case HCCacheParamsKey =>
      HCCacheParameters(
        name = s"L1",
        level = 1,
        inclusive = false,
        clientCaches = Seq(
          CacheParameters(
            sets = 64,
            ways = 4,
            blockGranularity = 7,
            name = "icache"
          )
        ),
        //   ctrl = Some(CacheCtrl(
        //     address = 0x39000000,
        //     numCores = corenum
        //   )),
        // prefetch = Some(huancun.prefetch.BOPParameters()),
        sramClkDivBy2 = true,
        reqField = Seq(),
        echoField = Seq()
        // enableDebug = true
      )
    }))
  )

//   memAXI4SlaveNode := l3cache.node
  memAXI4SlaveNode := TLToAXI4() := TLWidthWidget(
    32
  ) := l3cache.node := s.clientNode

  lazy val module = new HuanCunExpImpl(this)
}
class HuanCunExpImpl(outer: HuanCunExp) extends LazyModuleImp(outer) {

  val io = IO { new Bundle {} }
  val memory = outer.memAXI4SlaveNode.makeIOs()
}
