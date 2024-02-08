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
import freechips.rocketchip.diplomaticobjectmodel.model.U
import os.write
import os.group

class DcacheArray() extends Module with Setting {
  val io = IO(new Bundle {
    // set 16kb,4ways,not banked,linesize = 64B
    val read_req = Flipped(Decoupled(new ReadReq))

    val array_read_resp = DecoupledIO(new ArrayRespBundle)
    val array_write_req = Flipped(Decoupled(new ArrayWriteBundle))
  })
  val req = io.read_req
  val resp = io.array_read_resp

  // three arrays
  val dataArray = Array.fill(banks)(
    SyncReadMem(64, Vec(ways, UInt((64).W)))
  )
  val tagArray = SyncReadMem(64, Vec(ways, UInt((32 - 6 - 6).W)))
  val metaArray = SyncReadMem(64, Vec(ways, UInt((2).W)))

  // 0-6 offset,6 idx,20tag
  val bank_idx = req.bits.addr(5, 3)
  val set_idx = req.bits.addr(11, 6)
  val read_tag = Wire(Vec(4, UInt(20.W)))
  val read_meta = Wire(Vec(4, UInt(2.W)))

  val re1 = dataArray(0).read(set_idx,bank_idx === 0.U  && req.valid)
  val re2 = dataArray(1).read(set_idx,bank_idx === 1.U  && req.valid)
  val re3 = dataArray(2).read(set_idx,bank_idx === 2.U  && req.valid)
  val re4 = dataArray(3).read(set_idx,bank_idx === 3.U  && req.valid)
  val re5 = dataArray(4).read(set_idx,bank_idx === 4.U  && req.valid)
  val re6 = dataArray(5).read(set_idx,bank_idx === 5.U  && req.valid)
  val re7 = dataArray(6).read(set_idx,bank_idx === 6.U  && req.valid)
  val re8 = dataArray(7).read(set_idx,bank_idx === 7.U  && req.valid)

  val seqq = VecInit(re1,re2,re3,re4,re5,re6,re7,re8)
  read_tag := tagArray.read(set_idx)
  read_meta := metaArray.read(set_idx)
  dontTouch(bank_idx)
  dontTouch(set_idx)
  // assign 4 ways RESULT to outer, bank_idx MUST BE REG!!!!!!
  resp.bits.data := seqq(RegNext(bank_idx))
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

  // ugly code below,refact later
  val bank_write_data = WireInit(VecInit(Seq.fill(banks)(0.U(64.W))))
  val write_4ways_data = WireInit(
    VecInit(
      Seq.fill(banks)(VecInit(
        Seq.fill(ways)(0.U(64.W))
      ))
    )
  )
  for (bank <- 0 until 8) {
    bank_write_data(bank.U) := 
      write_req.bits.data(((bank + 1) * 64 - 1) % 256,(bank * 64) % 256)
    write_4ways_data(bank.U)(waymask_uint) := bank_write_data(bank)
  }
  for (id <- 0 until 8) {
    val cond = bankmask(id.U) && write_req.valid
    when(cond) {
      dataArray(id).write(set_idx, write_4ways_data(id), waymask_onehot)
    }
  }

  val write_4ways_tag = WireInit(VecInit(Seq.fill(ways)(0.U(20.W))))
  write_4ways_tag(waymask_uint) := write_req.bits.tag
  val write_4ways_meta = WireInit(VecInit(Seq.fill(ways)(0.U(20.W))))
  write_4ways_meta(waymask_uint) := write_req.bits.meta
  dontTouch(write_4ways_data)
  dontTouch(write_4ways_tag)
  dontTouch(write_4ways_meta)

  when(write_req.valid) {
    tagArray.write(set_idx, write_4ways_tag, waymask_onehot)
    metaArray.write(set_idx, write_4ways_meta, waymask_onehot)
  }

  io.array_write_req.ready := true.B
}
