package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class VGAIO extends Bundle {
  val r = Output(UInt(8.W))
  val g = Output(UInt(8.W))
  val b = Output(UInt(8.W))
  val hsync = Output(Bool())
  val vsync = Output(Bool())
  val valid = Output(Bool())
}

class VGACtrlIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
  val vga = new VGAIO
}

class vga_top_apb extends BlackBox {
  val io = IO(new VGACtrlIO)
}

class VGAHelper extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle{
    val x_addr = Input(UInt(10.W))
    val y_addr = Input(UInt(10.W))
    val w_addr = Input(UInt(32.W))
    val ren = Input(Bool())
    val wen = Input(Bool())
    val rdata = Output(UInt(32.W))
    val wdata = Input(UInt(32.W))
  })
  setInline("VGAHelper.v",
    """module VGAHelper(
      | input  [9:0] x_addr,
      | input  [9:0] y_addr,
      | input  [31:0] w_addr,
      | input        ren,
      | input        wen,
      | output reg [31:0] rdata,
      | input  [31:0] wdata
      |);
      |import "DPI-C" function void vga_read(input int x_addr, input int y_addr, output int rdata);
      |import "DPI-C" function void vga_write(input int w_addr, input int wdata);
      |always @(*) begin
      | if (ren) vga_read({22'b0, x_addr}, {22'b0, y_addr}, rdata);
      | else rdata = 0;
      | if (wen) vga_write(w_addr, wdata);
      |end
      |endmodule
    """.stripMargin)
}

class vgaChisel extends Module {
  val io = IO(new VGACtrlIO)

  def h_front_porch = 96.U
  def h_active = 144.U
  def h_backporch = 784.U
  def h_total = 800.U

  def v_front_porch = 2.U
  def v_active = 35.U
  def v_backporch = 515.U
  def v_total = 525.U

  val x_cnt = RegInit(1.U(10.W))
  val y_cnt = RegInit(1.U(10.W))

  when(x_cnt >= h_total){
    x_cnt := 1.U
  }.otherwise{
    x_cnt := x_cnt + 1.U
  }

  when(y_cnt >= v_total){
    y_cnt := 1.U
  }.elsewhen(x_cnt >= h_total){
    y_cnt := y_cnt + 1.U
  }

  io.vga.hsync := (x_cnt > h_front_porch)
  io.vga.vsync := (y_cnt > v_front_porch)

  val h_valid = Wire(Bool())
  val v_valid = Wire(Bool())
  h_valid := (x_cnt > h_active) & (x_cnt <= h_backporch)
  v_valid := (y_cnt > v_active) & (y_cnt <= v_backporch)
  io.vga.valid := h_valid & v_valid

  val vga_helper = Module(new VGAHelper)
  vga_helper.io.x_addr := Mux(h_valid, x_cnt - h_active - 1.U, 0.U)
  vga_helper.io.y_addr := Mux(v_valid, y_cnt - v_active - 1.U, 0.U)
  vga_helper.io.ren := h_valid & v_valid

  io.vga.r := vga_helper.io.rdata(23, 16)
  io.vga.g := vga_helper.io.rdata(15, 8)
  io.vga.b := vga_helper.io.rdata(7, 0)

  val s_idle :: s_read :: s_write :: s_delay :: Nil = Enum(4)
  val state = RegInit(s_idle)

  io.in.pslverr := false.B

  state := MuxLookup(state, s_idle)(Seq(
    s_idle -> Mux(io.in.psel, s_delay, s_idle),
    s_delay -> Mux(io.in.penable, Mux(io.in.pwrite, s_write, s_read), s_delay),
    s_read -> Mux(io.in.penable, s_idle, s_read),
    s_write -> Mux(io.in.penable, s_idle, s_write)
  ))

  io.in.pready := (state === s_idle)

  vga_helper.io.wen := (state === s_write)
  vga_helper.io.w_addr := io.in.paddr
  vga_helper.io.wdata := io.in.pwdata
  io.in.prdata := 0.U
}

class APBVGA(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
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
    val vga_bundle = IO(new VGAIO)

    val mvga = Module(new vgaChisel)
    mvga.io.clock := clock
    mvga.io.reset := reset
    mvga.io.in <> in
    vga_bundle <> mvga.io.vga
  }
}
