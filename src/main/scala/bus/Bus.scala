package bus

import chisel3._
import chisel3.experimental._
import chisel3.util._
import freechips.rocketchip.config._
import top.Setting

class ReadReq extends Bundle with Setting {
  val addr = UInt(PAddrBits.W)
  val size = UInt(4.W)
}

class ReadResp extends Bundle with Setting {

  val data = UInt(XLEN.W)

}
class ArrayReadResp[T <: Bundle](bun: T) extends Bundle with Setting {

  val data = Output(bun)

}
class ReadRespWithReqInfo extends Bundle with Setting {

  val req = new ReadReq
  val resp = new ReadResp
}

class WriteBus[T <: Bundle](bun: T) extends Bundle with Setting {
//  val io = IO //addr size mask ...

  val addr = Output(UInt(PAddrBits.W))
  val data = Output(bun)
  val size = Output(UInt(log2Up(XLEN / 8).W))
  val mask = Output(UInt(log2Up(XLEN / 8).W))

}

class iRefillBundle extends Bundle {
  val tag = UInt(20.W)
  val data = UInt(64.W)
  val meta = UInt(2.W)
}
class ArrayRespBundle extends Bundle with Setting{
  val tag = Vec(ways ,UInt(20.W))
  val data = Vec(ways, UInt(XLEN.W))
  val meta = Vec(ways, UInt(2.W))
}

