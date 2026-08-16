package com.ynu.marginx.infrastructure.simulator;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.SimulationResult;
import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.shared.exception.SimulationFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shared plumbing for the simulators we drive as external processes. Each run gets its own
 * temporary directory: the C++ tool separated concurrent runs by PID, which does not work once the
 * parallelism moves from fork() to threads inside one JVM.
 *
 * <p>The two adapters differ only in the command, the netlist they hand over and the reader that
 * interprets the result - the same split the {@code _jsim} twins in the C++ tree encode by
 * duplicating every file.
 */
abstract sealed class ExternalProcessSimulator implements CircuitSimulator
        permits JosimSimulator, JsimSimulator {

    private static final String NETLIST_FILE = "MARGIN.cir";

    private final ProcessExecutor executor;
    private final Duration timeout;

    ExternalProcessSimulator(ProcessExecutor executor, Duration timeout) {
        this.executor = executor;
        this.timeout = timeout;
    }

    @Override
    public final SimulationResult simulate(Netlist netlist) {
        Path workDirectory = createWorkDirectory();
        try {
            Files.write(workDirectory.resolve(NETLIST_FILE), render(netlist, outputFileName()));
            executor.run(List.of(name(), NETLIST_FILE), workDirectory, timeout);
            return read(workDirectory.resolve(outputFileName()));
        } catch (IOException e) {
            throw new SimulationFailedException("Cannot write the intermediate netlist", e);
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    /** The netlist as this simulator wants to read it, pointed at {@code outputFileName}. */
    abstract List<String> render(Netlist netlist, String outputFileName);

    /** The name asked for through {@code .FILE}, and read back afterwards. */
    abstract String outputFileName();

    abstract SimulationResult read(Path csv);

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
