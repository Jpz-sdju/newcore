FIR_OPTS = td build --output-file top.v --full-stacktrace

verilog:
	mill chiselModule.test.runMain $()

help:
	mill chiselModule.runMain Sim --help