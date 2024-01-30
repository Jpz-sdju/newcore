FIR_OPTS = -td build --output-file top.v --full-stacktrace

verilog:
	mill chiselModule.test.runMain Sim $(FIR_OPTS)

help:
	mill chiselModule.runMain Sim --help

init:
	git submodule update --init --recursive

clean:
	rm -rf build/

.PHONY:verilog