package tile

import chisel3._, utils._, util._
import bus._
import frontend._
import backend._
import freechips.rocketchip.config._

class Core()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val iread_req = Decoupled(new ReadReq)
    val iread_resp = Decoupled(Flipped(new ReadResp))
  })

  val frontend = Module(new Frontend())
  val backend = Module(new Backend())

  val iread_req = io.iread_req
  val iread_resp = io.iread_resp

  iread_req <> frontend.io.iread_req
  iread_resp <> frontend.io.iread_resp
}
