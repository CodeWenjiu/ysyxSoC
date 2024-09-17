package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.axi4._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class CLINT_healper extends BlackBox with HasBlackBoxInline {
    val io = IO(new Bundle {
        val raddr = Input(UInt(32.W))
        val ren = Input(Bool())
        val rdata = Output(UInt(32.W))
    })
    setInline("CLINT_healper.v",
        """module CLINT_healper(
          |    input  wire [31:0] raddr,
          |    input  wire        ren,
          |    output reg [31:0] rdata
          |);
          |import "DPI-C" function void clint_read(input int raddr, output int rdata);
          |always @(*) begin
          |    if(ren) clint_read(raddr, rdata);
          |    else rdata = 0;
          |end
          |endmodule
        """.stripMargin)
}

class AXI4CLINT(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
    val beatBytes = 4
    val node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
        Seq(AXI4SlaveParameters(
            address       = address,
            executable    = true,
            supportsWrite = TransferSizes.none,
            supportsRead  = TransferSizes(1, beatBytes),
            interleavedId = Some(1))
        ),
        beatBytes  = beatBytes)))

    lazy val module = new Impl
    class Impl extends LazyModuleImp(this) {
        val (in, _) = node.in(0)

        val clint = Module(new CLINT_healper)

        val s_idle :: s_wait_ready :: Nil = Enum(2)
        val state = RegInit(s_idle)

        state := MuxLookup(state, s_idle)(Seq(
            s_idle -> Mux(in.ar.fire, s_wait_ready, s_idle),
            s_wait_ready -> Mux(in.r.fire, s_idle, s_wait_ready)
        ))

        clint.io.raddr := in.ar.bits.addr
        clint.io.ren := in.ar.fire
        in.ar.ready := (state === s_idle)

        in.r.bits.data := RegEnable(clint.io.rdata, in.ar.fire)
        in.r.bits.id := RegEnable(in.ar.bits.id, in.ar.fire)
        in.r.bits.resp := 0.U
        in.r.bits.last := true.B
        in.r.valid := (state === s_wait_ready)

        in.aw.ready := false.B
        in. w.ready := false.B
        in. b.valid := false.B
    }
}