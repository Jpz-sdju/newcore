package top

import chisel3._,util._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import other._
import freechips.rocketchip.tilelink._
import huancun._
import device._
import freechips.rocketchip.amba.axi4._

class Top1()(implicit p: Parameters) extends Module {
    val l_soc = LazyModule(new HuanCunExp())
    val soc = Module(l_soc.module)
    
    val l_simAXIMem = AXI4MemorySlave(
      l_soc.memAXI4SlaveNode,
      128 * 1024 * 1024,
      useBlackBox = true,
      dynamicLatency = false
      )
      
      val sfd= Module(l_simAXIMem.module)

  l_simAXIMem.io_axi4 <> soc.memory



}

class Top()(implicit p: Parameters) extends Module {

  val io = IO(new Bundle {})
  val l_soc = LazyModule(new simpleCase())
  val soc = Module(l_soc.module)

  val target = l_soc.iun0.clientNode
  val target_1 = l_soc.timer.node
}

class HuanCunExp()(implicit p: Parameters) extends LazyModule {

    val s = LazyModule(new IUnCache())
    //axi4ram slave node
  val device = new MemoryDevice
  val memRange = AddressSet(0x00000000L, 0xffffffffL).subtract(AddressSet(0x0L, 0x7fffffffL))
  val memAXI4SlaveNode = AXI4SlaveNode(Seq(
    AXI4SlavePortParameters(
      slaves = Seq(
        AXI4SlaveParameters(
          address = memRange,
          regionType = RegionType.UNCACHED,
          executable = true,
          supportsRead = TransferSizes(1, 64),
          //supportsRead = TransferSizes(1, 8),
          supportsWrite = TransferSizes(1, 64),
          //supportsWrite = TransferSizes(1, 8),
          interleavedId = Some(0),
          resources = device.reg("mem")
        )
      ),
      beatBytes = 8
      // beatBytes = 32
    )
  ))

  val l3cache = LazyModule(new HuanCun()(new Config((_, _, _) => {
    case HCCacheParamsKey => HCCacheParameters(
      name = s"L1",
      level = 1,
      inclusive = false,
      clientCaches = Seq(CacheParameters(sets = 64, ways = 4, blockGranularity = 7, name = "icache")),
    //   ctrl = Some(CacheCtrl(
    //     address = 0x39000000,
    //     numCores = corenum
    //   )),
      //prefetch = Some(huancun.prefetch.BOPParameters()),
      sramClkDivBy2 = true,
      reqField = Seq(),
      echoField = Seq()
      //enableDebug = true
    )
  })))

//   memAXI4SlaveNode := l3cache.node
  memAXI4SlaveNode :=   TLToAXI4():=  TLWidthWidget(32) :=l3cache.node := s.clientNode





  lazy val module = new HuanCunExpImpl(this)
}
class HuanCunExpImpl(outer: HuanCunExp) extends LazyModuleImp(outer) {

  val io = IO{new Bundle{

  }}
  val memory = outer.memAXI4SlaveNode.makeIOs()
}

