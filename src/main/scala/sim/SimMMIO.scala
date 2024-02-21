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

package sim

import chisel3._
import chisel3.util._
import device.{AXI4UART, _}
import difftest.UARTIO
//import difftest._

import freechips.rocketchip.diplomacy.{IdRange, LazyModule, LazyModuleImp, TransferSizes, AddressSet, InModuleBody}
import chipsalliance.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.amba.axi4._

class SimMMIO(edge: AXI4EdgeParameters)(implicit p:Parameters) extends LazyModule {
  
  val node = AXI4MasterNode(List(edge.master))
  val uart = LazyModule(new AXI4UART(Seq(AddressSet(0x40600000L, 0xf))))
  val axiBus = AXI4Xbar()
  axiBus := node
  uart.node := axiBus
  
  val io_axi4 = InModuleBody {
    node.makeIOs()
  }

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      // val rw = Flipped(new SimpleBusUC)
      val meip = Output(Bool())
      val uart = new UARTIO
    })

    val devAddrSpace = List(
      //(0x40600000L, 0x10L), // uart
      (0x50000000L, 0x400000L), // vmem
      (0x40001000L, 0x8L),  // vga ctrl
      (0x40000000L, 0x1000L),  // flash
      (0x40002000L, 0x1000L), // dummy sdcard
      (0x40004000L, 0x1000L), // meipGen
      (0x40003000L, 0x1000L)  // dma
    )

    // val xbar = Module(new SimpleBusCrossbar1toN(devAddrSpace))
    // xbar.io.in <> io.rw
    // xbar.io.in <> DontCare

    // xbar.io.out.map(_ <> DontCare)

    io.uart <> uart.module.io.extra.get
  }

}
