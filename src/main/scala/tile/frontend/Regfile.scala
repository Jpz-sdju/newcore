package tile.frontend
import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import bus._
import freechips.rocketchip.rocket.Instructions
import freechips.rocketchip.util.uintToBitPat
import utils._
import freechips.rocketchip.rocket.Instructions._

class RfReadPort extends Bundle {
  val addr = Input(UInt(5.W))
  val data = Output(UInt(64.W))
}

class RfWritePort extends Bundle {
  val wen = Input(Bool())
  val addr = Input(UInt(5.W))
  val data = Input(UInt(64.W))
}

class Regfile extends Module {
  val io = IO(new Bundle() {
    val readPorts = Vec(4, new RfReadPort)
    val writePorts = Vec(2, new RfWritePort)
    val debugPorts = Output(Vec(32,UInt(64.W)))
    val mem = Output(Vec(32, UInt(63.W)))
  })

  val mem = Reg(Vec(32, UInt(64.W)))
  io.mem := mem

  for (r <- io.readPorts) {
    val rdata = Mux(r.addr === 0.U, 0.U, mem(r.addr))
    r.data := rdata
  }
  val writeHit = Wire(Bool())
  writeHit := io.writePorts(0).wen && io.writePorts(1).wen && io
    .writePorts(0)
    .addr === io.writePorts(1).addr
  when(!writeHit) {
    for (w <- io.writePorts) {
      when(w.wen) {
        mem(w.addr) := w.data
      }
    }
  }.otherwise {
    mem(io.writePorts(1).addr) := io.writePorts(1).data
  }

  for (i <- 0 to 31){
    io.debugPorts(i) := Mux(i.U === 0.U, 0.U, mem(i.U))
  }
}
