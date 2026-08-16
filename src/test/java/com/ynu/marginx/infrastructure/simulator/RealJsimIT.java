package com.ynu.marginx.infrastructure.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.ynu.marginx.application.CalculateMarginUseCase;
import com.ynu.marginx.application.ProgressListener;
import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.domain.service.BinarySearchMarginSearcher;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationJudge;
import com.ynu.marginx.infrastructure.config.SimulatorProperties;
import com.ynu.marginx.infrastructure.judgement.FileJudgementSpecRepository;
import com.ynu.marginx.infrastructure.judgement.JudgementSpecParser;
import com.ynu.marginx.infrastructure.netlist.FileNetlistRepository;
import com.ynu.marginx.infrastructure.netlist.JsimPrintDirectiveConverter;
import com.ynu.marginx.infrastructure.netlist.NetlistParser;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.result.FileMarginResultRepository;
import com.ynu.marginx.infrastructure.result.JsimCsvReader;
import com.ynu.marginx.testsupport.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the real JSIM against the reference JTL circuit. Skipped unless the binary is named
 * explicitly, because JSIM is not part of the build:
 *
 * <pre>./gradlew test -Dmarginx.it.jsim=jsim</pre>
 *
 * <p>Verified against JSIM as installed on a Windows workstation: the deck comes back upper-cased,
 * the CSV has no header, and the circuit passes its judgement the same way it does under JoSIM.
 */
@EnabledIfSystemProperty(named = "marginx.it.jsim", matches = ".+")
class RealJsimIT {

    @TempDir
    Path workingDirectory;

    private Netlist netlist;
    private JudgementSpec spec;
    private CircuitSimulator simulator;

    @BeforeEach
    void setUp() throws IOException {
        Files.write(workingDirectory.resolve("test_JTL.cir"), Fixtures.circuitLines());
        Files.write(workingDirectory.resolve("test_JTL.txt"), Fixtures.judgementLines());

        netlist = new FileNetlistRepository(workingDirectory, new NetlistParser()).load("test_JTL");
        spec = new FileJudgementSpecRepository(workingDirectory, new JudgementSpecParser()).load("test_JTL");
        simulator = new JsimSimulator(
                new SimulatorProperties("josim", System.getProperty("marginx.it.jsim"), Duration.ofMinutes(2)),
                new NetlistRenderer(), new JsimPrintDirectiveConverter(), new JsimCsvReader(),
                new ProcessExecutor());
    }

    @Test
    void theReferenceCircuitPassesItsJudgement() {
        OperationEvaluator evaluator = new OperationEvaluator(simulator, new OperationJudge());

        assertThat(evaluator.evaluate(netlist, spec).violation()).isNull();
    }

    @Test
    void everyTargetGetsAFiniteMargin() {
        OperationEvaluator evaluator = new OperationEvaluator(simulator, new OperationJudge());
        MarginTable table = new CalculateMarginUseCase(
                new BinarySearchMarginSearcher(evaluator),
                new FileMarginResultRepository(workingDirectory))
                .execute(netlist, spec, ProgressListener.NOOP);

        assertThat(table.size()).isEqualTo(netlist.elementCount());
        for (ElementMargin entry : table.entries()) {
            assertThat(entry.margin().lowerPercent()).isFinite().isLessThanOrEqualTo(0);
            assertThat(entry.margin().upperPercent()).isFinite().isGreaterThanOrEqualTo(0);
        }
    }
}
