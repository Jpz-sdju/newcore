package tile.frontend
import chisel3._, utils._, util._
import bus._
import freechips.rocketchip.config._
import tile._
class Frontend()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val iread_req = Decoupled(new ReadReq)
    val iread_resp = Flipped(Decoupled(new ReadRespWithReqInfo))

    val out = Decoupled(new Bundle {
      val cf = (new CfCtrl)
      val src = (Vec(2, UInt(64.W)))
    })

  })

  val ifu = Module(new IFU())
  val decoder = Module(new Decoder())
  val decoder_in = decoder.io.in.ctrl_flow
  val decoder_out = decoder.io.out.cf_ctrl

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
  ifu.io.redirect := false.B
  ifu.io.read_fin := iread_resp.valid
  // frontend to backend
  
  val out_wire = Wire(Decoupled(new Bundle {
    val cf = (new CfCtrl)
    val src = (Vec(2, UInt(64.W)))
  }))

  //reg file
  val regfile = Module(new Regfile())
  val reg_read_port = regfile.io.readPorts //4
  val reg_write_port = regfile.io.writePorts //2
  //choose source data
  val src_type = decoder_out.bits.ctrl.srcType
  val src1 = Mux(SrcType.isReg(src_type(0)), reg_read_port)
  
  out_wire.bits.cf := decoder_out.bits
  out_wire.bits.src := DontCare
  out_wire.valid := decoder_out.valid
  decoder_out.ready := out_wire.ready
  PipelineConnect(out_wire,io.out, out_wire.valid && io.out.ready, false.B)

  dontTouch(decoder_out)

}
