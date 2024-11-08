package ysyx

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.amba._
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class APBDelayerIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Reset())
  val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
  val out = new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32))
}

class apb_delayer extends BlackBox {
  val io = IO(new APBDelayerIO)
}

class APBDelayerChisel extends Module {
  val io = IO(new APBDelayerIO)
  
  def r = 985 //Mhz
  val s_idle :: s_wait_resp :: s_delay :: s_transmit :: Nil = Enum(4)
  val state = RegInit(s_idle)

  val delay_cnt = RegInit(0.U(16.W))
  val exact_delay_cnt = RegInit(0.U(7.W))
  val delay_target = RegInit(0.U(16.W))

  state := MuxLookup(state, s_idle)(Seq(
    s_idle -> Mux(io.in.psel && io.in.penable, s_wait_resp, s_idle),
    s_wait_resp -> Mux(io.out.pready, s_delay, s_wait_resp),
    s_delay -> Mux(delay_cnt === delay_target, s_transmit, s_delay),
    s_transmit -> s_idle
  ))

  when(state === s_wait_resp || state === s_delay) {
    delay_cnt := delay_cnt + 1.U
  }.elsewhen(state === s_transmit){
    delay_cnt := 0.U
  }
  
  when(state === s_transmit){
    exact_delay_cnt := Mux(exact_delay_cnt === 99.U, 0.U, exact_delay_cnt + 1.U)
  }

  when(state === s_wait_resp) {
    delay_target := delay_target + Mux(exact_delay_cnt > (r % 100 - 1).U, (r / 100).U, ((r / 100) + 1).U)
  }.elsewhen(state === s_transmit){
    delay_target := 0.U
  }

  val fire = !RegNext(io.out.pready) && io.out.pready

  val pslverr = RegEnable(io.out.pslverr, fire)
  val prdata = RegEnable(io.out.prdata, fire)

  when(state === s_transmit){
    io.in.pready := true.B
    io.in.pslverr := pslverr
    io.in.prdata := prdata
  }.otherwise{
    io.in.pready := false.B
    io.in.pslverr := false.B
    io.in.prdata := 0.U
  }
  when(state === s_idle || state === s_wait_resp){
    io.out.psel <> io.in.psel
    io.out.penable <> io.in.penable
    io.out.pwrite <> io.in.pwrite
    io.out.paddr <> io.in.paddr
    io.out.pprot <> io.in.pprot
    io.out.pwdata <> io.in.pwdata
    io.out.pstrb <> io.in.pstrb
  }.otherwise{
    io.out.psel := false.B
    io.out.penable := false.B
    io.out.pwrite := false.B
    io.out.paddr := 0.U
    io.out.pprot := 0.U
    io.out.pwdata := 0.U
    io.out.pstrb := 0.U
  }
}

class APBDelayerWrapper(implicit p: Parameters) extends LazyModule {
  val node = APBIdentityNode()

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val delayer = Module(new APBDelayerChisel)
      delayer.io.clock := clock
      delayer.io.reset := reset
      delayer.io.in <> in
      out <> delayer.io.out
    }
  }
}

object APBDelayer {
  def apply()(implicit p: Parameters): APBNode = {
    val apbdelay = LazyModule(new APBDelayerWrapper)
    apbdelay.node
  }
}
