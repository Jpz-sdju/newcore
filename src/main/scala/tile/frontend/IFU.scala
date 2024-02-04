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
    val read_fin = Input(Bool())
    val redirect = Input(Bool())
  })

  val pc = RegInit(("h8000_0000".U)(32.W))
  val npc = RegInit(("h8000_0000".U)(32.W))

  val read = io.read_req

  when(io.redirect){
    pc := 0.U
  }.elsewhen(io.read_fin){
    pc := pc +4.U
  }

  read.valid := true.B
  read.bits.addr := pc
  read.bits.size := 4.U

}