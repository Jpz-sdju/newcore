import chisel3._
import chisel3.util._
import chisel3.stage._
import chipsalliance.rocketchip.config._
import top._
import freechips.rocketchip.diplomacy.DisableMonitors
import freechips.rocketchip.util.HasRocketChipStageUtils
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.diplomacy.MonitorsEnabled
import huancun._

object Generator {
  def execute(args: Array[String], mod: => RawModule) {
    (new ChiselStage).execute(args, Seq(ChiselGeneratorAnnotation(mod _)))
  }
}
object Sim extends App {
  // implicit val p = Parameters.empty
  // p.alterPartial { case MonitorsEnabled => false }
  Generator.execute(args, DisableMonitors(p => new Top()(p))(Parameters.empty))
}
object Exp extends App with HasRocketChipStageUtils {

  implicit val p = Parameters.empty
  p.alterPartial { case MonitorsEnabled => false }
  Generator.execute(args, new Top()(p))

}
