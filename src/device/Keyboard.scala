package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class PS2IO extends Bundle {
  val clk = Input(Bool())
  val data = Input(Bool())
}

class PS2CtrlIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
  val ps2 = new PS2IO
}

class ps2_top_apb extends BlackBox {
  val io = IO(new PS2CtrlIO)
}

class ps2Chisel extends Module {
  val io = IO(new PS2CtrlIO)

  val ps2_clock = Wire(Clock())
  ps2_clock := (!io.ps2.clk).asClock

  val ps2_s_idle :: ps2_s_read :: Nil = Enum(2)
  val ps2_state = withClockAndReset(ps2_clock, io.reset)(RegInit(ps2_s_idle))
  val cache = withClockAndReset(ps2_clock, io.reset)(RegInit(0.U(9.W)))
  val data = withClockAndReset(ps2_clock, io.reset)(RegInit(0.U(16.W)))
  val counter = withClockAndReset(ps2_clock, io.reset)(RegInit(0.U(4.W)))

  ps2_state := MuxLookup(ps2_state, ps2_s_idle)(Seq(
    ps2_s_idle -> ps2_s_read,
    ps2_s_read -> Mux(counter === 9.U, ps2_s_idle, ps2_s_read)
  ))

  when(ps2_state === ps2_s_read){
    cache := Cat(io.ps2.data, cache(8, 1))
    when(counter === 9.U){
      data := Cat(data(7, 0), cache(7, 0))
    }.otherwise{
      counter := counter + 1.U
    }
  }.elsewhen(ps2_state === ps2_s_idle){
    counter := 0.U
  }

  val s_idle :: s_read :: s_write :: s_delay :: Nil = Enum(4)
  val state = withClockAndReset(io.clock, io.reset)(RegInit(s_idle))

  io.in.pslverr := false.B

  state := MuxLookup(state, s_idle)(Seq(
    s_idle -> Mux(io.in.psel, s_delay, s_idle),
    s_delay -> Mux(io.in.penable, Mux(io.in.pwrite, s_write, s_read), s_delay),
    s_read -> Mux(io.in.penable, s_idle, s_read),
    s_write -> Mux(io.in.penable, s_idle, s_write)
  ))

  io.in.pready := (state === s_idle)

  io.in.prdata := data
}

class APBKeyboard(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
  val node = APBSlaveNode(Seq(APBSlavePortParameters(
    Seq(APBSlaveParameters(
      address       = address,
      executable    = true,
      supportsRead  = true,
      supportsWrite = true)),
    beatBytes  = 4)))

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val (in, _) = node.in(0)
    val ps2_bundle = IO(new PS2IO)

    val mps2 = Module(new ps2Chisel)
    mps2.io.clock := clock
    mps2.io.reset := reset
    mps2.io.in <> in
    ps2_bundle <> mps2.io.ps2
  }
}
