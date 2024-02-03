package other


import chisel3._,util._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import other._
import freechips.rocketchip.tilelink._
import huancun._
import device._
import freechips.rocketchip.amba.axi4._

class simpleCase()(implicit p: Parameters) extends LazyModule {
  val iun0 = LazyModule(new IUnCache())
  val timer = LazyModule(new TLTimer())
  val iun1 = LazyModule(new IUnCache())
  val xbar = TLXbar()
  xbar := iun0.clientNode
  xbar := iun1.clientNode
//    xbar := TLTempNode() := iun.clientNode
  timer.node := xbar

  lazy val module = new LazyModuleImp(this){
    
  }

}

