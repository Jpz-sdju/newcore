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
  Generator.execute(args,new Top1())

}
object Exp extends App with HasRocketChipStageUtils {

  // implicit val p = Parameters.empty
  // p.alterPartial { case MonitorsEnabled => false }
  // Generator.execute(args, new Top()(p))
  Generator.execute(args, DisableMonitors(p => new SimTop()(p))(Parameters.empty))
  println("======================Finish Chisel Compile!======================")
  println(" ")
  println(" ")
  println(" ")

}
