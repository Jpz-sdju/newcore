package tile.backend

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import bus._
import tile._


class Backend()(implicit p: Parameters) extends Module{
    val io = IO(new Bundle{
        val in = Flipped(Decoupled(new CfCtrl))
    })

    val alu = Module(new ALU)
    alu.io.in.cf <> io.in
    alu.io.in.src := DontCare
    alu.io.out <> DontCare
}