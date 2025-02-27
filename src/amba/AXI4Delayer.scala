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

  def r = 985 //Mhz
  val s_count :: s_wait :: Nil = Enum(2)

  val state_r = RegInit(s_wait)
  val delay_cnt_read = RegInit(0.U(16.W))
  val exact_delay_cnt_read = RegInit(0.U(7.W))
  val delay_target_read = RegInit(0.U(16.W))

  val state_w = RegInit(s_wait)
  val delay_cnt_write = RegInit(0.U(16.W))
  val exact_delay_cnt_write = RegInit(0.U(7.W))
  val delay_target_write = RegInit(0.U(16.W))

  state_r := MuxLookup(state_r, s_wait)(Seq(
    s_count -> Mux(io.out.r.valid, s_wait, s_count),
    s_wait -> Mux(delay_cnt_read === delay_target_read, s_count, s_wait)
  ))

  state_w := MuxLookup(state_w, s_wait)(Seq(
    s_count -> Mux(io.out.b.valid, s_wait, s_count),
    s_wait -> Mux(delay_cnt_write === delay_target_write, s_count, s_wait)
  ))

  when(io.in.ar.fire) {
    delay_cnt_read := 0.U
    delay_target_read := 0.U
  }

  when(state_r === s_count) {
    exact_delay_cnt_read := Mux(exact_delay_cnt_read === 99.U, 0.U, exact_delay_cnt_read + 1.U)
    delay_cnt_read := delay_cnt_read + 1.U
    delay_target_read := delay_target_read + Mux(exact_delay_cnt_read === (r % 100 - 1).U, (r / 100).U, ((r / 100) + 1).U)
  }.elsewhen(state_r === s_wait){
    delay_cnt_read := delay_cnt_read + 1.U
  }

  when(RegNext(state_w) === s_wait && state_w === s_count) {
    delay_cnt_write := 0.U
    delay_target_write := 0.U
  }

  when(state_w === s_count) {
    exact_delay_cnt_write := Mux(exact_delay_cnt_write === 99.U, 0.U, exact_delay_cnt_write + 1.U)
    delay_cnt_write := delay_cnt_write + 1.U
    delay_target_write := delay_target_write + Mux(exact_delay_cnt_write === (r % 100 - 1).U, (r / 100).U, ((r / 100) + 1).U)
  }.elsewhen(state_w === s_wait){
    delay_cnt_write := delay_cnt_write + 1.U
  }

  when(state_r === s_wait){
    io.in.r.valid := false.B
    io.out.r.ready := false.B
  }

  when(state_w === s_wait){
    io.in.b.valid := false.B
    io.out.b.ready := false.B
  }

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
