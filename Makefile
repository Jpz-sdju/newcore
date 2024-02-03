BUILD_DIR = build

FIR_OPTS = -td $(BUILD_DIR) --output-file Top.v --full-stacktrace

V = verilator
CPP = $(abspath ./csrc/main.cpp)
# OTHER_VSRC = $(shell find ./difftest/src/test/vsrc/common -name "*.v" -or -name "*.sv")
OTHER_VSRC = ./difftest/src/test/vsrc/common/ram.v

V_FLAG = --cc --exe --build \
--top-module Top \
-Mdir $(BUILD_DIR)/obj_dir  --trace \



verilog:
	mill chiselModule.test.runMain Sim $(FIR_OPTS)

test:
	mill chiselModule.test.runMain Exp $(FIR_OPTS)
	$(V) $(V_FLAG) $(BUILD_DIR)/Top.v  $(CPP) $(OTHER_VSRC)
	cp $(BUILD_DIR)/obj_dir/VTop $(BUILD_DIR)/emu
	$(BUILD_DIR)/emu
help:
	mill chiselModule.runMain Sim --help

init:
	git submodule update --init --recursive

clean:
	rm -rf build/

.PHONY:verilog


gtk: test
	sudo gtkwave vcd.vcd