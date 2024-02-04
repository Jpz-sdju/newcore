package tile.frontend

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import bus._
class FetchIO extends Bundle {
  // val 
}
class IFU()(implicit p: Parameters) extends Module{

  val io = IO(new Bundle{
    val read_req = DecoupledIO(new ReadReq)
    val read_resp = Flipped(DecoupledIO(new ReadResp))

    val redirect = Input(Bool())
  })

  val pc = RegInit(("h80000000".U)(32.W))
  val npc = RegInit(("h80000000".U)(32.W))

  val read = io.read_req
  val resp = io.read_resp
  resp := DontCare

  when(io.redirect){
    pc := 0.U
  }.otherwise{
    pc := pc +4.U
  }

  read.valid := true.B
  read.bits.addr := pc
  read.bits.size := 4.U

}