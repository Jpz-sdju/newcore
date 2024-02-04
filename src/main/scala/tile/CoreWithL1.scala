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
class CoreWithL1()(implicit p: Parameters) extends LazyModule {

  val icache = LazyModule(new Icache())
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
        // beatBytes = 32
        beatBytes = 8
      )
    )
  )
  memAXI4SlaveNode :=   TLToAXI4() := TLBuffer() := TLCacheCork():= icache.node

  lazy val module = new CoreWithL1Imp(this)

}
class CoreWithL1Imp(outer: CoreWithL1) extends LazyModuleImp(outer) {
  val io = IO(new Bundle {
    val iread_req = Decoupled(new ReadReq)
    val iread_resp = Flipped(Decoupled(new ReadResp))
  })

  val frontend = Module(new Frontend())
  val backend = Module(new Backend())

  val iread_req = io.iread_req
  val iread_resp = io.iread_resp

  

  val icache = outer.icache.module

  icache.io.read_req <> frontend.io.iread_req
  icache.io.read_resp <> frontend.io.iread_resp


  val memory = outer.memAXI4SlaveNode.makeIOs()
}
