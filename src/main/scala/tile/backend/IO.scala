package tile.backend

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import tile._
import top.Setting
import tile.frontend._

class FunctionUnitIO extends Bundle with Setting {
  val in = Flipped(Decoupled(new Bundle {
    val src1 = Output(UInt(XLEN.W))
    val src2 = Output(UInt(XLEN.W))
    val func = Output(FuOpType())
  }))
  val out = Decoupled(Output(UInt(XLEN.W)))
}

// abstract class FunctionUnit(len: Int = 64)(implicit p: Parameters) extends Module {

//   val io = IO(new FunctionUnitIO(len))

// }