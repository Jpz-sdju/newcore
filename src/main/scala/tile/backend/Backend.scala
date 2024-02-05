package tile.backend

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import bus._
import tile._
import freechips.rocketchip.util.DontTouch
import top.Setting
import utils.PipelineConnect
import _root_.tile.frontend._

class Backend()(implicit p: Parameters) extends Module with Setting {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new PipelineBundle))

    val redirect = (DecoupledIO(UInt(XLEN.W)))
  })
  val in = io.in.bits
  val pc = io.in.bits.cf.cf.pc

  /* 
  IDU TO EXU
  */
  
  val alu = Module(new ALU)
  alu.io.in.bits.cf :=  in.cf
  alu.io.in.bits.src(0) := in.Src1
  alu.io.in.bits.src(1) := in.Src2
  alu.io.in.valid := in.isAlu 
  //assign redirect
  val redirect = Wire(DecoupledIO(UInt(XLEN.W)))
  val jal = in.cf.ctrl.fuOpType === JumpOpType.jal
  val jalr = in.cf.ctrl.fuOpType === JumpOpType.jalr
  val pc_with_offset = pc + in.Imm
  val jmp_target = Mux(jal, pc_with_offset, in.Src1 + in.Imm )
  redirect.bits := Mux(in.isBranch, pc_with_offset, jmp_target )
  redirect.valid := io.in.valid &&( jal || jalr || alu.io.taken_branch)
  io.redirect <> redirect
  
  
  //assign to exu out,FILL lsAddr!
  val exu_out = Wire(Decoupled(new PipelineBundle))
  val mem_in = Wire(Decoupled(new PipelineBundle))
  
  exu_out.bits := io.in.bits
  exu_out.bits.lsAddr := in.Src1 + in.Imm
  exu_out.valid := io.in.valid
  
  //ready transmit to frontedn
  io.in.ready := exu_out.ready
  /* 
  EX to DCACHE
  */
  PipelineConnect(exu_out, mem_in, mem_in.fire,false.B)
  
  
  mem_in.ready := true.B
  dontTouch(mem_in)
  alu.io.out.ready := true.B
  dontTouch(alu.io.out)
  dontTouch(io.in)
}
