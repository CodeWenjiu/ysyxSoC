package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class GPIOIO extends Bundle {
  val out = Output(UInt(16.W))
  val in = Input(UInt(16.W))
  val seg = Output(Vec(8, UInt(8.W)))
}

class GPIOCtrlIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Reset())
  val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
  val gpio = new GPIOIO
}

class gpio_top_apb extends BlackBox {
  val io = IO(new GPIOCtrlIO)
}

object seg_val {
  def num_0 = 0x11.U(8.W)
  def num_1 = 0x9F.U(8.W)
  def num_2 = 0x25.U(8.W)
  def num_3 = 0x0D.U(8.W)
  def num_4 = 0x99.U(8.W)
  def num_5 = 0x49.U(8.W)
  def num_6 = 0x41.U(8.W)
  def num_7 = 0x1f.U(8.W)
  def num_8 = 0x01.U(8.W)
  def num_9 = 0x09.U(8.W)
  def num_A = 0x03.U(8.W)
  def num_B = 0xc1.U(8.W)
  def num_C = 0x62.U(8.W)
  def num_D = 0x85.U(8.W)
  def num_E = 0x61.U(8.W)
  def num_F = 0x71.U(8.W)
}

class gpioChisel extends Module {
  val seg_default = List(seg_val.num_0)

  val seg_map = Array(
    BitPat("b0000")   -> List(seg_val.num_0),
    BitPat("b0001")   -> List(seg_val.num_1),
    BitPat("b0010")   -> List(seg_val.num_2),
    BitPat("b0011")   -> List(seg_val.num_3),
    BitPat("b0100")   -> List(seg_val.num_4),
    BitPat("b0101")   -> List(seg_val.num_5),
    BitPat("b0110")   -> List(seg_val.num_6),
    BitPat("b0111")   -> List(seg_val.num_7),
    BitPat("b1000")   -> List(seg_val.num_8),
    BitPat("b1001")   -> List(seg_val.num_9),
    BitPat("b1010")   -> List(seg_val.num_A),
    BitPat("b1011")   -> List(seg_val.num_B),
    BitPat("b1100")   -> List(seg_val.num_C),
    BitPat("b1101")   -> List(seg_val.num_D),
    BitPat("b1110")   -> List(seg_val.num_E),
    BitPat("b1111")   -> List(seg_val.num_F)
  )

  val io = IO(new GPIOCtrlIO)

  val s_idle :: s_read :: s_write :: s_delay :: Nil = Enum(4)

  val state = withClockAndReset(io.clock, io.reset)(RegInit(s_idle))
  val led_reg = withClockAndReset(io.clock, io.reset)(RegInit(0.U(16.W)))
  val btn_reg = withClockAndReset(io.clock, io.reset)(RegInit(0.U(16.W)))
  val seg_reg = withClockAndReset(io.clock, io.reset)(RegInit(0.U(32.W)))

  io.gpio.out := led_reg
  io.in.prdata := btn_reg

  io.gpio.seg(7) := ListLookup(seg_reg(3, 0),   seg_default, seg_map)(0)
  io.gpio.seg(6) := ListLookup(seg_reg(7, 4),   seg_default, seg_map)(0)
  io.gpio.seg(5) := ListLookup(seg_reg(11, 8),  seg_default, seg_map)(0)
  io.gpio.seg(4) := ListLookup(seg_reg(15, 12), seg_default, seg_map)(0)
  io.gpio.seg(3) := ListLookup(seg_reg(19, 16), seg_default, seg_map)(0)
  io.gpio.seg(2) := ListLookup(seg_reg(23, 20), seg_default, seg_map)(0)
  io.gpio.seg(1) := ListLookup(seg_reg(27, 24), seg_default, seg_map)(0)
  io.gpio.seg(0) := ListLookup(seg_reg(31, 28), seg_default, seg_map)(0)

  io.in.pslverr := false.B

  state := MuxLookup(state, s_idle)(Seq(
    s_idle -> Mux(io.in.psel, s_delay, s_idle),
    s_delay -> Mux(io.in.penable, Mux(io.in.pwrite, s_write, s_read), s_delay),
    s_read -> Mux(io.in.penable, s_idle, s_read),
    s_write -> Mux(io.in.penable, s_idle, s_write)
  ))

  io.in.pready := (state === s_idle)

  when(state === s_write & io.in.paddr(3, 0) === 0.U){
    led_reg := MuxLookup(io.in.pstrb, 0.U(16.W))(Seq(
      1.U -> Cat(led_reg(15, 8), io.in.pwdata(7, 0)),
      3.U -> io.in.pwdata
    ))
  }

  when(state === s_write & io.in.paddr(3, 0) === 8.U){
    seg_reg := MuxLookup(io.in.pstrb, 0.U(32.W))(Seq(
      1.U -> Cat(seg_reg(31, 8), io.in.pwdata(7, 0)),
      3.U -> Cat(seg_reg(23, 16), io.in.pwdata(15, 0)),
      15.U -> io.in.pwdata
    ))
  }

  when((state === s_read) & (io.in.paddr(3, 0) === 4.U)){
    btn_reg := Cat(0.U(16.W), io.gpio.in)
  }
}

class APBGPIO(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
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
    val gpio_bundle = IO(new GPIOIO)

    val mgpio = Module(new gpioChisel)
    mgpio.io.clock := clock
    mgpio.io.reset := reset
    mgpio.io.in <> in
    gpio_bundle <> mgpio.io.gpio
  }
}
