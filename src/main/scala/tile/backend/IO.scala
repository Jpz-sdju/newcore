package tile.backend

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import tile._


class FuOutput(val len: Int)(implicit p: Parameters) extends Bundle {
  val data = UInt(len.W)
}

class FunctionUnitInput(val len: Int)(implicit p: Parameters) extends Bundle {
  val src1 = UInt(len.W)
  val src2 = UInt(len.W)
  val cf = new CfCtrl

}

class FunctionUnitIO(val len: Int)(implicit p: Parameters) extends Bundle {
  val in = Flipped(DecoupledIO(new FunctionUnitInput(len)))

  val out = DecoupledIO(new FuOutput(len))

}

abstract class FunctionUnit(len: Int = 64)(implicit p: Parameters) extends Module {

  val io = IO(new FunctionUnitIO(len))

}