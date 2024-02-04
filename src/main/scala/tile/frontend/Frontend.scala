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
  val iread_req = io.iread_req
  val iread_resp = io.iread_resp

  iread_req <> ifu.io.read_req
  iread_resp <> ifu.io.read_resp
  ifu.io.redirect := false.B

  val decode = Module(new DecodeUnit())

  decode.io.in.ctrl_flow.instr := "h00113423".U
  decode.io.in.ctrl_flow.pc := "h80000000".U
  decode.io.in.ctrl_flow.pred_taken := false.B

  val res = decode.io.out.cf_ctrl
  dontTouch(res)


}
