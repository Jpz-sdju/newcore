import chisel3._
import chisel3.util._
import chisel3.stage._
import chipsalliance.rocketchip.config._
import top._
import freechips.rocketchip.util.HasRocketChipStageUtils
import freechips.rocketchip.diplomacy._
import huancun._
import other.simpleCase

object Generator {
  def execute(args: Array[String], mod: => RawModule) {
    (new ChiselStage).execute(args, Seq(ChiselGeneratorAnnotation(mod _)))
  }
}

class test extends Module{
    val b = IdRange(0, 2)
    val s = b.contains("b10".U)

    val w = WireInit(false.B)
    w := s
    dontTouch(w)
    println(s === true.B)
}
object Sim extends App {
  implicit val p = Parameters.empty
  p.alterPartial { case MonitorsEnabled => false }
  // Generator.execute(args, DisableMonitors(p => new Top()(p))(Parameters.empty))
  val s = LazyModule(new simpleCase())
  Generator.execute(args, s.module)

  // println(s.iun0.clientNode.edges.out.length)//1
  println(s.iun0.clientNode.bindingInfo)//1
  println(s.iun0.clientNode.parametersInfo)//1
  println(s.iun0.clientNode.connectedPortsInfo)//1
  println("===================================")
  println(s.iun1.clientNode.bindingInfo)//1
  println(s.iun1.clientNode.parametersInfo)//1
  println(s.iun1.clientNode.connectedPortsInfo)//1
  println("===================================")

  println(s.timer.node.bindingInfo)
  println(s.timer.node.parametersInfo)
  println(s.timer.node.connectedPortsInfo)


  println(s.xbar)
  println(s.xbar.outer)
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
