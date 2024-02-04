package tile.frontend
import chisel3._, utils._, util._
import bus._
import freechips.rocketchip.config._

class Frontend()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val iread_req = Decoupled(new ReadReq)
    val iread_resp = Flipped(Decoupled(new ReadResp))
  })

  val ifu = Module(new IFU())
  val decode = Module(new DecodeUnit())
  val decode_in = decode.io.in.ctrl_flow
  val decode_out = decode.io.out.cf_ctrl
  
  ifu.io.redirect := false.B

  val iread_req = io.iread_req
  val iread_resp = io.iread_resp
  //icache read req
  iread_req <> ifu.io.read_req
  //icache read resp
  iread_resp.ready := true.B
  decode_in.instr := iread_resp.bits.data
  decode_in.pc := 0.U
  decode_in.pred_taken := false.B

  
  val res = decode.io.out.cf_ctrl
  dontTouch(res)


}
