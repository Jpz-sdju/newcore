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



  val l3cache = LazyModule(new HuanCun()(new Config((_, _, _) => {
    case HCCacheParamsKey => HCCacheParameters(
      name = s"L1",
      level = 1,
      inclusive = false,
      clientCaches = Seq(CacheParameters(sets = 64, ways = 4, blockGranularity = 7, name = "icache")),
    //   ctrl = Some(CacheCtrl(
    //     address = 0x39000000,
    //     numCores = corenum
    //   )),
      //prefetch = Some(huancun.prefetch.BOPParameters()),
      sramClkDivBy2 = true,
      reqField = Seq(),
      echoField = Seq()
      //enableDebug = true
    )
  })))
  // val sf = Module(l3cache.module)
  Generator.execute(args, l3cache.module)
  writeOutputFile("./build","graph.graphml",l3cache.graphML)

}
