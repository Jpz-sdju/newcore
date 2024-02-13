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
    Module(
      new SRAMTemplate(
        gen = UInt(64.W),
        set = 64,
        way = ways,
        singlePort = true,
        shouldReset = true,
        holdRead = true
      )
    )
  )

  val tagArray = Module(
    new SRAMTemplate(
      gen = UInt((32 - 6 - 6).W),
      set = 64,
      way = ways,
      singlePort = true,
      shouldReset = true,
      holdRead = true
    )
  )
  val metaArray = Module(
    new SRAMTemplate(
      gen = UInt(2.W),
      set = 64,
      way = ways,
      singlePort = true,
      shouldReset = true,
      holdRead = true
    )
  )
  val tag_read_bus = Wire(
    new SRAMReadBus(gen = UInt((32 - 6 - 6).W), set = 64, way = ways)
  )
  val meta_read_bus = Wire(
    new SRAMReadBus(gen = UInt(2.W), set = 64, way = ways)
  )
  // 0-6 offset,6 idx,20tag
  val bank_idx = req.bits.addr(5, 3)
  val set_idx = req.bits.addr(11, 6)

  // read addr assign
  for (i <- 0 until 8) {
    val read = Wire(new SRAMReadBus(gen = UInt(64.W), set = 64, way = ways))
    read.apply(bank_idx === i.U && req.valid, set_idx)
    dataArray(i).io.r <> read
  }
  tag_read_bus.apply(req.valid, set_idx)
  meta_read_bus.apply(req.valid, set_idx)
  tagArray.io.r <> tag_read_bus
  metaArray.io.r <> meta_read_bus

  val seqq = VecInit(Seq.tabulate(8)(i => dataArray(i).io.r.resp.data))

  dontTouch(bank_idx)
  dontTouch(set_idx)
  // assign 4 ways RESULT to outer, bank_idx MUST BE REG!!!!!!
  resp.bits.data := seqq(RegNext(bank_idx))
  resp.bits.tag := tagArray.io.r.resp.data
  resp.bits.meta := metaArray.io.r.resp.data
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
      Seq.fill(banks)(
        VecInit(
          Seq.fill(ways)(0.U(64.W))
        )
      )
    )
  )
  for (bank <- 0 until 8) {
    bank_write_data(bank.U) :=
      write_req.bits.data(((bank + 1) * 64 - 1) % 256, (bank * 64) % 256)
    write_4ways_data(bank.U)(waymask_uint) := bank_write_data(bank)
  }

  // assign write data
  for (i <- 0 until 8) {
    val write = Wire(new SRAMWriteBus(gen = UInt(64.W), set = 64, way = ways))
    write.apply(
      valid = bankmask(i.U) && write_req.valid,
      data = write_4ways_data(i),
      setIdx = set_idx,
      waymask = waymask_onehot.asUInt
    )
    dataArray(i).io.w <> write
  }

  val tag_write_bus = Wire(
    new SRAMWriteBus(gen = UInt((32 - 6 - 6).W), set = 64, way = ways)
  )
  val meta_write_bus = Wire(
    new SRAMWriteBus(gen = UInt(2.W), set = 64, way = ways)
  )
  
  val write_4ways_tag = WireInit(VecInit(Seq.fill(ways)(0.U(20.W))))
  write_4ways_tag(waymask_uint) := write_req.bits.tag
  val write_4ways_meta = WireInit(VecInit(Seq.fill(ways)(0.U(20.W))))
  write_4ways_meta(waymask_uint) := write_req.bits.meta
  
  tag_write_bus.apply(valid = write_req.valid, data = write_4ways_tag, setIdx = set_idx, waymask = waymask_onehot.asUInt)
  meta_write_bus.apply(valid = write_req.valid, data = write_4ways_meta, setIdx = set_idx, waymask = waymask_onehot.asUInt)

  tagArray.io.w <> tag_write_bus
  metaArray.io.w <> meta_write_bus

  io.array_write_req.ready := true.B
}
