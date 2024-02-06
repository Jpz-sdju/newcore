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

class CacheFSM()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val iread_req = Flipped(Decoupled(new ReadReq))
  })  

  val req = io.iread_req
  val req_reg = RegNext(req.bits)
  val req_valid = RegNext(req.valid)


  val array = Module(new IcacheArray)
  array.io.iread_req <> req
  array.io.array_write_req <> DontCare

  //array read result
  val array_resp = array.io.array_read_resp
  array_resp.ready := true.B

  val meta = array_resp.bits.meta
  val data = array_resp.bits.data
  val tag = array_resp.bits.tag

  val meta_hit = VecInit(meta.map(_ === "b11".U))
  val tag_hit = VecInit(tag.map(_ === req_reg.addr(31,12)))

  dontTouch(meta_hit)
  dontTouch(tag_hit)
}
