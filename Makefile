MAKEFILE_PATH := $(abspath $(lastword $(MAKEFILE_LIST)))

MAKEFILE_DIR := $(dir $(MAKEFILE_PATH))

BUILD_DIR = build

FIR_OPTS = -td $(BUILD_DIR) --output-file SimTop.v --full-stacktrace
SCALA_FILE = $(shell find ./src/main/scala -name '*.scala')
V = verilator
CPP = $(abspath ./csrc/main.cpp)
# OTHER_VSRC = $(shell find ./difftest/src/test/vsrc/common -name "*.v" -or -name "*.sv")
OTHER_VSRC = ./difftest/src/test/vsrc/common/ram.v

V_FLAG = --cc --exe --build \
--top-module SimTop \
-Mdir $(BUILD_DIR)/obj_dir  --trace \



$(BUILD_DIR)/SimTop.v: $(SCALA_FILE)
	mill -j 9 chiselModule.test.runMain Exp $(FIR_OPTS) 

sim-verilog: $(BUILD_DIR)/SimTop.v

simple:
	$(V) $(V_FLAG) $(BUILD_DIR)/SimTop.v  $(CPP) $(OTHER_VSRC)
	cp $(BUILD_DIR)/obj_dir/VSimTop $(BUILD_DIR)/emu
	$(BUILD_DIR)/emu $(MAKEFILE_DIR)/ready2run/coremark-mt-riscv64-nutshell.bin


emu: sim-verilog
	$(MAKE) -C ./difftest emu EMU_CXX_EXTRA_FLAGS="-DFIRST_INST_ADDRESS=0x80000000" EMU_TRACE=1 -j8
	./build/emu --diff ready2run/riscv64-nemu-interpreter-so -i ./ready2run/add-riscv64-nutshell.bin --dump-wave -b 0 --wave-path=vcd.vcd

help:
	mill chiselModule.runMain Sim --help

init:
	git submodule update --init --recursive

clean:
	rm -rf build/

.PHONY:verilog


gtk: 
	sudo gtkwave vcd.vcd

ntk:
	sudo gtkwave $(shell find ~/xs-env/NutShell/build/2*)

dv:
	rm -rf build/2*

test:
	mill -j 9 chiselModule.test.runMain Sim $(FIR_OPTS) 