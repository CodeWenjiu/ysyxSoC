module top(	// src/SoC.scala:113:9
        input         clock,	// src/SoC.scala:113:9
        reset,	// src/SoC.scala:113:9
        output [15:0] externalPins_gpio_out,	// src/SoC.scala:150:26
        input  [15:0] externalPins_gpio_in,	// src/SoC.scala:150:26
        output [7:0]  externalPins_gpio_seg_0,	// src/SoC.scala:150:26
        externalPins_gpio_seg_1,	// src/SoC.scala:150:26
        externalPins_gpio_seg_2,	// src/SoC.scala:150:26
        externalPins_gpio_seg_3,	// src/SoC.scala:150:26
        externalPins_gpio_seg_4,	// src/SoC.scala:150:26
        externalPins_gpio_seg_5,	// src/SoC.scala:150:26
        externalPins_gpio_seg_6,	// src/SoC.scala:150:26
        externalPins_gpio_seg_7,	// src/SoC.scala:150:26
        input         externalPins_ps2_clk,	// src/SoC.scala:150:26
        externalPins_ps2_data,	// src/SoC.scala:150:26
        output [7:0]  externalPins_vga_r,	// src/SoC.scala:150:26
        externalPins_vga_g,	// src/SoC.scala:150:26
        externalPins_vga_b,	// src/SoC.scala:150:26
        output        externalPins_vga_sync_hsync,	// src/SoC.scala:150:26
        externalPins_vga_sync_vsync,	// src/SoC.scala:150:26
        externalPins_vga_sync_valid,	// src/SoC.scala:150:26
        input         externalPins_uart_rx,	// src/SoC.scala:150:26
        output        externalPins_uart_tx	// src/SoC.scala:150:26
    );

    ysyxSoCFull soc (
                    .clock	                        (clock),
                    .reset	                        (reset),
                    .externalPins_gpio_out	        (externalPins_gpio_out),
                    .externalPins_gpio_in	        (externalPins_gpio_in),
                    .externalPins_gpio_seg_0	    (externalPins_gpio_seg_0),
                    .externalPins_gpio_seg_1	    (externalPins_gpio_seg_1),
                    .externalPins_gpio_seg_2	    (externalPins_gpio_seg_2),
                    .externalPins_gpio_seg_3	    (externalPins_gpio_seg_3),
                    .externalPins_gpio_seg_4	    (externalPins_gpio_seg_4),
                    .externalPins_gpio_seg_5	    (externalPins_gpio_seg_5),
                    .externalPins_gpio_seg_6	    (externalPins_gpio_seg_6),
                    .externalPins_gpio_seg_7	    (externalPins_gpio_seg_7),
                    .externalPins_ps2_clk	    (externalPins_ps2_clk),
                    .externalPins_ps2_data	    (externalPins_ps2_data),
                    .externalPins_vga_r	        (externalPins_vga_r),
                    .externalPins_vga_g	        (externalPins_vga_g),
                    .externalPins_vga_b	        (externalPins_vga_b),
                    .externalPins_vga_sync_hsync	(externalPins_vga_sync_hsync),
                    .externalPins_vga_sync_vsync	(externalPins_vga_sync_vsync),
                    .externalPins_vga_sync_valid	(externalPins_vga_sync_valid),
                    .externalPins_uart_rx	    (externalPins_uart_rx),
                    .externalPins_uart_tx	    (externalPins_uart_tx)
                );

endmodule
