package other

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import chipsalliance.rocketchip.config._

import freechips.rocketchip.tilelink._


class IUnCache()(implicit p: Parameters) extends LazyModule {

  val clientParameters = TLMasterPortParameters.v1(
    clients = Seq(
      TLMasterParameters.v1(
        "Iuncache",
        sourceId = IdRange(0, 1 << 1),
        supportsProbe = TransferSizes(32)//64
      )
    )
  )
  val clientNode = TLClientNode(Seq(clientParameters))

  lazy val module = new IUncacheImp(this)
}

class IUncacheImp(wo: IUnCache) extends LazyModuleImp(wo) {
  val io = IO {
    new Bundle {
      val ss = Output(UInt(64.W))

    }

  }
  val cnt = RegInit(0.U(8.W))
  val (bus, edge) = wo.clientNode.out.head

println(wo.clientNode.out.head._1.params)
  val mem_acquire = bus.a
  val mem_grant = bus.d

  when(true.B){
    cnt := cnt +1.U
  }
  val load = edge
        .Get(
          fromSource = 1.U,
          toAddress = 2.U,
          lgSize = log2Ceil(64).U, // 64
          // growPermissions = "b00".U
        )
        ._2

    val store = edge
        .Put(
          fromSource = 1.U,
          toAddress = 2.U,
          lgSize = log2Ceil(64).U,
          data = 0.U
          // growPermissions = "b00".U
        )
        ._2

  mem_acquire.bits := load.asTypeOf(new TLBundleA(edge.bundle))
  mem_acquire.valid := Mux(cnt(1),true.B,false.B)


  mem_grant.ready := Mux(cnt(1),true.B,false.B)
  io.ss := mem_grant.bits.data
}