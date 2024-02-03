// TODO
#include <verilated.h>
// Include model header, generated from Verilating "top.v"
#include "VTop.h"
#include <iostream>
#include "verilated_vcd_c.h"
VerilatedVcdC *vcd = NULL;
VerilatedContext *contextp = new VerilatedContext;
VTop *top = NULL;



using namespace std;
void single_cycle(int cycle)
{
    top->clock = 0;
    top->eval();
    vcd->dump(cycle);

    // top->

    // cout << "this" <<endl;
    top->clock = 1;
    top->eval();
}
void init()
{
    contextp->debug(0);
    contextp->traceEverOn(true);
    top = new VTop{contextp};
    vcd = new VerilatedVcdC;


    top->trace(vcd, 00);
    vcd->open("/home/oslab/work/chisel/newcore/build/vcd.vcd");
}
int main(int argc, char **argv)
{

    contextp->commandArgs(argc, argv);
    init();


    int counter = 0;
    while (!contextp->gotFinish() && (counter <= 5000))
    {
        // Evaluate model
        single_cycle(counter);
        counter++;
    }
    // Final model cleanup
    top->final();
    vcd->close();
    // Destroy model
    delete top;
    // Return good completion status
    return 0;
}
