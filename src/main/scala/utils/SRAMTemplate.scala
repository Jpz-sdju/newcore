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

package utils

import chisel3._
import chisel3.experimental.ExtModule
import chisel3.util._

/*class TS5N28HPCPLVTA128X64M2F extends ExtModule with HasExtModuleResource {
  //  val io = IO(new Bundle {
  val Q =   IO(Output(UInt(64.W)))
  val CLK = IO(Input(Clock()))
  val CEB = IO(Input(Bool()))
  val WEB = IO(Input(Bool()))
  val A =   IO(Input(UInt(7.W)))
  val D =   IO(Input(UInt(64.W)))
  //  })
  addResource("/vsrc/TS5N28HPCPLVTA128X64M2F.v")
}*/

class TS5N28HPCPLVTA128X32M2F extends ExtModule with HasExtModuleResource {
  //  val io = IO(new Bundle {
  val Q =   IO(Output(UInt(32.W)))
  val CLK = IO(Input(Clock()))
  val CEB = IO(Input(Bool()))
  val WEB = IO(Input(Bool()))
  val A =   IO(Input(UInt(7.W)))
  val D =   IO(Input(UInt(32.W)))
  //  })
  addResource("/vsrc/TS5N28HPCPLVTA128X32M2F.v")
}

class TS5N28HPCPLVTA64X32M2F extends ExtModule with HasExtModuleResource {
  //  val io = IO(new Bundle {
  val Q =   IO(Output(UInt(32.W)))
  val CLK = IO(Input(Clock()))
  val CEB = IO(Input(Bool()))
  val WEB = IO(Input(Bool()))
  val A =   IO(Input(UInt(6.W)))
  val D =   IO(Input(UInt(32.W)))
  //  })
  addResource("/vsrc/TS5N28HPCPLVTA64X32M2F.v")
}

class TS5N28HPCPLVTA32X64M2F extends ExtModule with HasExtModuleResource {
  //  val io = IO(new Bundle {
  val Q =   IO(Output(UInt(64.W)))
  val CLK = IO(Input(Clock()))
  val CEB = IO(Input(Bool()))
  val WEB = IO(Input(Bool()))
  val A =   IO(Input(UInt(5.W)))
  val D =   IO(Input(UInt(64.W)))
  //  })
  addResource("/vsrc/TS5N28HPCPLVTA32X64M2F.v")
}

class TS5N28HPCPLVTA64X8M2F extends ExtModule with HasExtModuleResource {
  //  val io = IO(new Bundle {
  val Q =   IO(Output(UInt(8.W)))
  val CLK = IO(Input(Clock()))
  val CEB = IO(Input(Bool()))
  val WEB = IO(Input(Bool()))
  val A =   IO(Input(UInt(6.W)))
  val D =   IO(Input(UInt(8.W)))
  //  })
  addResource("/vsrc/TS5N28HPCPLVTA64X8M2F.v")
}

class TS5N28HPCPLVTA128X80M2F extends ExtModule with HasExtModuleResource {
  //  val io = IO(new Bundle {
  val Q =   IO(Output(UInt(80.W)))
  val CLK = IO(Input(Clock()))
  val CEB = IO(Input(Bool()))
  val WEB = IO(Input(Bool()))
  val A =   IO(Input(UInt(7.W)))
  val D =   IO(Input(UInt(80.W)))
  //  })
  addResource("/vsrc/TS5N28HPCPLVTA128X80M2F.v")
}

/*class TS5N28HPCPLVTA128X64M2F extends Module {
  val Q =   IO(Output(UInt(64.W)))
  val CLK = IO(Input(Clock()))
  val CEB = IO(Input(Bool()))
  val WEB = IO(Input(Bool()))
  val A =   IO(Input(UInt(7.W)))
  val D =   IO(Input(UInt(64.W)))
  
  val sram = Seq.fill(2)(Module(new TS5N28HPCPLVTA128X32M2F()))
  sram.map(_.CLK := CLK)
  sram.zipWithIndex.map{
    case (s, i) => s.CEB := CEB
  }
  sram.zipWithIndex.map{
    case (s, i) => s.WEB := WEB
  }
  sram.zipWithIndex.map{
    case (s, i) => s.A := A
  }

  sram(0).D := D(31, 0)
  sram(1).D := D(63, 32)

  Q := Cat(sram(1).Q, sram(0).Q)
}*/

class TS5N28HPCPLVTA128X64M2F extends Module {
  val Q =   IO(Output(UInt(64.W)))
  val CLK = IO(Input(Clock()))
  val CEB = IO(Input(Bool()))
  val WEB = IO(Input(Bool()))
  val A =   IO(Input(UInt(7.W)))
  val D =   IO(Input(UInt(64.W)))
  
  val sram = Seq.fill(4)(Module(new TS5N28HPCPLVTA32X64M2F()))
  sram.map(_.CLK := CLK)
  sram.zipWithIndex.map{
    case (s, i) => s.CEB := CEB || ~(i.asUInt === A(6, 5))
  }
  sram.zipWithIndex.map{
    case (s, i) => s.WEB := WEB || ~(i.asUInt === A(6, 5))
  }
  sram.zipWithIndex.map{
    case (s, i) => s.A := A(4, 0)
  }
  
  sram.map(_.D := D)

  val sel = RegNext(A(6, 5))
  Q := MuxLookup(sel.asUInt, 0.U(64.W), Array(
      0.U -> sram(0).Q,
      1.U -> sram(1).Q,
      2.U -> sram(2).Q,
      3.U -> sram(3).Q)
  ) 
}

class SRAMBundleA(val set: Int) extends Bundle {
  val setIdx = Output(UInt(log2Up(set).W))

  def apply(setIdx: UInt) = {
    this.setIdx := setIdx
    this
  }
}
class SramIO extends Bundle {
  val rData: UInt = Output(UInt(128.W))

  val en: Bool = Input(Bool())
  val idx: UInt =Input(UInt(6.W))
  val wen: Bool =Input(Bool())
  val wMask: UInt = Input(UInt(128.W))
  val wData: UInt = Input(UInt(128.W))
}

class SRAMBundleAW[T <: Data](private val gen: T, set: Int, val way: Int = 1) extends SRAMBundleA(set) {
  val data = Output(gen)
  val waymask = if (way > 1) Some(Output(UInt(way.W))) else None

  def apply(data: T, setIdx: UInt, waymask: UInt) = {
    super.apply(setIdx)
    this.data := data
    this.waymask.map(_ := waymask)
    this
  }
}

class SRAMBundleR[T <: Data](private val gen: T, val way: Int = 1) extends Bundle {
  val data = Output(Vec(way, gen))
}

class SRAMReadBus[T <: Data](private val gen: T, val set: Int, val way: Int = 1) extends Bundle {
  val req = Decoupled(new SRAMBundleA(set))
  val resp = Flipped(new SRAMBundleR(gen, way))

  def apply(valid: Bool, setIdx: UInt) = {
    this.req.bits.apply(setIdx)
    this.req.valid := valid
    this
  }
}

class SRAMWriteBus[T <: Data](private val gen: T, val set: Int, val way: Int = 1) extends Bundle {
  val req = Decoupled(new SRAMBundleAW(gen, set, way))

  def apply(valid: Bool, data: T, setIdx: UInt, waymask: UInt) = {
    this.req.bits.apply(data = data, setIdx = setIdx, waymask = waymask)
    this.req.valid := valid
    this
  }
}

class SRAMTemplate[T <: Data](gen: T, set: Int, way: Int = 1,
                              shouldReset: Boolean = false, holdRead: Boolean = false, singlePort: Boolean = false, isDcache: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val r = Flipped(new SRAMReadBus(gen, set, way))
    val w = Flipped(new SRAMWriteBus(gen, set, way))
  })

  val wordType = UInt(gen.getWidth.W)
  val array = SyncReadMem(set, Vec(way, wordType))
  val (resetState, resetSet) = (WireInit(false.B), WireInit(0.U))

  if (shouldReset) {
    val _resetState = RegInit(true.B)
    val (_resetSet, resetFinish) = Counter(_resetState, set)
    when (resetFinish) { _resetState := false.B }

    resetState := _resetState
    resetSet := _resetSet
  }

  val (ren, wen) = (io.r.req.valid, io.w.req.valid || resetState)
  val realRen = (if (singlePort) ren && !wen else ren)

  val setIdx = Mux(resetState, resetSet, io.w.req.bits.setIdx)
  val wdataword = Mux(resetState, 0.U.asTypeOf(wordType), io.w.req.bits.data.asUInt)
  val waymask = Mux(resetState, Fill(way, "b1".U), io.w.req.bits.waymask.getOrElse("b1".U))
  val wdata = VecInit(Seq.fill(way)(wdataword))
  when (wen) { array.write(setIdx, wdata, waymask.asBools) }

  val rdata = (if (holdRead) ReadAndHold(array, io.r.req.bits.setIdx, realRen)
  else array.read(io.r.req.bits.setIdx, realRen)).map(_.asTypeOf(gen))
  io.r.resp.data := VecInit(rdata)

  io.r.req.ready := !resetState && (if (singlePort) !wen else true.B)
  io.w.req.ready := true.B

  println("L1 + BTB len: %d, set: %d, way: %d\n", gen.getWidth.W, set, way)

  Debug(false) {
    when (wen && setIdx === 0x9c.U) {
      printf("%d: SRAMTemplate: write %x to idx = %d\n", GTimer(), wdata.asUInt, setIdx)
    }
    when (RegNext(realRen) && setIdx === 0x9c.U) {
      printf("%d: SRAMTemplate: read %x at idx = %d\n", GTimer(), VecInit(rdata).asUInt, RegNext(io.r.req.bits.setIdx))
    }
  }
}

class SRAMTemplateWithArbiter[T <: Data](nRead: Int, gen: T, set: Int, way: Int = 1,
                                         shouldReset: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val r = Flipped(Vec(nRead, new SRAMReadBus(gen, set, way)))
    val w = Flipped(new SRAMWriteBus(gen, set, way))
  })

  val ram = Module(new SRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
  ram.io.w <> io.w

  val readArb = Module(new Arbiter(chiselTypeOf(io.r(0).req.bits), nRead))
  readArb.io.in <> io.r.map(_.req)
  ram.io.r.req <> readArb.io.out

  // latch read results
  io.r.map{ case r => {
    r.resp.data := HoldUnless(ram.io.r.resp.data, RegNext(r.req.fire))
  }}
}

class BTBSRAMTemplate[T <: Data](gen: T, set: Int, way: Int = 1,
                                 shouldReset: Boolean = false, holdRead: Boolean = false, singlePort: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val r = Flipped(new SRAMReadBus(gen, set, way))
    val w = Flipped(new SRAMWriteBus(gen, set, way))
  })

  require(holdRead)
  val wordType = UInt(gen.getWidth.W)
  // val array = SyncReadMem(set, Vec(way, wordType))
  val sram = Module(new TS5N28HPCPLVTA128X80M2F())

  val (resetState, resetSet) = (WireInit(false.B), WireInit(0.U))

  if (shouldReset) {
    val _resetState = RegInit(true.B)
    val (_resetSet, resetFinish) = Counter(_resetState, set)
    when (resetFinish) { _resetState := false.B }

    resetState := _resetState
    resetSet := _resetSet
  }

  val (ren, wen) = (io.r.req.valid, io.w.req.valid || resetState)
  val realRen = (if (singlePort) ren && !wen else ren)

  val setIdx = Mux(resetState, resetSet, io.w.req.bits.setIdx)
  val wdataword = Mux(resetState, 0.U.asTypeOf(wordType), io.w.req.bits.data.asUInt)
  val waymask = Mux(resetState, Fill(way, "b1".U), io.w.req.bits.waymask.getOrElse("b1".U))
  val wdata = VecInit(Seq.fill(way)(wdataword))
  // when (wen) { array.write(setIdx, wdata, waymask.asBools) }

  sram.CLK := clock
  sram.A := Mux(wen, setIdx, io.r.req.bits.setIdx)
  sram.CEB := ~(wen || realRen)
  sram.WEB := ~wen
  sram.D := Cat(0.U((80-gen.getWidth).W), Cat(wdata))

  val rdata = HoldUnless(sram.Q, RegNext(realRen, false.B))
  io.r.resp.data := rdata(gen.getWidth-1, 0).asTypeOf(io.r.resp.data)

  io.r.req.ready := !resetState && (if (singlePort) !wen else true.B)
  io.w.req.ready := true.B

  Debug(false) {
    when (wen) {
      printf("%d: SRAMTemplate: write %x to idx = %d\n", GTimer(), wdata.asUInt, setIdx)
    }
    when (RegNext(realRen)) {
      printf("%d: SRAMTemplate: read %x at idx = %d\n", GTimer(), VecInit(rdata).asUInt, RegNext(io.r.req.bits.setIdx))
    }
  }
}

class DataSRAMTemplate[T <: Data](gen: T, set: Int, way: Int = 1,
                                  shouldReset: Boolean = false, holdRead: Boolean = false, singlePort: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val r = Flipped(new SRAMReadBus(gen, set, way))
    val w = Flipped(new SRAMWriteBus(gen, set, way))
  })

  require(!holdRead)
  val wordType = UInt(gen.getWidth.W)
  val sram = Seq.fill(way)(Module(new TS5N28HPCPLVTA128X64M2F()))
  val (resetState, resetSet) = (WireInit(false.B), WireInit(0.U))

  if (shouldReset) {
    val _resetState = RegInit(true.B)
    val (_resetSet, resetFinish) = Counter(_resetState, set)
    when (resetFinish) { _resetState := false.B }

    resetState := _resetState
    resetSet := _resetSet
  }

  val (ren, wen) = (io.r.req.valid, io.w.req.valid || resetState)
  val realRen = (if (singlePort) ren && !wen else ren)

  val setIdx = Mux(resetState, resetSet, io.w.req.bits.setIdx)
  val wdataword = Mux(resetState, 0.U.asTypeOf(wordType), io.w.req.bits.data.asUInt)
  val waymask = Mux(resetState, Fill(way, "b1".U), io.w.req.bits.waymask.getOrElse("b1".U))
  val wdata = VecInit(Seq.fill(way)(wdataword))
  // when (wen) { array.write(setIdx, wdata, waymask.asBools) }

  sram.map(_.CLK := clock)
  sram.map(_.A := Mux(wen, setIdx, io.r.req.bits.setIdx))
  sram.zipWithIndex.map{
    case (s, i) => s.CEB := ~(wen || realRen)
  }
  sram.zipWithIndex.map{
    case (s, i) => s.WEB :=  ~(wen && waymask(i))
  }
  sram.map(_.D := wdataword)

  val rdata = VecInit(sram.map(_.Q)).map(_.asTypeOf(gen))
  io.r.resp.data := VecInit(rdata)

  io.r.req.ready := !resetState && (if (singlePort) !wen else true.B)
  io.w.req.ready := true.B

  Debug(false) {
    when (wen) {
      printf("%d: SRAMTemplate: write %x to idx = %d\n", GTimer(), wdata.asUInt, setIdx)
    }
    when (RegNext(realRen)) {
      printf("%d: SRAMTemplate: read %x at idx = %d\n", GTimer(), VecInit(rdata).asUInt, RegNext(io.r.req.bits.setIdx))
    }
  }
}

class MetaSRAMTemplate[T <: Data](gen: T, set: Int, way: Int = 1,
                                  shouldReset: Boolean = false, holdRead: Boolean = false, singlePort: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val r = Flipped(new SRAMReadBus(gen, set, way))
    val w = Flipped(new SRAMWriteBus(gen, set, way))
  })

  require(!holdRead)
  val wordType = UInt(gen.getWidth.W)
  val sram = Seq.fill(way)(Module(new TS5N28HPCPLVTA64X8M2F()))
  val (resetState, resetSet) = (WireInit(false.B), WireInit(0.U))

  if (shouldReset) {
    val _resetState = RegInit(true.B)
    val (_resetSet, resetFinish) = Counter(_resetState, set)
    when (resetFinish) { _resetState := false.B }

    resetState := _resetState
    resetSet := _resetSet
  }

  val (ren, wen) = (io.r.req.valid, io.w.req.valid || resetState)
  val realRen = (if (singlePort) ren && !wen else ren)

  val setIdx = Mux(resetState, resetSet, io.w.req.bits.setIdx)
  val wdataword = Mux(resetState, 0.U.asTypeOf(wordType), io.w.req.bits.data.asUInt)
  val waymask = Mux(resetState, Fill(way, "b1".U), io.w.req.bits.waymask.getOrElse("b1".U))
  val wdata = VecInit(Seq.fill(way)(wdataword))
  // when (wen) { array.write(setIdx, wdata, waymask.asBools) }

  sram.map(_.CLK := clock)
  sram.map(_.A := Mux(wen, setIdx, io.r.req.bits.setIdx))
  sram.zipWithIndex.map{
    case (s, i) => s.CEB := ~(wen || realRen)
  }
  sram.zipWithIndex.map{
    case (s, i) => s.WEB := ~(wen && waymask(i))
  }
  sram.map(_.D := wdataword)

  val rdata = VecInit(sram.map(_.Q)).map(_.asTypeOf(gen))
  io.r.resp.data := VecInit(rdata)

  io.r.req.ready := !resetState && (if (singlePort) !wen else true.B)
  io.w.req.ready := true.B

  Debug(false) {
    when (wen) {
      printf("%d: SRAMTemplate: write %x to idx = %d\n", GTimer(), wdata.asUInt, setIdx)
    }
    when (RegNext(realRen)) {
      printf("%d: SRAMTemplate: read %x at idx = %d\n", GTimer(), VecInit(rdata).asUInt, RegNext(io.r.req.bits.setIdx))
    }
  }
}

class TagSRAMTemplate[T <: Data](gen: T, set: Int, way: Int = 1,
                                  shouldReset: Boolean = false, holdRead: Boolean = false, singlePort: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val r = Flipped(new SRAMReadBus(gen, set, way))
    val w = Flipped(new SRAMWriteBus(gen, set, way))
  })

  require(!holdRead)
  val wordType = UInt(gen.getWidth.W)
  val sram = Seq.fill(way)(Module(new TS5N28HPCPLVTA64X32M2F()))
  val (resetState, resetSet) = (WireInit(false.B), WireInit(0.U))

  if (shouldReset) {
    val _resetState = RegInit(true.B)
    val (_resetSet, resetFinish) = Counter(_resetState, set)
    when (resetFinish) { _resetState := false.B }

    resetState := _resetState
    resetSet := _resetSet
  }

  val (ren, wen) = (io.r.req.valid, io.w.req.valid || resetState)
  val realRen = (if (singlePort) ren && !wen else ren)

  val setIdx = Mux(resetState, resetSet, io.w.req.bits.setIdx)
  val wdataword = Mux(resetState, 0.U.asTypeOf(wordType), io.w.req.bits.data.asUInt)
  val waymask = Mux(resetState, Fill(way, "b1".U), io.w.req.bits.waymask.getOrElse("b1".U))
  val wdata = VecInit(Seq.fill(way)(wdataword))
  // when (wen) { array.write(setIdx, wdata, waymask.asBools) }

  sram.map(_.CLK := clock)
  sram.map(_.A := Mux(wen, setIdx, io.r.req.bits.setIdx))
  sram.zipWithIndex.map{
    case (s, i) => s.CEB := ~(wen || realRen)
  }
  sram.zipWithIndex.map{
    case (s, i) => s.WEB := ~(wen && waymask(i))
  }
  sram.map(_.D := wdataword)

  val rdata = VecInit(sram.map(_.Q)).map(_.asTypeOf(gen))
  io.r.resp.data := VecInit(rdata)

  io.r.req.ready := !resetState && (if (singlePort) !wen else true.B)
  io.w.req.ready := true.B

  Debug(false) {
    when (wen) {
      printf("%d: SRAMTemplate: write %x to idx = %d\n", GTimer(), wdata.asUInt, setIdx)
    }
    when (RegNext(realRen)) {
      printf("%d: SRAMTemplate: read %x at idx = %d\n", GTimer(), VecInit(rdata).asUInt, RegNext(io.r.req.bits.setIdx))
    }
  }
}

//class SRAMTemplateWithArbiter[T <: Data](nRead: Int, gen: T, set: Int, way: Int = 1,
//                                         shouldReset: Boolean = false, isData: Boolean = false) extends Module {
//  val io = IO(new Bundle {
//    val r = Flipped(Vec(nRead, new SRAMReadBus(gen, set, way)))
//    val w = Flipped(new SRAMWriteBus(gen, set, way))
//  })
//
//  val ram = if (isData) Module(new DataSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
//            else Module(new MetaSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
////  when(isData.asBool()) {
//    val ram = Module(new DataSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
////  }.otherwise {
////    val ram = Module(new MetaSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
////  }
//  println("len: %d, set: %d, way: %d\n", gen.getWidth.W, set, way)
//  ram.io.w <> io.w
//
//  val readArb = Module(new Arbiter(chiselTypeOf(io.r(0).req.bits), nRead))
//  readArb.io.in <> io.r.map(_.req)
//  ram.io.r.req <> readArb.io.out
//
//  // latch read results
//  io.r.map{ case r => {
//    r.resp.data := HoldUnless(ram.io.r.resp.data, RegNext(r.req.fire))
//  }}
//}

class DataSRAMTemplateWithArbiter[T <: Data](nRead: Int, gen: T, set: Int, way: Int = 1,
                                             shouldReset: Boolean = false) extends Module {
  val io = IO(new Bundle {//3
    val r = Flipped(Vec(nRead, new SRAMReadBus(gen, set, way)))
    val w = Flipped(new SRAMWriteBus(gen, set, way))
  })

  //  val ram = if (isData) Module(new DataSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
  //  else Module(new MetaSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
  //  when(isData.asBool()) {
  //val ram = Module(new DataSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = false))
  val ram = Module(new DataSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
  //  }.otherwise {
  //    val ram = Module(new MetaSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
  //  }
  //println("len: %d, set: %d, way: %d\n", gen.getWidth.W, set, way)
  ram.io.w <> io.w

  val readArb = Module(new Arbiter(chiselTypeOf(io.r(0).req.bits), nRead))
  readArb.io.in <> io.r.map(_.req)
  ram.io.r.req <> readArb.io.out

  // latch read results
  io.r.map{ case r => {
    r.resp.data := HoldUnless(ram.io.r.resp.data, RegNext(r.req.fire))
  }}
}

class MetaSRAMTemplateWithArbiter[T <: Data](nRead: Int, gen: T, set: Int, way: Int = 1,
                                             shouldReset: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val r = Flipped(Vec(nRead, new SRAMReadBus(gen, set, way)))
    val w = Flipped(new SRAMWriteBus(gen, set, way))
  })

  //  val ram = if (isData) Module(new DataSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
  //  else Module(new MetaSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
  //  when(isData.asBool()) {
  val ram = Module(new MetaSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
  //  }.otherwise {
  //    val ram = Module(new MetaSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
  //  }
  //println("len: %d, set: %d, way: %d\n", gen.getWidth.W, set, way)
  ram.io.w <> io.w

  val readArb = Module(new Arbiter(chiselTypeOf(io.r(0).req.bits), nRead))
  readArb.io.in <> io.r.map(_.req)
  ram.io.r.req <> readArb.io.out

  // latch read results
  io.r.map{ case r => {
    r.resp.data := HoldUnless(ram.io.r.resp.data, RegNext(r.req.fire))
  }}
}

class TagSRAMTemplateWithArbiter[T <: Data](nRead: Int, gen: T, set: Int, way: Int = 1,
                                             shouldReset: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val r = Flipped(Vec(nRead, new SRAMReadBus(gen, set, way)))
    val w = Flipped(new SRAMWriteBus(gen, set, way))
  })

  //  val ram = if (isData) Module(new DataSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
  //  else Module(new MetaSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
  //  when(isData.asBool()) {
  val ram = Module(new TagSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
  //  }.otherwise {
  //    val ram = Module(new MetaSRAMTemplate(gen, set, way, shouldReset, holdRead = false, singlePort = true))
  //  }
  //println("len: %d, set: %d, way: %d\n", gen.getWidth.W, set, way)
  ram.io.w <> io.w

  val readArb = Module(new Arbiter(chiselTypeOf(io.r(0).req.bits), nRead))
  readArb.io.in <> io.r.map(_.req)
  ram.io.r.req <> readArb.io.out

  // latch read results
  io.r.map{ case r => {
    r.resp.data := HoldUnless(ram.io.r.resp.data, RegNext(r.req.fire))
  }}
}