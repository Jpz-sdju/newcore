module dff #(
    parameter WIDTH=1
) (
    input [WIDTH-1:0] din,
    input clk,
    input rst_l,
    
    output [WIDTH-1:0] dout
);
    always @(posedge clk or negedge rst_l) begin
        if (~rst_l) begin
            dout <= WIDTH'b0;
        end
        else begin
            dout <= din;
        end
    end
endmodule