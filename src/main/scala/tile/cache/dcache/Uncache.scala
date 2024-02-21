package tile.cache.dcache
import chisel3._
import utils._
import util._
import bus._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4._
import device._
import top.Setting
import dataclass.data

class UnCache()(implicit p: Parameters) extends LazyModule {

  val clientParameters = TLMasterPortParameters.v1(
    clients = Seq(
      TLMasterParameters.v1(
        "Uncache",
        sourceId = IdRange(0, 1 << 1)
      )
    )
  )
  val clientNode = TLClientNode(Seq(clientParameters))

  lazy val module = new UncacheImp(this)
}

class UncacheImp(outer: UnCache) extends LazyModuleImp(outer) with Setting {

  val io = IO(new Bundle {
    val req_from_lsu = Flipped(new LsuBus)
    val uncache_stall = Output(Bool())
  })

  val (bus, edge) = outer.clientNode.out.head
  val mem_acquire = bus.a
  val mem_grant = bus.d

  // Uncache FSM
  val s_invalid :: s_refill_req :: s_refill_resp :: s_send_resp :: Nil = Enum(4)
  val state = RegInit(s_invalid)

  //  Assign default values to output signals.

  mem_acquire.valid := false.B
  mem_acquire.bits := DontCare
  mem_grant.ready := false.B

  //  ================================================
  //  FSM state description:
  //  s_invalid     : Entry is invalid.
  //  s_refill_req  : Send Acquire request.
  //  s_refill_resp : Wait for Grant response.
  //  s_send_resp   : Send Uncache response.

  // LSU s1 req Info
  val s1_req = io.req_from_lsu.req
  // reg  s1_req -> s2_req
  val s2_req = RegEnable(s1_req.bits, (0.U).asTypeOf(new CacheReq), s1_req.fire)
  val s2_req_valid = RegEnable(s1_req.valid, false.B, s1_req.fire)
  when(io.req_from_lsu.resp.fire) {
    s2_req_valid := false.B
  }

  val resp_data = Reg(UInt(DataBits.W))

  def storeReq = s1_req.bits.cmd

  val load = edge
    .Get(
      fromSource = 3.U,
      toAddress = s1_req.bits.addr,
      lgSize = s1_req.bits.wsize
    )
    ._2

  val store = edge
    .Put(
      fromSource = 3.U,
      toAddress = s1_req.bits.addr,
      lgSize = s1_req.bits.wsize,
      data = s1_req.bits.wdata,
      mask = s1_req.bits.wmask
    )
    ._2

  val (_, _, refill_done, _) = edge.addr_inc(mem_grant)

  // only when state is idle,could let more req in!
  s1_req.ready := (state === s_invalid)
  io.uncache_stall := !(state === s_invalid)


  //FUCKING BUGS!!!!!
  io.req_from_lsu.resp.valid := mem_grant.valid
  switch(state) {
    is(s_invalid) {
      when(s1_req.fire) {
        state := s_refill_resp
        mem_acquire.valid := true.B
        mem_acquire.bits := Mux(storeReq, store, load)

      }
    }
    is(s_refill_resp) {
      mem_grant.ready := true.B
      when(mem_grant.fire) {
        resp_data := mem_grant.bits.data
        state := s_send_resp
      }
    }
    is(s_send_resp) {
      io.req_from_lsu.resp.valid := true.B
      io.req_from_lsu.resp.bits := resp_data

      when(io.req_from_lsu.resp.fire) {
        state := s_invalid
      }
    }
  }

}
