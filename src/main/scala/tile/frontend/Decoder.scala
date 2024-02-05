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
  val out = Decoupled(new PipelineBundle)
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
  val fuType = decode_unit_out.ctrl.fuType
  val fuOpType = decode_unit_out.ctrl.fuOpType
  val out = io.out.bits
  out.cf := decode_unit_out
  out.isAlu := FuType.isAluExu(fuType)
  out.isBranch := FuType.isAluExu(fuType) && ALUOpType.isBranch(fuOpType)
  out.isJmp := JumpOpType.jumpOpisJ(fuOpType)
  out.isAuipc := JumpOpType.jumpOpisAuipc(fuOpType) && FuType.isJumpExu(fuType)
  out.isLoad := FuType.isLoadStore(fuType) && !FuType.isStoreExu(fuType)
  out.isStore := FuType.isLoadStore(fuType) && FuType.isStoreExu(fuType)
  out.rs1 := decode_unit_out.ctrl.lsrc(0)
  out.rs2 := decode_unit_out.ctrl.lsrc(1)
  out.Imm := decode_unit_out.ctrl.imm
  out.lsSize := LSUOpType.size(fuOpType)
  
  out.Src1 := 0.U //init houmian
  out.Src2 := 0.U //init houmian
  out.StoreData := 0.U //rs2
  out.lsAddr := 0.U //rs1 + offset
  out.WRITE_BACK := 0.U //could assign at ex, or lsu
 
  io.out.valid := io.in.ctrl_flow.valid
  
}