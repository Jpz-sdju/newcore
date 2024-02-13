package bus

import chisel3._
import chisel3.experimental._
import chisel3.util._
import freechips.rocketchip.config._
import top.Setting

class CacheReq extends Bundle with Setting {
  val cmd = Bool() //0 is read, 1 is wirte
  val addr = UInt(PAddrBits.W)
  val wdata = UInt(XLEN.W)
  val wsize = UInt(4.W)
  val wmask = UInt(8.W)
}
class ReadReq extends Bundle with Setting {
  val addr = UInt(PAddrBits.W)
  val size = UInt(4.W)
}

class ReadResp extends Bundle with Setting {

  val data = UInt(XLEN.W)

}
class ReadRespWithReqInfo extends Bundle with Setting {

  val req = new ReadReq
  val resp = new ReadResp
}

class ReadRespFromDown extends Bundle with Setting {

  val data = UInt(256.W)
}

class WriteBus[T <: Bundle](bun: T) extends Bundle with Setting {
//  val io = IO //addr size mask ...

  val addr = Output(UInt(PAddrBits.W))
  val data = Output(bun)
  val size = Output(UInt(log2Up(XLEN / 8).W))
  val mask = Output(UInt(log2Up(XLEN / 8).W))

}

class ArrayWriteBundle extends Bundle {
  val addr = UInt(32.W)
  val tag = UInt(20.W)
  val data = UInt(256.W)
  val meta = UInt(2.W)
  val bank_mask = Vec(8, Bool())
  val way_mask = Vec(4, Bool())
}

//icache and dcache UNIFORM!
class ArrayRespBundle extends Bundle with Setting{
  val data = Vec(ways, UInt(XLEN.W))
}

