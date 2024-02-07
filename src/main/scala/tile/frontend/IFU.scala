package tile.frontend

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import bus._
import top.Setting
class FetchIO extends Bundle {
  // val 
}
class IFU()(implicit p: Parameters) extends Module with Setting{

  val io = IO(new Bundle{
    val read_req = DecoupledIO(new ReadReq)
    val read_fin = Input(Bool())
    val redirect = Flipped(DecoupledIO(UInt(XLEN.W)))
  })

  val pc = RegInit(("h8000_0000".U)(32.W))
  val npc = RegInit(("h8000_0000".U)(32.W))

  val read = io.read_req

  // REDIRECT always ready
  io.redirect.ready := true.B

  when(io.redirect.valid){
    pc := io.redirect.bits
  }.elsewhen(io.read_fin){
    pc := pc +4.U
  }

  val fetch_on_flight = RegInit(false.B)
  when(read.fire){
    fetch_on_flight := true.B
  }.elsewhen(io.read_fin){
    fetch_on_flight := false.B
  }

  read.valid :=  !reset.asBool && !fetch_on_flight
  read.bits.addr := pc
  read.bits.size := 4.U

}