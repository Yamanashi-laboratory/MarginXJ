package com.ynu.marginx.infrastructure.simulator;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.SimulationResult;
import com.ynu.marginx.infrastructure.config.SimulatorLocation;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.result.JosimCsvReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Drives JoSIM, which writes its CSV where the {@code .FILE} directive points. */
public final class JosimSimulator extends ExternalProcessSimulator {

    private static final String OUTPUT_FILE = "CIRCUIT.CSV";

    private final NetlistRenderer renderer;
    private final JosimCsvReader reader;

    public JosimSimulator(SimulatorLocation location, Duration timeout, NetlistRenderer renderer,
                          JosimCsvReader reader, ProcessExecutor executor) {
        super(executor, timeout, location);
        this.renderer = renderer;
        this.reader = reader;
    }

    @Override
    List<String> render(Netlist netlist, String outputFileName) {
        return renderer.render(netlist, outputFileName);
    }

    @Override
    String outputFileName() {
        return OUTPUT_FILE;
    }

    @Override
    SimulationResult read(Path csv) {
        return reader.read(csv);
    }
}
