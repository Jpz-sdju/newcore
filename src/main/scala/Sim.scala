import chisel3._
import chisel3.util._
import chisel3.stage._
import chipsalliance.rocketchip.config._
import top._
import freechips.rocketchip.diplomacy.DisableMonitors

object Generator {
  def execute(args: Array[String], mod: => RawModule) {
    (new ChiselStage).execute(args, Seq(
      ChiselGeneratorAnnotation(mod _)))
  }
}
object Sim extends App {
  // (new ChiselStage).execute()
  // (new ChiselStage).emitVerilog(new Top()(Parameters.empty), args)
  Generator.execute(args, DisableMonitors(p => new Top()(p))(Parameters.empty))
}
object Exp extends App {
  // (new ChiselStage).execute()
  // (new ChiselStage).emitVerilog(new Top()(Parameters.empty), args)
  Generator.execute(args, DisableMonitors(p => new Top1()(p))(Parameters.empty))
}

