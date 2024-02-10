package tile.frontend
import chisel3._, utils._, util._
import bus._
import freechips.rocketchip.config._
import tile._
import top.Setting
class Frontend()(implicit p: Parameters) extends Module with Setting {
  val io = IO(new Bundle {
    val iread_req = Decoupled(new ReadReq)
    val iread_resp = Flipped(Decoupled(new ReadRespWithReqInfo))

    val out = Decoupled(new PipelineBundle)

    val redirect = Flipped(DecoupledIO(UInt(XLEN.W)))

    val wb = Flipped(Decoupled(new WBundle))
  })

  val ifu = Module(new IFU())
  val decoder = Module(new Decoder())
  val decoder_in = decoder.io.in.ctrl_flow
  val decoder_out = decoder.io.out

  val iread_req = io.iread_req
  val iread_resp = io.iread_resp

  // icache read req
  iread_req <> ifu.io.read_req
  // icache read resp to decoder
  decoder_in.bits.pred_taken := false.B
  decoder_in.bits.instr := iread_resp.bits.resp.data
  decoder_in.bits.pc := iread_resp.bits.req.addr
  decoder_in.valid := iread_resp.valid
  iread_resp.ready := true.B

  // resp is valid,notify ifu to update pc
  ifu.io.redirect <> io.redirect
  ifu.io.read_fin := iread_resp.valid
  // reg file
  val regfile = Module(new Regfile())
  val reg_read_port = regfile.io.readPorts // 4
  val reg_write_port = regfile.io.writePorts // 2
  reg_read_port(0).addr := decoder_out.bits.rs1
  reg_read_port(1).addr := decoder_out.bits.rs2

  // not use
  reg_read_port(2) := DontCare
  reg_read_port(3) := DontCare
  reg_write_port := DontCare

  // frontend to backend
  val out_wire = Wire(Decoupled(new PipelineBundle))

  // choose source data
  val src_type = decoder_out.bits.cf.ctrl.srcType
  val pc = decoder_out.bits.cf.cf.pc
  val imm = decoder_out.bits.Imm
  val src1 = Mux(SrcType.isReg(src_type(0)), reg_read_port(0).data, pc)
  val src2 = Mux(SrcType.isReg(src_type(1)), reg_read_port(1).data, imm)

  out_wire.bits := decoder_out.bits // 3 signals init
  out_wire.bits.Src1 := src1
  out_wire.bits.Src2 := src2
  out_wire.bits.storeData := src2
  // out_wire.bits.lsAddr := // init AFTER!!

  out_wire.valid := decoder_out.valid

  decoder_out.ready := out_wire.ready
  PipelineConnect(out_wire, io.out, out_wire.valid && io.out.ready, false.B)

  /* 
  
    WB REGION
   */

   reg_write_port(0).addr := io.wb.bits.rd
   reg_write_port(0).data := io.wb.bits.data
   reg_write_port(0).wen := io.wb.valid
   io.wb.ready := true.B
}
