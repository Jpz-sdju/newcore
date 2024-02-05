package tile.backend

import chisel3._, utils._, util._
import bus._
import freechips.rocketchip.config._
import tile._
import top.Setting
import frontend._

class Jump(implicit p: Parameters) extends Module with Setting {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Bundle {
      val cf = new CfCtrl
      val src = Vec(2, UInt(XLEN.W))
    }))
    val out = Decoupled(new Bundle {
      val result = UInt(XLEN.W)
    })
    val redirect = DecoupledIO(UInt(XLEN.W))
  })

  io.out.valid := io.in.valid
  io.in.ready := io.out.ready

  val is_auipc = JumpOpType.jumpOpisAuipc(io.in.bits.cf.ctrl.fuOpType)

  io.out.bits.result := Mux(is_auipc, io.in.bits.src(0) + io.in.bits.src(1),io.in.bits.src(0) + 4.U)
  
  //
  io.redirect.bits := io.in.bits.src(0) + io.in.bits.src(1)
  io.redirect.valid := !is_auipc && io.in.valid


}
