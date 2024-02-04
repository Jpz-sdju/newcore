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
      val src =(Vec(2, UInt(64.W)))
    })

  })

  val ifu = Module(new IFU())
  val decoder = Module(new Decoder())
  val decoder_in = decoder.io.in.ctrl_flow
  val decoder_out = decoder.io.out.cf_ctrl
  
  
  val iread_req = io.iread_req
  val iread_resp = io.iread_resp
  
  //icache read req
  iread_req <> ifu.io.read_req
  //icache read resp to decoder
  decoder_in.bits.pred_taken := false.B
  decoder_in.bits.instr := iread_resp.bits.resp.data
  decoder_in.bits.pc := iread_resp.bits.req.addr
  decoder_in.valid := iread_resp.valid
  iread_resp.ready := true.B
  

  //resp is valid,notify ifu to update pc
  ifu.io.redirect := false.B
  ifu.io.read_fin := iread_resp.valid
  //frontend to backend
  io.out.bits.cf :=  decoder_out.bits
  io.out.bits.src := DontCare
  io.out.valid := decoder_out.valid
  decoder_out.ready := io.out.ready
  
  
  
  dontTouch(decoder_out)


}
