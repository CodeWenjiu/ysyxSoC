package ysyx

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.amba._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class AXI4DelayerIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Reset())
  val in = Flipped(new AXI4Bundle(AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4)))
  val out = new AXI4Bundle(AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4))
}

class axi4_delayer extends BlackBox {
  val io = IO(new AXI4DelayerIO)
}

class AXI4DelayerChisel extends Module {
  val io = IO(new AXI4DelayerIO)
  io.in <> io.out
  // def r = 10

  // val s_idle :: s_wait_resp :: s_delay :: s_transmit :: Nil = Enum(4)
  // val r_state = RegInit(s_idle)

  // val r_delay_cnt = RegInit(0.U(10.W))
  // val r_delay_target = RegInit(0.U(10.W))

  // r_state := MuxLookup(r_state, s_idle)(
  //   Seq(
  //     s_idle -> Mux(io.in.ar.fire, s_wait_resp, s_idle),
  //     s_wait_resp -> Mux(io.out.r.valid, s_delay, s_wait_resp),
  //     s_delay -> Mux(r_delay_cnt === r_delay_target, s_transmit, s_delay),
  //     s_transmit -> Mux(io.out.ar.fire, s_idle, s_transmit)
  //   )
  // )

  // when(r_state === s_wait_resp || r_state === s_delay) {
  //   r_delay_cnt := r_delay_cnt + 1.U
  // }.elsewhen(r_state === s_transmit){
  //   r_delay_cnt := 0.U
  // }

  // when(r_state === s_wait_resp) {
  //   r_delay_target := r_delay_target * r.U
  // }.elsewhen(r_state === s_transmit){
  //   r_delay_target := 0.U
  // }

  // when(r_state === s_transmit){
  //   io.out.r <> io.in.r
  // }.otherwise{
  //   io.out.r.valid := false.B
  //   io.out.r.bits := DontCare

  //   io.in.r.ready := false.B
  // }

  // io.in.ar <> io.out.ar
}

class AXI4DelayerWrapper(implicit p: Parameters) extends LazyModule {
  val node = AXI4IdentityNode()

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val delayer = Module(new AXI4DelayerChisel)
      delayer.io.clock := clock
      delayer.io.reset := reset
      delayer.io.in <> in
      out <> delayer.io.out
    }
  }
}

object AXI4Delayer {
  def apply()(implicit p: Parameters): AXI4Node = {
    val axi4delay = LazyModule(new AXI4DelayerWrapper)
    axi4delay.node
  }
}
