package tile.cache

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
import freechips.rocketchip.diplomaticobjectmodel.model.U

class IcacheArray() extends Module with Setting {
  val io = IO(new Bundle {
    // set 16kb,4ways,not banked,linesize = 64B
    val iread_req = Flipped(Decoupled(new ReadReq))

    val array_read_resp = DecoupledIO(new ArrayRespBundle)
    val array_write_req = Flipped(Decoupled(new iRefillBundle))
  })
  val req = io.iread_req
  val resp = io.array_read_resp

  // three arrays
  val dataArray = Array.fill(8)(
    SyncReadMem(64, Vec(ways, UInt((64).W)))
  )
  val tagArray = SyncReadMem(64, Vec(ways, UInt((32 - 6 - 6).W)))
  val metaArray = SyncReadMem(64, Vec(ways, UInt((2).W)))

  // 0-6 offset,6 idx,20tag
  val bank_idx = io.iread_req.bits.addr(5, 3)
  val set_idx = io.iread_req.bits.addr(11, 6)
  val read_tag = Wire(Vec(4, UInt(20.W)))
  val read_data = WireInit(VecInit(Seq.fill(ways)(0.U(64.W))))
  val read_meta = Wire(Vec(4, UInt(2.W)))

  for (id <- 0 until 8) {
    val cond = id.U === bank_idx
    when(cond) {
      read_data := dataArray(id).read(set_idx, cond.asBool)
    }
  }
  read_tag := tagArray.read(set_idx)
  read_meta := metaArray.read(set_idx)

  // assign 4 ways RESULT to outer
  resp.bits.data := read_data
  resp.bits.tag := read_tag
  resp.bits.meta := read_meta
  resp.valid := RegNext(req.valid)

  dontTouch(read_data)

  req.ready := true.B
  io.array_read_resp <> DontCare
  io.array_write_req <> DontCare
}
