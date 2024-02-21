package tile

import chisel3._
import utils._
import util._
import bus._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4._
import device._
import huancun.utils._
import tile._
import frontend._
import backend._
import cache._
import cache.dcache._
import top.Setting
object ColorPrint extends App {
  // ANSI 转义码定义颜色和样式
  val RESET = "\u001B[0m"
  val RED = "\u001B[31m"
  val GREEN = "\u001B[32m"
  val YELLOW = "\u001B[33m"
  val BLUE = "\u001B[34m"

  // 示例文本
  val message = "Hello, Colorful Scala!"

  def ColorPrint(v: Any): Unit = {
    val RESET = "\u001B[0m"
    val RED = "\u001B[31m"
    val GREEN = "\u001B[32m"
    val YELLOW = "\u001B[33m"
    val BLUE = "\u001B[34m"

    val pre = "\u001B[33;1m"
    println(s"${pre}$v$RESET")
  }
  // 打印彩色文本
  println(s"${RED}This is red.${RESET}")
  println(s"${GREEN}This is green.${RESET}")
  println(s"${YELLOW}This is yellow.${RESET}")
  println(s"${BLUE}This is blue.${RESET}")

  // 在文本中混合颜色
  println(s"${RED}This is red.${GREEN} This is green.${RESET}")

  // 打印带有变量的彩色文本
  val variableValue = 42
  println(s"${YELLOW}This is a variable: $variableValue${RESET}")

  // 使用样式，比如加粗
  val boldText = "\u001B[1mBold Text\u001B[0m"
  println(s"Regular text and $boldText")

  // 使用背景颜色
  val backgroundRed = "\u001B[41mRed Background\u001B[0m"
  println(backgroundRed)
}
class CoreWithL1()(implicit p: Parameters) extends LazyModule with Setting{

  val icache = LazyModule(new Icache())
  val dcache = LazyModule(new Dcache())
  val uncache = LazyModule(new UnCache())
  // axi4ram slave node
  val device = new MemoryDevice
  val memRange =
    AddressSet(0x00000000L, 0xffffffffL).subtract(AddressSet(0x0L, 0x7fffffffL))
  // ColorPrint.ColorPrint(memRange)
  // ColorPrint.ColorPrint(memRange(0).base)
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
        // beatBytes = 32
        beatBytes = 8
      )
    )
  )
  val xbar = TLXbar()
  xbar := TLCacheCork() := icache.node
  xbar := TLCacheCork() := dcache.node
  // memAXI4SlaveNode := TLToAXI4() := TLBuffer() := TLCacheCork(TLCacheCorkParams(true)) :=* xbar
  memAXI4SlaveNode := AXI4UserYanker() := AXI4Buffer() := TLToAXI4() := TLWidthWidget(
    32
  ) := TLBuffer() := TLBuffer() := xbar



  val clintSpace = Seq(AddressSet(CLINTBase, CLINTSize - 0x1L)) // CLINT
  val timer = LazyModule(new TLTimer(clintSpace, sim = true))
  val mmioxbar = TLXbar()
  mmioxbar := TLBuffer.chainNode(2) := TLTempNode() := uncache.clientNode
  // mmioxbar := TLBuffer.chainNode(2) := TLTempNode() := iUncache.clientNode
  timer.node := mmioxbar



  val onChipPeripheralRange = AddressSet(0x38000000L, 0x07ffffffL)
  val uartRange = AddressSet(0x40600000L, 0xf)
  val uartDevice = new SimpleDevice("serial", Seq("xilinx,uartlite"))
  val uartParams = AXI4SlaveParameters(
    address = Seq(uartRange),
    regionType = RegionType.UNCACHED,
    supportsRead = TransferSizes(1, 8),
    supportsWrite = TransferSizes(1, 8),
    resources = uartDevice.reg
  )
  val peripheralRange = AddressSet(
    0x0,
    0x7fffffff
  ).subtract(onChipPeripheralRange).flatMap(x => x.subtract(uartRange))
  val peripheralNode = AXI4SlaveNode(
    Seq(
      AXI4SlavePortParameters(
        Seq(uartParams),
        beatBytes = 8
      )
    )
  )

  peripheralNode :=
    AXI4IdIndexer(idBits = 4) :=
    AXI4Buffer() :=
    AXI4UserYanker() :=
    AXI4Deinterleaver(8) :=
    TLToAXI4() :=
    TLBuffer() := mmioxbar

  lazy val module = new CoreWithL1Imp(this)

}
class CoreWithL1Imp(outer: CoreWithL1)
    extends LazyModuleImp(outer)
    with Setting {
  val io = IO(new Bundle {
    val iread_req = Decoupled(new ReadReq)
    val iread_resp = Flipped(Decoupled(new ReadResp))
  })

  val frontend = Module(new Frontend())
  val backend = Module(new Backend())

  val iread_req = io.iread_req
  val iread_resp = io.iread_resp
  val icache = outer.icache.module
  val dcache = outer.dcache.module
  val uncache = outer.uncache.module

  // icache reslut must connect to frontend
  icache.io.read_req <> frontend.io.iread_req
  icache.io.read_resp <> frontend.io.iread_resp

  // dcache.io.req_from_lsu <> backend.io.d_req

  frontend.io.out <> backend.io.in
  frontend.io.redirect <> backend.io.redirect

  frontend.io.wb <> backend.io.wb

  // debug
  frontend.io.gpr <> backend.io.gpr
  val memory = outer.memAXI4SlaveNode.makeIOs()
  val peripheral = outer.peripheralNode.makeIOs()

  val addrSpace = List(
    (ResetVector, 0x80000000L), // cache
    (
      UnCacheBase,
      UnCacheSize.toLong
    ) // uncache
  )
  val to1Nxbar = Module(new SimpleBusCrossbar1toN(addrSpace))

  to1Nxbar.io.in <>  backend.io.d_req
  to1Nxbar.io.out(0) <> dcache.io.req_from_lsu
  to1Nxbar.io.out(1) <> uncache.io.req_from_lsu
}
