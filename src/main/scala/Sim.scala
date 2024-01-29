import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage
import chipsalliance.rocketchip.config._

object Sim extends App {
  // (new ChiselStage).execute()
  (new ChiselStage).emitVerilog(new Top()(Parameters.empty), args)
}

