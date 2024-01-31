package other

import chisel3._
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.AddressSet
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.diplomacy.SimpleDevice
import freechips.rocketchip.tilelink.TLRegisterNode
import freechips.rocketchip.diplomacy.LazyModuleImp
import freechips.rocketchip.regmapper.RegField


class TLTimer()(implicit p: Parameters) extends LazyModule {
  val spdeviec = new SimpleDevice("clint", Seq("xiangshan", "clint"))
  val node = TLRegisterNode(
    Seq(AddressSet(0x38000000L, 0x00010000L - 1)),
    spdeviec,
    concurrency = 1,
    beatBytes = 8
  )

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle() {
      val mtip = Output(Bool())
      val msip = Output(Bool())
    })

    val mtime = RegInit(0.U(64.W))  // unit: us
    val mtimecmp = RegInit(2000000.U(64.W))
    val msip = RegInit(0.U(64.W))

    val clk = 5
    val freq = RegInit(clk.U(64.W))
    val inc = RegInit(1.U(64.W))

    val cnt = RegInit(0.U(64.W))
    val nextCnt = cnt + 1.U
    cnt := Mux(nextCnt < freq, nextCnt, 0.U)
    val tick = (nextCnt === freq)
    when (tick) { mtime := mtime + inc }


    node.regmap(
      0x0    -> Seq(RegField(64,msip)),
      0x4000 -> Seq(RegField(64,mtimecmp)),
      0x8000 -> Seq(RegField(64,freq)),
      0x8008 -> Seq(RegField(64,inc)),
      0xbff8 -> Seq(RegField(64,mtime))
    )

    io.mtip := RegNext(mtime >= mtimecmp)
    io.msip := RegNext(msip =/= 0.U)

  }
}

class TLTimerImpl(outer: TLTimer) extends LazyModuleImp(outer) {
  val mtime = RegInit(0.U(64.W)) // unit: us
  val mtimecmp = RegInit(0.U(64.W))
  val msip = RegInit(0.U(64.W))

  val clk = 5
  val freq = RegInit(clk.U(64.W))
  val inc = RegInit(1.U(64.W))

  val cnt = RegInit(0.U(64.W))
  val nextCnt = cnt + 1.U
  cnt := Mux(nextCnt < freq, nextCnt, 0.U)
  val tick = (nextCnt === freq)
  when(tick) { mtime := mtime + inc }
  outer.node.regmap(
    0x0 -> Seq(RegField(64, msip)),
    0x4000 -> Seq(RegField(64, mtimecmp)),
    0x8000 -> Seq(RegField(64, freq)),
    0x8008 -> Seq(RegField(64, inc)),
    0xbff8 -> Seq(RegField(64, mtime))
  )

}
