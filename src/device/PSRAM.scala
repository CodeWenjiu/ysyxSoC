package ysyx

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class QSPIIO extends Bundle {
  val sck = Output(Bool())
  val ce_n = Output(Bool())
  val dio = Analog(4.W)
}

class psram_top_apb extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
    val qspi = new QSPIIO
  })
}

class psram extends BlackBox {
  val io = IO(Flipped(new QSPIIO))
}

class psram_healper extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle{
    val wraddr = Input(UInt(24.W))
    val ren = Input(Bool())
    val wen = Input(Bool())
    val rdata = Output(UInt(32.W))
    val wdata = Input(UInt(32.W))
  })
  setInline("psram_healper.v",
  """module psram_healper(
    |  input  [23:0] wraddr,
    |  input  ren,
    |  input  wen,
    |  input  [31:0] wdata,
    |  output reg [31:0] rdata
    |);
    |import "DPI-C" function void psram_read(input int raddr, output int rdata);
    |import "DPI-C" function void psram_write(input int waddr, input int wdata);
    |
    |  always_latch begin
    |    if (wen) psram_write({8'h00, wraddr}, wdata);
    |    else if (ren) psram_read({8'h00, wraddr}, rdata);
    |    else rdata = 0;
    |  end
    |
    |endmodule
  """.stripMargin)
}

class psramChisel extends RawModule {
  val io = IO(Flipped(new QSPIIO))

  val den = Wire(Bool())
  val dout = Wire(UInt(4.W))
  val din = TriStateInBuf(io.dio, dout, den)

  val s_idle :: s_read_wait_addr :: s_write_wait_addr :: s_read_delay :: s_read :: s_write :: Nil = Enum(6)

  val clock = io.sck.asClock
  val reset = io.ce_n.asAsyncReset

  val state = withClockAndReset(clock, reset)(RegInit(s_idle))
  val last_time_state = withClockAndReset(clock, reset)(RegInit(s_idle))

  val command_buffer = withClockAndReset(clock, reset)(RegInit(0.U(8.W)))
  val address_buffer = withClockAndReset(clock, reset)(RegInit(0.U(24.W)))
  val data_buffer = withClockAndReset(clock, reset)(RegInit(0.U(32.W)))
  val sck_counter = withClockAndReset(clock, reset)(RegInit(0.U(8.W)))

  val healper = Module(new psram_healper)

  command_buffer := Cat(command_buffer(7, 1), din(0))
  when(state === s_read_wait_addr || state === s_write_wait_addr){
    address_buffer := Cat(address_buffer(23, 4), din(3, 0))
  }
  when(state === s_write || state === s_read){
    data_buffer := Cat(data_buffer(31, 4), din(3, 0))
  }.elsewhen(state === s_read_delay){
    data_buffer := healper.io.rdata
  }
  sck_counter := sck_counter + 1.U

  healper.io.wraddr := address_buffer
  healper.io.wdata  := data_buffer
  healper.io.ren    := state === s_read_delay && last_time_state === s_idle
  healper.io.wen    := state === s_idle && last_time_state === s_write

  den := state === s_read
  dout := data_buffer(31, 28)

  state := MuxLookup(state, s_idle)(Seq(
    s_idle -> Mux(sck_counter === 8.U, Mux(command_buffer =/= "hE8".U(8.W), Mux(command_buffer =/= "h38".U(8.W), state, s_write_wait_addr), s_read_wait_addr), state),
    s_read_wait_addr -> Mux(sck_counter === 14.U, s_read_delay, state),
    s_write_wait_addr -> Mux(sck_counter === 14.U, s_write, state),
    s_read_delay -> Mux(sck_counter === 20.U, s_read, state),
    s_read -> Mux(sck_counter === 28.U, s_idle, state),
    s_write -> Mux(sck_counter === 20.U, s_idle, state)
  ))
  last_time_state := state
}

class APBPSRAM(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
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
    val qspi_bundle = IO(new QSPIIO)

    val mpsram = Module(new psram_top_apb)
    mpsram.io.clock := clock
    mpsram.io.reset := reset
    mpsram.io.in <> in
    qspi_bundle <> mpsram.io.qspi
  }
}
