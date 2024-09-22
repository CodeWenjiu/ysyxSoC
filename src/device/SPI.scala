package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class SPIIO(val ssWidth: Int = 8) extends Bundle {
  val sck = Output(Bool())
  val ss = Output(UInt(ssWidth.W))
  val mosi = Output(Bool())
  val miso = Input(Bool())
}

class spi_top_apb extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
    val spi = new SPIIO
    val spi_irq_out = Output(Bool())
  })
}

class flash extends BlackBox {
  val io = IO(Flipped(new SPIIO(1)))
}

class APBSPI(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
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
    val spi_bundle = IO(new SPIIO)

    val s_idle :: s_send_addr :: s_send_divider :: s_send_ss :: s_send_ctrl :: s_transmit :: s_wait_receive :: s_receive :: Nil = Enum(8)

    val state = RegInit(s_idle)
    
    val mspi = Module(new spi_top_apb)

    state := MuxLookup(state, s_idle)(Seq(
      s_idle          -> Mux((in.psel && in.penable && !in.pwrite && (in.paddr(29, 28) === "b11".U)), s_send_addr, state),
      s_send_addr     -> Mux(mspi.io.in.pready, s_send_divider, state),
      s_send_divider  -> Mux(mspi.io.in.pready, s_send_ss, state),
      s_send_ss       -> Mux(mspi.io.in.pready, s_send_ctrl, state),
      s_send_ctrl     -> Mux(mspi.io.in.pready, s_transmit, state),
      s_transmit      -> Mux(mspi.io.in.pready, s_wait_receive, state),
      s_wait_receive  -> Mux(mspi.io.spi_irq_out, s_receive, state),
      s_receive       -> Mux(mspi.io.in.pready, s_idle, state)
    ))

    when(state === s_idle){
      mspi.io.in <> in
    }.elsewhen(state === s_send_addr){
      mspi.io.in.psel := true.B
      mspi.io.in.penable := true.B
      mspi.io.in.pwrite := true.B
      mspi.io.in.paddr  := "h10001004".U
      mspi.io.in.pprot  := "b001".U
      mspi.io.in.pwdata := Cat("h03".U(8.W), in.paddr(23, 0))
      mspi.io.in.pstrb  := "b1111".U
    }.elsewhen(state === s_send_divider){
      mspi.io.in.psel := true.B
      mspi.io.in.penable := true.B
      mspi.io.in.pwrite := true.B
      mspi.io.in.paddr  := "h10001014".U
      mspi.io.in.pprot  := "b001".U
      mspi.io.in.pwdata := "h00000000".U
      mspi.io.in.pstrb  := "b1111".U
    }.elsewhen(state === s_send_ss){
      mspi.io.in.psel := true.B
      mspi.io.in.penable := true.B
      mspi.io.in.pwrite := true.B
      mspi.io.in.paddr  := "h10001018".U
      mspi.io.in.pprot  := "b001".U
      mspi.io.in.pwdata := "h00000001".U
      mspi.io.in.pstrb  := "b1111".U
    }.elsewhen(state === s_send_ctrl){
      mspi.io.in.psel := true.B
      mspi.io.in.penable := true.B
      mspi.io.in.pwrite := true.B
      mspi.io.in.paddr  := "h10001010".U
      mspi.io.in.pprot  := "b001".U
      mspi.io.in.pwdata := "h00003040".U
      mspi.io.in.pstrb  := "b1111".U
    }.elsewhen(state === s_transmit){
      mspi.io.in.psel := true.B
      mspi.io.in.penable := true.B
      mspi.io.in.pwrite := true.B
      mspi.io.in.paddr  := "h10001010".U
      mspi.io.in.pprot  := "b001".U
      mspi.io.in.pwdata := "h00003140".U
      mspi.io.in.pstrb  := "b1111".U
    }.elsewhen(state === s_wait_receive){
      mspi.io.in.psel := true.B
      mspi.io.in.penable := true.B
      mspi.io.in.pwrite := false.B
      mspi.io.in.paddr  := "h10001000".U
      mspi.io.in.pprot  := "b001".U
      mspi.io.in.pwdata := "h00000000".U
      mspi.io.in.pstrb  := "b0000".U
    }.otherwise{
      mspi.io.in.psel := true.B
      mspi.io.in.penable := true.B
      mspi.io.in.pwrite := false.B
      mspi.io.in.paddr  := "h10001000".U
      mspi.io.in.pprot  := "b001".U
      mspi.io.in.pwdata := "h00000000".U
      mspi.io.in.pstrb  := "b0000".U
    }

    when(state === s_idle){
      in <> mspi.io.in
    }.elsewhen(state === s_receive){
      in.prdata := mspi.io.in.prdata
      in.pready := true.B
      in.pslverr := false.B
    }.otherwise{
      in.prdata := "h00000000".U
      in.pready := false.B
      in.pslverr := false.B
    }

    mspi.io.clock := clock
    mspi.io.reset := reset
    spi_bundle <> mspi.io.spi
  }
}
