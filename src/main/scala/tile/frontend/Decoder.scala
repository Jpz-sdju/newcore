package tile.frontend
import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import bus._
import freechips.rocketchip.rocket.Instructions
import freechips.rocketchip.util.uintToBitPat
import utils._
import freechips.rocketchip.rocket.Instructions._
import tile._


class DecoderIO(implicit p: Parameters) extends Bundle {
  val in = new Bundle { val ctrl_flow = Flipped(Decoupled(new CtrlFlow)) }
  val out = new Bundle { val cf_ctrl = Decoupled(new CfCtrl) }
}

class Decoder()(implicit p: Parameters) extends Module  {
  val io = IO(new DecoderIO)

  val decode_unit = Module(new DecodeUnit())
  val decode_unit_in = decode_unit.io.in.ctrl_flow
  val decode_unit_out = decode_unit.io.out.cf_ctrl  

  //assign to DecodeUnit
  decode_unit_in.instr := io.in.ctrl_flow.bits.instr
  decode_unit_in.pc := io.in.ctrl_flow.bits.pc
  decode_unit_in.pred_taken := io.in.ctrl_flow.bits.pred_taken

  io.in.ctrl_flow.ready := true.B

  //assign out 
  io.out.cf_ctrl.bits.cf := decode_unit_out.cf
  io.out.cf_ctrl.bits.ctrl := decode_unit_out.ctrl
  io.out.cf_ctrl.valid := true.B
}