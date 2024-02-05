package bus

import chisel3._
import chisel3.experimental._
import chisel3.util._
import freechips.rocketchip.config._
import top.Setting


class ReadReq extends Bundle with Setting{
  val addr = UInt(PAddrBits.W)
  val size = UInt(4.W)
}

class ReadResp extends Bundle with Setting{

  val data = UInt(XLEN.W)

}
class ReadRespWithReqInfo extends Bundle with Setting{

  val req = new ReadReq
  val resp = new ReadResp
}

class WriteBus extends Bundle with Setting{
//  val io = IO //addr size mask ...

  val addr = Output(UInt(PAddrBits.W))
  val data = Output(UInt(XLEN.W))
  val size = Output(UInt(log2Up(XLEN/8).W))

  val mask = Output(UInt(log2Up(XLEN/8).W))

}
// class MemBus extends Bundle with Setting{
//     val readBus = Decoupled(new ReadBus)
//     val writeBus = Decoupled(new WriteBus)

// }