package ysyx

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog

import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class SDRAMIO extends Bundle {
  val clk = Output(Bool())
  val cke = Output(Bool())
  val cs  = Output(Bool())
  val ras = Output(Bool())
  val cas = Output(Bool())
  val we  = Output(Bool())
  val a   = Output(UInt(13.W))
  val ba  = Output(UInt(2.W))
  val dqm = Output(UInt(4.W))
  val dq  = Analog(32.W)
}

class sdram_top_axi extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val in = Flipped(new AXI4Bundle(AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4)))
    val sdram = new SDRAMIO
  })
}

class sdram_top_apb extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
    val sdram = new SDRAMIO
  })
}

class sdram extends BlackBox {
  val io = IO(Flipped(new SDRAMIO))
}

class sdram_healper extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle{
    val clk = Input(Bool())
    val wraddr = Input(UInt(25.W))
    val ren = Input(Bool())
    val wen = Input(Bool())
    val rdata = Output(UInt(16.W))
    val wdata = Input(UInt(16.W))
    val wlen = Input(UInt(2.W))
  })
  setInline("sdram_healper.v",
  """module sdram_healper(
    |  input  clk,
    |  input  [24:0] wraddr,
    |  input  ren,
    |  input  wen,
    |  output reg [15:0] rdata,
    |  input  [15:0] wdata,
    |  input  [1:0] wlen
    |);
    |
    |import "DPI-C" function void sdram_read(input int raddr, output int rdata);
    |import "DPI-C" function void sdram_write(input int waddr, input int wdata, input int wlen);
    |
    |always @(posedge clk) begin
    |  if(ren) begin
    |    sdram_read({7'h00, wraddr}, {16'h0000, rdata});
    |  end
    |end
    |
    |always @(negedge clk) begin
    |  if(wen) begin
    |    sdram_write({7'h00, wraddr}, {16'h0000, wdata}, {30'h00000000, wlen});
    |  end
    |end
    |
    |endmodule
  """.stripMargin)
}

class sdramChisel extends RawModule {
  val io = IO(Flipped(new SDRAMIO))

  val s_idle :: s_burst_read :: s_burst_write :: Nil = Enum(3)

  val den = Wire(Bool())
  val dout = Wire(UInt(32.W))
  val din = TriStateInBuf(io.dq, dout, den)

  val clock = io.clk.asClock
  val reset = (!io.cke).asAsyncReset

  val command = Cat(io.cs, io.ras, io.cas, io.we)
  
  val state = withClockAndReset(clock, reset)(RegInit(s_idle))

  val raw_address = withClockAndReset(clock, reset)(RegInit(VecInit(Seq.fill(4)(0.U(13.W)))))
  val column_address = withClockAndReset(clock, reset)(RegInit(0.U(9.W)))
  val rawaddr_cache = withClockAndReset(clock, reset)(RegInit(0.U(15.W)))

  when(command === "b0011".U){
    raw_address(io.ba) := io.a
  }.elsewhen(command === "b0101".U){
    column_address := io.a(8, 0)
    rawaddr_cache := Cat(raw_address(io.ba), io.ba)
  }.elsewhen(command === "b0100".U){
    column_address := io.a(8, 0)
    rawaddr_cache := Cat(raw_address(io.ba), io.ba)
  }

  state := MuxLookup(state, s_idle)(Seq(
    s_idle -> MuxLookup(command, s_idle)(Seq(
      "b0101".U -> s_burst_read,
      "b0100".U -> s_burst_write
    )),
    s_burst_read -> s_idle,
    s_burst_write -> s_idle
  ))

  val healper1 = Module(new sdram_healper)
  val healper2 = Module(new sdram_healper)
  val healper3 = Module(new sdram_healper)
  val healper4 = Module(new sdram_healper)
  
  val ren = Wire(Bool())
  val ren_delay = withClockAndReset(clock, reset)(RegInit(0.U(1.W)))

  ren := (state === s_burst_read)
  ren_delay := ren

  healper1.io.clk := io.clk
  healper2.io.clk := io.clk
  healper3.io.clk := io.clk
  healper4.io.clk := io.clk

  when(io.dqm(1, 0) === "b01".U){
    healper1.io.wraddr := Cat(false.B, rawaddr_cache(13, 0), column_address, true.B)
  }.otherwise{
    healper1.io.wraddr := Cat(false.B, rawaddr_cache(13, 0), column_address, false.B)
  }

  when(io.dqm(3, 2) === "b01".U){
    healper2.io.wraddr := Cat(false.B, rawaddr_cache(13, 0), column_address, true.B) + 2.U // + 2 is not need for real hardware but benefit for simulation
  }.otherwise{
    healper2.io.wraddr := Cat(false.B, rawaddr_cache(13, 0), column_address, false.B) + 2.U
  }

  when(io.dqm(1, 0) === "b01".U){
    healper3.io.wraddr := Cat(true.B, rawaddr_cache(13, 0), column_address, true.B)
  }.otherwise{
    healper3.io.wraddr := Cat(true.B, rawaddr_cache(13, 0), column_address, false.B)
  }

  when(io.dqm(3, 2) === "b01".U){
    healper4.io.wraddr := Cat(true.B, rawaddr_cache(13, 0), column_address, true.B) + 2.U
  }.otherwise{
    healper4.io.wraddr := Cat(true.B, rawaddr_cache(13, 0), column_address, false.B) + 2.U
  }
  
  healper1.io.ren := ren & !rawaddr_cache(14)
  healper2.io.ren := ren & !rawaddr_cache(14)
  healper3.io.ren := ren &  rawaddr_cache(14)
  healper4.io.ren := ren &  rawaddr_cache(14)

  healper1.io.wen := (state === s_burst_write) & (io.dqm(1, 0) =/= "b11".U) & !rawaddr_cache(14)
  healper2.io.wen := (state === s_burst_write) & (io.dqm(3, 2) =/= "b11".U) & !rawaddr_cache(14)
  healper3.io.wen := (state === s_burst_write) & (io.dqm(1, 0) =/= "b11".U) &  rawaddr_cache(14)
  healper4.io.wen := (state === s_burst_write) & (io.dqm(3, 2) =/= "b11".U) &  rawaddr_cache(14)

  when(io.dqm(1, 0) === "b01".U){
    healper1.io.wdata := din >> 8.U
  }.otherwise{
    healper1.io.wdata := din
  }
  when(io.dqm(1, 0) === "b00".U){
    healper1.io.wlen := 2.U
  }.otherwise{
    healper1.io.wlen := 1.U
  }

  when(io.dqm(3, 2) === "b01".U){
    healper2.io.wdata := din >> 24.U
  }.otherwise{
    healper2.io.wdata := din >> 16.U
  }
  when(io.dqm(3, 2) === "b00".U){
    healper2.io.wlen := 2.U
  }.otherwise{
    healper2.io.wlen := 1.U
  }

  when(io.dqm(1, 0) === "b01".U){
    healper3.io.wdata := din >> 8.U
  }.otherwise{
    healper3.io.wdata := din
  }
  when(io.dqm(1, 0) === "b00".U){
    healper3.io.wlen := 2.U
  }.otherwise{
    healper3.io.wlen := 1.U
  }

  when(io.dqm(3, 2) === "b01".U){
    healper4.io.wdata := din >> 24.U
  }.otherwise{
    healper4.io.wdata := din >> 16.U
  }
  when(io.dqm(3, 2) === "b00".U){
    healper4.io.wlen := 2.U
  }.otherwise{
    healper4.io.wlen := 1.U
  }

  den := ren_delay
  dout := Mux(rawaddr_cache(14), Cat(healper4.io.rdata, healper3.io.rdata), Cat(healper2.io.rdata, healper1.io.rdata))
}

class AXI4SDRAM(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
  val beatBytes = 4
  val node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
        address       = address,
        executable    = true,
        supportsWrite = TransferSizes(1, beatBytes),
        supportsRead  = TransferSizes(1, beatBytes),
        interleavedId = Some(0))
    ),
    beatBytes  = beatBytes)))

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val (in, _) = node.in(0)
    val sdram_bundle = IO(new SDRAMIO)

    val msdram = Module(new sdram_top_axi)
    msdram.io.clock := clock
    msdram.io.reset := reset.asBool
    msdram.io.in <> in
    sdram_bundle <> msdram.io.sdram
  }
}

class APBSDRAM(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
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
    val sdram_bundle = IO(new SDRAMIO)

    val msdram = Module(new sdram_top_apb)
    msdram.io.clock := clock
    msdram.io.reset := reset.asBool
    msdram.io.in <> in
    sdram_bundle <> msdram.io.sdram
  }
}
