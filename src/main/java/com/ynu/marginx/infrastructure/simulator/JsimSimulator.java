package com.ynu.marginx.infrastructure.simulator;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.SimulationResult;
import com.ynu.marginx.infrastructure.config.SimulatorLocation;
import com.ynu.marginx.infrastructure.netlist.JsimPrintDirectiveConverter;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.result.JsimCsvReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Drives JSIM, the fallback for circuits on machines without JoSIM.
 *
 * <p>Two things differ from JoSIM and both are load-bearing. JSIM upper-cases the whole deck as it
 * reads it, the {@code .FILE} argument included, so the output name is only stable if we ask for it
 * in upper case to begin with. And its CSV carries no header row.
 */
public final class JsimSimulator extends ExternalProcessSimulator {

    private static final String OUTPUT_FILE = "CIRCUIT.CSV";

    private final NetlistRenderer renderer;
    private final JsimPrintDirectiveConverter converter;
    private final JsimCsvReader reader;

    public JsimSimulator(SimulatorLocation location, Duration timeout, NetlistRenderer renderer,
                         JsimPrintDirectiveConverter converter, JsimCsvReader reader,
                         ProcessExecutor executor) {
        super(executor, timeout, location);
        this.renderer = renderer;
        this.converter = converter;
        this.reader = reader;
    }

    @Override
    List<String> render(Netlist netlist, String outputFileName) {
        return converter.convert(renderer.render(netlist, outputFileName));
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
