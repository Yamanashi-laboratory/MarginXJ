package com.ynu.marginx.infrastructure.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.ynu.marginx.domain.model.judge.SimulationResult;
import com.ynu.marginx.domain.model.margin.Margin;
import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.domain.service.BinarySearchMarginSearcher;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationJudge;
import com.ynu.marginx.infrastructure.config.SimulatorKind;
import com.ynu.marginx.infrastructure.config.SimulatorLocation;
import com.ynu.marginx.infrastructure.netlist.JsimPrintDirectiveConverter;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.result.JsimCsvReader;
import com.ynu.marginx.testsupport.Circuits;
import com.ynu.marginx.testsupport.StubJosimLauncher;
import com.ynu.marginx.testsupport.StubJsim;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The JSIM counterpart of {@link JosimSimulatorProcessTest}: a real external process, stubbed only
 * where JSIM's physics would be. It pins the two ways JSIM differs from JoSIM - the upper-cased
 * output name and the header-less CSV - because both are silent failures if they regress.
 */
class JsimSimulatorProcessTest {

    @TempDir
    Path scriptDirectory;

    private CircuitSimulator simulator;

    @BeforeEach
    void setUp() throws IOException {
        simulator = simulatorAcceptingWindow(0.5, 1.5);
    }

    @Test
    void readsBackTheHeaderlessOutput() {
        SimulationResult result = simulator.simulate(Circuits.singleResistor(1.0));

        // 12 rows, not 11: dropping a header that JSIM never writes would swallow t=0.
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
    void searchesMarginsThroughRealProcesses() {
        OperationEvaluator evaluator = new OperationEvaluator(simulator, new OperationJudge());

        Margin margin = new BinarySearchMarginSearcher(evaluator)
                .search(Circuits.singleResistor(1.0), 0, Circuits.singleWindow());

        assertThat(margin.lowerPercent()).isCloseTo(-50, within(1.0));
        assertThat(margin.upperPercent()).isCloseTo(50, within(1.0));
    }

    private CircuitSimulator simulatorAcceptingWindow(double lower, double upper) throws IOException {
        String command = StubJosimLauncher.write(scriptDirectory, StubJsim.class, lower, upper);
        return new JsimSimulator(
                SimulatorLocation.found(SimulatorKind.JSIM, Path.of(command), SimulatorLocation.Source.USER_SETTING),
                Duration.ofSeconds(60), new NetlistRenderer(), new JsimPrintDirectiveConverter(),
                new JsimCsvReader(), new ProcessExecutor());
    }
}
