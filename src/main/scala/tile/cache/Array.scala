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
import os.write
import os.group

class IcacheArray() extends Module with Setting {
  val io = IO(new Bundle {
    // set 16kb,4ways,not banked,linesize = 64B
    val iread_req = Flipped(Decoupled(new ReadReq))

    val array_read_resp = DecoupledIO(new ArrayRespBundle)
    val array_write_req = Flipped(Decoupled(new ArrayWriteBundle))
  })
  val req = io.iread_req
  val resp = io.array_read_resp

  // three arrays
  val dataArray = Array.fill(banks)(
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


  req.ready := true.B
  /* 
    WRITE REGION
   */
  val write_req = io.array_write_req
  val bankmask = write_req.bits.bank_mask
  val waymask_onehot = write_req.bits.way_mask
  val waymask_uint = PriorityEncoder(waymask_onehot)

  //ugly code below,refact later
  val bank_write_data = WireInit(VecInit(Seq.fill(8)(0.U(64.W))))
  val write_4ways_data = WireInit(VecInit(Seq.fill(ways)(0.U(64.W))))
  for (bank <- 0 until 8){
    bank_write_data(bank.U) :=  write_req.bits.data(((bank+1)*64 -1)%256 ,(bank * 64) % 256)
    write_4ways_data(waymask_uint) := bank_write_data(bank)
  }
  for (id <- 0 until 8) {
    val cond = bankmask(id.U) && write_req.valid
    when(cond) {
      dataArray(id).write(set_idx, write_4ways_data , waymask_onehot)
    }
  }
  val write_4ways_tag = WireInit(VecInit(Seq.fill(ways)(0.U(20.W))))
  write_4ways_tag(waymask_uint) := write_req.bits.tag
  val write_4ways_meta = WireInit(VecInit(Seq.fill(ways)(0.U(20.W))))
  write_4ways_meta(waymask_uint) := write_req.bits.meta

  when(write_req.valid){
    tagArray.write(set_idx, write_4ways_tag, waymask_onehot)
    metaArray.write(set_idx, write_4ways_meta, waymask_onehot)
  }


  io.array_read_resp <> DontCare
  io.array_write_req <> DontCare
}
