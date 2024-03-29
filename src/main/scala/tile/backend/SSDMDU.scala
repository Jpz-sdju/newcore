/**************************************************************************************
 * Copyright (c) 2020 Institute of Computing Technology, CAS
 * Copyright (c) 2020 University of Chinese Academy of Sciences
 *
 * NutShell is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 *             http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR
 * FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/

package tile.backend


import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import utils._
import top.Setting
import freechips.rocketchip.config._
import tile.frontend._


// object MDUOpType {
//   def mul    = "b0000".U
//   def mulh   = "b0001".U
//   def mulhsu = "b0010".U
//   def mulhu  = "b0011".U
//   def div    = "b0100".U
//   def divu   = "b0101".U
//   def rem    = "b0110".U
//   def remu   = "b0111".U

//   def mulw   = "b1000".U
//   def divw   = "b1100".U
//   def divuw  = "b1101".U
//   def remw   = "b1110".U
//   def remuw  = "b1111".U

//   def isDiv(op: UInt) = op(2)
//   def isDivSign(op: UInt) = isDiv(op) && !op(0)
//   def isW(op: UInt) = op(3)
// }

class MulDivIO(val len: Int) extends Bundle with Setting{
  val in = Flipped(DecoupledIO(Vec(2, Output(UInt(len.W)))))
  val sign = Input(Bool())
  val out = DecoupledIO(Output(UInt((len * 2).W)))
  val divflush = Input(Bool())
}

class Multiplier(len: Int) extends Module {
  val io = IO(new MulDivIO(len))
  val latency = 1

  def DSPInPipe[T <: Data](a: T) = RegNext(a)
  def DSPOutPipe[T <: Data](a: T) = RegNext(a)
  val mulRes = (DSPInPipe(io.in.bits(0)).asSInt * DSPInPipe(io.in.bits(1)).asSInt)
  io.out.bits := DSPOutPipe(mulRes).asUInt
  io.out.valid := DSPOutPipe(DSPInPipe(io.in.fire))

  val busy = RegInit(false.B)
  when (io.in.valid && !busy) { busy := true.B }
  when (io.out.valid) { busy := false.B }
  io.in.ready := (if (latency == 0) true.B else !busy || io.out.valid)
}

class Divider(len: Int = 64) extends Module with Setting{
  val io = IO(new MulDivIO(len))

  val divflush = io.divflush

  def abs(a: UInt, sign: Bool): (Bool, UInt) = {
    val s = a(len - 1) && sign
    (s, Mux(s, -a, a))
  }

  val s_idle :: s_log2 :: s_shift :: s_compute :: s_finish :: Nil = Enum(5)
  val state = RegInit(s_idle)
  val newReq = (state === s_idle) && io.in.fire
  val anotherReq = (state === s_finish) && io.in.fire && io.out.ready

  //add reg
  val a_reg = RegEnable(io.in.bits(0),io.in.fire)
  val b_reg = RegEnable(io.in.bits(1),io.in.fire)
  val a_in = Mux(io.in.valid,io.in.bits(0),a_reg)
  val b_in = Mux(io.in.valid,io.in.bits(1),b_reg)
  val (a, b) = (a_in,b_in)
  //val (a, b) = (io.in.bits(0), io.in.bits(1))
  val divBy0 = b === 0.U(len.W)

  val shiftReg = Reg(UInt((1 + len * 2).W))
  val hi = shiftReg(len * 2, len)
  val lo = shiftReg(len - 1, 0)

  val (aSign, aVal) = abs(a, io.sign)
  val (bSign, bVal) = abs(b, io.sign)
  val aSignReg = RegEnable(aSign, newReq || anotherReq)
  val qSignReg = RegEnable((aSign ^ bSign) && !divBy0, newReq || anotherReq)
  val bReg = RegEnable(bVal, newReq || anotherReq)
  val aValx2Reg = RegEnable(Cat(aVal, "b0".U), newReq || anotherReq)

  val cnt = Counter(len)
//  dontTouch(cnt.value)
  when (newReq) {
    state := s_log2
  } .elsewhen (state === s_log2) {
    // `canSkipShift` is calculated as following:
    //   bEffectiveBit = Log2(bVal, XLEN) + 1.U
    //   aLeadingZero = 64.U - aEffectiveBit = 64.U - (Log2(aVal, XLEN) + 1.U)
    //   canSkipShift = aLeadingZero + bEffectiveBit
    //     = 64.U - (Log2(aVal, XLEN) + 1.U) + Log2(bVal, XLEN) + 1.U
    //     = 64.U + Log2(bVal, XLEN) - Log2(aVal, XLEN)
    //     = (64.U | Log2(bVal, XLEN)) - Log2(aVal, XLEN)  // since Log2(bVal, XLEN) < 64.U
    val canSkipShift = (len.U | Log2(bReg)) - Log2(aValx2Reg)
    // When divide by 0, the quotient should be all 1's.
    // Therefore we can not shift in 0s here.
    // We do not skip any shift to avoid this.
    cnt.value := Mux(divBy0, 0.U, Mux(canSkipShift >= (len-1).U, (len-1).U, canSkipShift))
    when(divflush) {
      state := s_idle
      shiftReg := 0.U
      cnt.value := 0.U
    } .elsewhen (!divflush) {
      state := s_shift
    }
  } .elsewhen (state === s_shift) {
    shiftReg := aValx2Reg << cnt.value
    state := s_compute
  } .elsewhen (state === s_compute) {
    val enough = hi.asUInt >= bReg.asUInt
    shiftReg := Cat(Mux(enough, hi - bReg, hi)(len - 1, 0), lo, enough)
    cnt.inc()
    when (cnt.value === (len-1).U) { state := s_finish }
    when (divflush) {
      state := s_idle
      shiftReg := 0.U
      cnt.value := 0.U
    }
  } .elsewhen (state === s_finish && io.out.ready && !io.in.fire) {
    state := s_idle
  }.elsewhen (anotherReq ) {
    state := s_log2
  }

  val r = hi(len, 1)
  val resQ = Mux(qSignReg, -lo, lo)
  val resR = Mux(aSignReg, -r, r)
  io.out.bits := Cat(resR, resQ)

  io.out.valid := (if (HasDiv) (state === s_finish) else io.in.valid) // FIXME: should deal with ready = 0
  io.in.ready := (state === s_idle) || io.out.valid
}

class MDUIO extends FunctionUnitIO with Setting{
  val divflush = Input(Bool())
}

class SSDMDU(implicit p: Parameters) extends Module with Setting{
  val io = IO(new MDUIO)

  val (valid, src1, src2, func) = (io.in.valid, io.in.bits.src1, io.in.bits.src2, io.in.bits.func)
  val isDiv = MDUOpType.isDiv(func)
  val isDivSign = MDUOpType.isDivSign(func)
  val isW = MDUOpType.isW(func)
  val isHi = !(func(1,0) === MDUOpType.mul(1,0))

  val mul = Module(new ArrayMultiplier(XLEN + 1))
  val div = Module(new Divider(XLEN))
  div.io.divflush := io.divflush
  List(div.io).map { case x =>
    x.sign := isDivSign
    x.out.ready := io.out.ready
  }

  val signext = SignExt(_: UInt, XLEN+1)
  val zeroext = ZeroExt(_: UInt, XLEN+1)
  val mulInputFuncTable = List(
    MDUOpType.mul    -> (zeroext, zeroext),
    MDUOpType.mulh   -> (signext, signext),
    MDUOpType.mulhsu -> (signext, zeroext),
    MDUOpType.mulhu  -> (zeroext, zeroext)
  )
  mul.io.in.bits.src(0) := LookupTree(func(1,0), mulInputFuncTable.map(p => (p._1(1,0), p._2._1(src1))))
  mul.io.in.bits.src(1) := LookupTree(func(1,0), mulInputFuncTable.map(p => (p._1(1,0), p._2._2(src2))))
  mul.io.in.bits.src(2) := DontCare
  mul.io.ctrl.sign := DontCare
  mul.io.ctrl.isW := isW
  mul.io.ctrl.isHi := isHi
  mul.io.out.ready := io.out.ready

  val divInputFunc = (x: UInt) => Mux(isW, Mux(isDivSign, SignExt(x(31,0), XLEN), ZeroExt(x(31,0), XLEN)), x)
  div.io.in.bits(0) := divInputFunc(src1)
  div.io.in.bits(1) := divInputFunc(src2)

  mul.io.in.valid := io.in.valid && !isDiv
  div.io.in.valid := io.in.valid && isDiv

  val mulRes = mul.io.out.bits
  val divResTmp = Mux(MDUOpType.isR(RegEnable(func, io.in.valid)) /* rem */, div.io.out.bits(2*XLEN-1,XLEN), div.io.out.bits(XLEN-1,0))
  val divRes = Mux(isW, SignExt(divResTmp(31,0),XLEN), divResTmp)

  io.out.bits := Mux(mul.io.out.valid, mulRes.asUInt, divRes)

  io.in.ready := Mux(isDiv, div.io.in.ready, mul.io.in.ready)
  io.out.valid := div.io.out.valid || mul.io.out.valid

}
