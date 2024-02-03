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

extern "C" uint64_t ram_read_helper(uint8_t en, uint64_t rIdx) {
//   if (!ram)
//     return 0;
//   if (en && rIdx >= EMU_RAM_SIZE / sizeof(uint64_t)) {
//     rIdx %= EMU_RAM_SIZE / sizeof(uint64_t);
//   }
// //   pthread_mutex_lock(&ram_mutex);
//   uint64_t rdata = (en) ? ram[rIdx] : 0;
// //   pthread_mutex_unlock(&ram_mutex);
//   return rdata;
}

extern "C" void ram_write_helper(uint64_t wIdx, uint64_t wdata, uint64_t wmask, uint8_t wen) {
//   if (wen && ram) {
//     if (wIdx >= EMU_RAM_SIZE / sizeof(uint64_t)) {
//       printf("ERROR: ram wIdx = 0x%lx out of bound!\n", wIdx);
//       assert(wIdx < EMU_RAM_SIZE / sizeof(uint64_t));
//     }
//     // pthread_mutex_lock(&ram_mutex);
//     ram[wIdx] = (ram[wIdx] & ~wmask) | (wdata & wmask);
//     // pthread_mutex_unlock(&ram_mutex);
//   }
}
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
    vcd->open("vcd.vcd");
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
