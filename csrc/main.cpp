// TODO
#include <verilated.h>
// Include model header, generated from Verilating "top.v"
#include "VTop.h"
#include <iostream>
#include <fstream>
#include <iomanip>
#include "verilated_vcd_c.h"
VerilatedVcdC *vcd = NULL;
VerilatedContext *contextp = new VerilatedContext;
VTop *top = NULL;
using namespace std;
#define EMU_RAM_SIZE (256 * 1024 * 1024UL) // 256 MB
uint64_t ram[1024 * 1024];

int dump_counter = 0;
extern "C" uint64_t ram_read_helper(uint8_t en, uint64_t rIdx)
{
    if (!ram)
        return 0;
    if (en && rIdx >= EMU_RAM_SIZE / sizeof(uint64_t))
    {
        rIdx %= EMU_RAM_SIZE / sizeof(uint64_t);
    }
    //   pthread_mutex_lock(&ram_mutex);
    uint64_t rdata = (en) ? ram[rIdx] : 0;
    //   pthread_mutex_unlock(&ram_mutex);
    return rdata;
}

extern "C" void ram_write_helper(uint64_t wIdx, uint64_t wdata, uint64_t wmask, uint8_t wen)
{
    if (wen && ram)
    {
        if (wIdx >= EMU_RAM_SIZE / sizeof(uint64_t))
        {
            printf("ERROR: ram wIdx = 0x%lx out of bound!\n", wIdx);
            assert(wIdx < EMU_RAM_SIZE / sizeof(uint64_t));
        }
        // pthread_mutex_lock(&ram_mutex);
        ram[wIdx] = (ram[wIdx] & ~wmask) | (wdata & wmask);
        // pthread_mutex_unlock(&ram_mutex);
    }
}
void single_cycle()
{
    top->clock = 0;
    top->eval();

    vcd->dump(dump_counter);

    top->clock = 1;
    top->eval();
    dump_counter++;

}
void init_verilator()
{
    contextp->debug(0);
    contextp->traceEverOn(true);
    top = new VTop{contextp};
    vcd = new VerilatedVcdC;

    top->trace(vcd, 00);
    vcd->open("vcd.vcd");
}
void soc_reset()
{
    top->reset = 1;
    int reset_period = 16;
    while (reset_period > 0)
    {
        single_cycle();
        reset_period --;
    }
    top->reset = 0;
}
int init_ram(char *path)
{
    std::ifstream inputFile(path, std::ios::binary);

    if (!inputFile.is_open())
    {
        std::cerr << "Failed to open the file!" << std::endl;
        return 1;
    }

    inputFile.seekg(0, std::ios::end);
    std::streampos fileSize = inputFile.tellg();
    inputFile.seekg(0, std::ios::beg);

    size_t numUint64 = fileSize / sizeof(uint64_t);

    inputFile.read(reinterpret_cast<char *>(ram), fileSize);

    inputFile.close();

    // for (size_t i = 0; i < 50; ++i)
    // {
    //     std::cout << "uint64_t value at index " << i << ": 0x" << hex << setw(16) << setfill('0') << ram[i] << std::endl;
    // }

    return 0;
}
int main(int argc, char **argv)
{
    // cout << argc << argv[0] <<argv[1] << endl;
    contextp->commandArgs(argc, argv);
    init_verilator();
    init_ram(argv[1]);

    soc_reset();
    while (!contextp->gotFinish() && (dump_counter <= 5000))
    {
        // Evaluate model
        single_cycle();
    }
    // Final model cleanup
    top->final();
    vcd->close();
    // Destroy model
    delete top;

    // cout << ram[0]<< endl;
    return 0;
}
