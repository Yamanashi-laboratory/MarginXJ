package com.ynu.marginx.infrastructure.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.SimulationResult;
import com.ynu.marginx.domain.model.margin.Margin;
import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.domain.service.BinarySearchMarginSearcher;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationJudge;
import com.ynu.marginx.infrastructure.config.SimulatorProperties;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.result.JosimCsvReader;
import com.ynu.marginx.shared.exception.SimulationFailedException;
import com.ynu.marginx.testsupport.Circuits;
import com.ynu.marginx.testsupport.StubJosimLauncher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the simulator adapter against a real external process, so the netlist hand-off, the
 * working-directory isolation and the CSV round trip are all exercised for real. Only JoSIM's own
 * physics is stubbed out.
 */
class JosimSimulatorProcessTest {

    @TempDir
    Path scriptDirectory;

    private CircuitSimulator simulator;

    @BeforeEach
    void setUp() throws IOException {
        simulator = simulatorAcceptingWindow(0.5, 1.5);
    }

    @Test
    void runsTheSimulatorAndReadsBackItsOutput() {
        SimulationResult result = simulator.simulate(Circuits.singleResistor(1.0));

        assertThat(result.rowCount()).isEqualTo(12);
        assertThat(result.columnCount()).isEqualTo(2);
        assertThat(result.startTime()).isEqualTo(0.0);
        assertThat(result.timeScale()).isCloseTo(1.0, within(1e-9));
        assertThat(result.at(5, 1)).isEqualTo(4.0);
        assertThat(result.at(4, 1)).isEqualTo(0.0);
    }

    @Test
    void handsTheSweptValueToTheSimulator() {
        SimulationResult operating = simulator.simulate(Circuits.singleResistor(1.0));
        SimulationResult failing = simulator.simulate(Circuits.singleResistor(9.0));

        assertThat(operating.at(11, 1)).isEqualTo(4.0);
        assertThat(failing.at(11, 1)).isEqualTo(0.0);
    }

    @Test
    void leavesNoWorkingDirectoryBehind() throws IOException {
        long before = temporaryWorkDirectories();

        simulator.simulate(Circuits.singleResistor(1.0));

        assertThat(temporaryWorkDirectories()).isEqualTo(before);
    }

    @Test
    void concurrentSimulationsDoNotShareIntermediateFiles() {
        // The C++ tool separated runs by PID; threads in one JVM share it, so this is the
        // regression test for the per-run temporary directory.
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Boolean> operating = IntStream.range(0, 32)
                    .mapToObj(index -> pool.submit(() -> {
                        double value = index % 2 == 0 ? 1.0 : 9.0;
                        return simulator.simulate(Circuits.singleResistor(value)).at(11, 1) == 4.0;
                    }))
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new AssertionError(e);
                        }
                    })
                    .toList();

            assertThat(IntStream.range(0, 32).allMatch(index -> operating.get(index) == (index % 2 == 0)))
                    .as("each run must see its own netlist")
                    .isTrue();
        }
    }

    @Test
    void reportsAMissingSimulatorInsteadOfHanging() {
        CircuitSimulator missing = new JosimSimulator(
                new SimulatorProperties("no-such-simulator-binary", "jsim", Duration.ofSeconds(10)),
                new NetlistRenderer(), new JosimCsvReader(), new ProcessExecutor());

        assertThatThrownBy(() -> missing.simulate(Circuits.singleResistor(1.0)))
                .isInstanceOf(SimulationFailedException.class)
                .hasMessageContaining("Cannot start");
    }

    @Test
    void searchesMarginsThroughRealProcesses() {
        Netlist netlist = Circuits.singleResistor(1.0);
        OperationEvaluator evaluator = new OperationEvaluator(simulator, new OperationJudge());

        Margin margin = new BinarySearchMarginSearcher(evaluator)
                .search(netlist, 0, Circuits.singleWindow());

        assertThat(margin.lowerPercent()).isCloseTo(-50, within(1.0));
        assertThat(margin.upperPercent()).isCloseTo(50, within(1.0));
    }

    private CircuitSimulator simulatorAcceptingWindow(double lower, double upper) throws IOException {
        String command = StubJosimLauncher.write(scriptDirectory, lower, upper);
        return new JosimSimulator(
                new SimulatorProperties(command, "jsim", Duration.ofSeconds(60)),
                new NetlistRenderer(), new JosimCsvReader(), new ProcessExecutor());
    }

    private long temporaryWorkDirectories() throws IOException {
        Path tempRoot = Path.of(System.getProperty("java.io.tmpdir"));
        try (var paths = Files.list(tempRoot)) {
            return paths.filter(path -> path.getFileName().toString().startsWith("marginx-")).count();
        }
    }
}
