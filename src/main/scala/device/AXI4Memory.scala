/** *************************************************************************************
  * Copyright (c) 2020-2022 Institute of Computing Technology, Chinese Academy
  * of Sciences
  *
  * XiangShan is licensed under Mulan PSL v2. You can use this software
  * according to the terms and conditions of the Mulan PSL v2. You may obtain a
  * copy of Mulan PSL v2 at: http://license.coscl.org.cn/MulanPSL2
  *
  * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY
  * KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
  * NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
  *
  * See the Mulan PSL v2 for more details.
  */

package device

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.experimental.ExtModule
import chisel3.util._
import freechips.rocketchip.amba.axi4.{
  AXI4MasterNode,
  AXI4Parameters,
  AXI4SlaveNode
}
import freechips.rocketchip.diplomacy.{
  AddressSet,
  InModuleBody,
  LazyModule,
  LazyModuleImp
}
import utils._

abstract class AXI4MemorySlave(
    slave: AXI4SlaveNode,
    memByte: Long,
    useBlackBox: Boolean = false
)(implicit p: Parameters)
    extends LazyModule {
  val master = AXI4MasterNode(List(slave.in.head._2.master))

  val portParam = slave.portParams.head
  val slaveParam = portParam.slaves.head
  val burstLen = portParam.maxTransfer / portParam.beatBytes

  val io_axi4 = InModuleBody { master.makeIOs() }

  lazy val module = new LazyModuleImp(this) {}
}

object AXI4MemorySlave {
  def apply(
      slave: AXI4SlaveNode,
      memByte: Long,
      useBlackBox: Boolean = false,
      dynamicLatency: Boolean = false
  )(implicit p: Parameters): AXI4MemorySlave = {
    val memory = if (dynamicLatency) {
      LazyModule(new AXI4RAMWrapper(slave, memByte, useBlackBox))
    } else {
      LazyModule(new AXI4RAMWrapper(slave, memByte, useBlackBox))
    }
    memory
  }
}
