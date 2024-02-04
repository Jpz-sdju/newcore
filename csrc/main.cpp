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
void init_verilator()
{
    contextp->debug(0);
    contextp->traceEverOn(true);
    top = new VTop{contextp};
    vcd = new VerilatedVcdC;

    top->trace(vcd, 00);
    vcd->open("vcd.vcd");
}

int init_ram(char* path) {
    std::ifstream inputFile(path, std::ios::binary);

    if (!inputFile.is_open()) {
        std::cerr << "Failed to open the file!" << std::endl;
        return 1;
    }

    // 获取文件大小
    inputFile.seekg(0, std::ios::end);
    std::streampos fileSize = inputFile.tellg();
    inputFile.seekg(0, std::ios::beg);

    // 计算需要多少 uint64_t 来存储数据
    size_t numUint64 = fileSize / sizeof(uint64_t);

    // 读取文件内容到缓冲区
    inputFile.read(reinterpret_cast<char*>(ram), fileSize);

    // 关闭文件
    inputFile.close();

    // 处理读取到的 uint64_t 数据
    for (size_t i = 0; i < 50; ++i) {
        std::cout << "uint64_t value at index " << i << ": 0x"<<hex << setw(16) <<setfill('0')  << ram[i] << std::endl;
    }

    return 0;
}
int main(int argc, char **argv)
{
    // cout << argc << argv[0] <<argv[1] << endl;
    contextp->commandArgs(argc, argv);
    init_verilator();
    init_ram(argv[1]);
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

    // cout << ram[0]<< endl;
    return 0;
}
