package tile
import chisel3._
import utils._
import util._
import bus._
import frontend._
import backend._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4._
import device._
import top._

class CtrlFlow(implicit p: Parameters) extends Bundle with Setting{
  val instr = UInt(32.W)
  val pc = UInt(VAddrBits.W)
  val pred_taken = Bool()
}
class CtrlSignals(implicit p: Parameters) extends Bundle with Setting {
  val srcType = Vec(3, SrcType())
  val lsrc = Vec(3, UInt(5.W))
  val ldest = UInt(5.W)
  val fuType = FuType()
  val fuOpType = FuOpType()
  val rfWen = Bool()
  val fpWen = Bool()
  val isXSTrap = Bool()
  val noSpecExec = Bool() // wait forward
  val blockBackward = Bool() // block backward
  val flushPipe =
    Bool() // This inst will flush all the pipe when commit, like exception but can commit
  val selImm = SelImm()
  val imm = UInt(ImmUnion.maxLen.W)
  val commitType = CommitType()
  private def allSignals = srcType ++ Seq(
    fuType,
    fuOpType,
    rfWen,
    fpWen,
    isXSTrap,
    noSpecExec,
    blockBackward,
    flushPipe,
    selImm
  )

  def decode(
      inst: UInt,
      table: Iterable[(BitPat, List[BitPat])]
  ): CtrlSignals = {
    val decoder = freechips.rocketchip.rocket.DecodeLogic(
      inst,
      XDecode.decodeDefault,
      table
    )
    allSignals zip decoder foreach { case (s, d) => s := d }
    commitType := DontCare
    this
  }
}
class CfCtrl(implicit p: Parameters) extends Bundle {
  val cf = new CtrlFlow
  val ctrl = new CtrlSignals
}
