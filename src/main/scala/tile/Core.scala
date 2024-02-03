package tile

import chisel3._
import utils._
import util._
import bus._
import frontend._
import backend._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import tilelink._

class Core()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val iread_req = Decoupled(new ReadReq)
    val iread_resp = Decoupled(Flipped(new ReadResp))
  })

  val frontend = Module(new Frontend())
  val backend = Module(new Backend())

  val iread_req = io.iread_req
  val iread_resp = io.iread_resp

  iread_req <> frontend.io.iread_req
  iread_resp <> frontend.io.iread_resp

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
      //beatBytes = 32
      beatBytes = 8
    )
  ))
}
