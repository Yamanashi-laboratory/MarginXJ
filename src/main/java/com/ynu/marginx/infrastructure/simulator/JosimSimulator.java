package com.ynu.marginx.infrastructure.simulator;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.SimulationResult;
import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.infrastructure.config.SimulatorProperties;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.result.JosimCsvReader;
import com.ynu.marginx.shared.exception.SimulationFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Each run gets its own temporary directory. The C++ tool separated concurrent runs by PID, which
 * does not work once the parallelism moves from fork() to threads inside one JVM.
 */
public final class JosimSimulator implements CircuitSimulator {

    private static final String NETLIST_FILE = "MARGIN.cir";
    private static final String OUTPUT_FILE = "CIRCUIT.CSV";

    private final SimulatorProperties properties;
    private final NetlistRenderer renderer;
    private final JosimCsvReader reader;
    private final ProcessExecutor executor;

    public JosimSimulator(SimulatorProperties properties, NetlistRenderer renderer,
                          JosimCsvReader reader, ProcessExecutor executor) {
        this.properties = properties;
        this.renderer = renderer;
        this.reader = reader;
        this.executor = executor;
    }

    @Override
    public SimulationResult simulate(Netlist netlist) {
        Path workDirectory = createWorkDirectory();
        try {
            Files.write(workDirectory.resolve(NETLIST_FILE), renderer.render(netlist, OUTPUT_FILE));
            executor.run(List.of(properties.josimCommand(), NETLIST_FILE), workDirectory, properties.timeout());
            return reader.read(workDirectory.resolve(OUTPUT_FILE));
        } catch (IOException e) {
            throw new SimulationFailedException("Cannot write the intermediate netlist", e);
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    @Override
    public String name() {
        return properties.josimCommand();
    }

    private Path createWorkDirectory() {
        try {
            return Files.createTempDirectory("marginx-");
        } catch (IOException e) {
            throw new SimulationFailedException("Cannot create a working directory for the simulator", e);
        }
    }

    private void deleteRecursively(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A leftover temp file must not fail the margin run.
                }
            });
        } catch (IOException ignored) {
            // Same reasoning: cleanup is best effort.
        }
    }
}
