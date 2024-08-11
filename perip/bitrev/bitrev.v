module bitrev (
  input  sck,
  input  ss,
  input  mosi,
  output miso
);
  wire reset = ss;

  typedef enum [1:0] { s_receive, s_transmit} state_t;
  reg[1:0] state;
  reg[2:0] counter;

  always@(posedge sck or posedge reset) begin
    if (reset) begin 
      state <= s_receive; 
    end
    else begin
        case (state)
          s_receive:  state <= (counter == 3'd7) ? s_transmit : state;
          s_transmit: state <= (counter == 3'd0) ? s_receive  : state;
          default:    state <= s_receive;
        endcase
    end 
  end

  always@(posedge sck or posedge reset) begin
    if (reset) counter <= 3'd0;
    else begin
      case (state)
        s_receive:  counter <= (counter < 3'd7) ? counter + 3'd1 : counter;
        s_transmit: counter <= (counter > 3'd0) ? counter - 3'd1 : counter;
        default:    counter <= counter + 3'd1;
      endcase
    end
  end 

  reg [7:0] stack;

  always@(posedge sck or posedge reset) begin
    if (reset) stack <= 8'd0;
    else begin
      case (state)
        s_receive:  stack[counter] <= mosi;
        default:    stack[counter] <= stack[counter];
      endcase
    end
  end

  assign miso = ((state == s_receive) || reset) ? 1'b1 : stack[counter];
endmodule
