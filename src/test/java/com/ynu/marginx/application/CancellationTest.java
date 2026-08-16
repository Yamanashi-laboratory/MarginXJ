package com.ynu.marginx.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.domain.service.BinarySearchMarginSearcher;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationJudge;
import com.ynu.marginx.infrastructure.config.SimulatorKind;
import com.ynu.marginx.infrastructure.config.SimulatorLocation;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.result.JosimCsvReader;
import com.ynu.marginx.infrastructure.simulator.SimulatorWorkspaces;
import com.ynu.marginx.infrastructure.simulator.JosimSimulator;
import com.ynu.marginx.infrastructure.simulator.ProcessExecutor;
import com.ynu.marginx.shared.exception.CalculationCancelledException;
import com.ynu.marginx.testsupport.Circuits;
import com.ynu.marginx.testsupport.StubJosim;
import com.ynu.marginx.testsupport.StubJosimLauncher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cancelling has to stop the run and leave nothing behind: no simulator still running, and no
 * working directory in the temp folder. The stub is given a delay so the cancel lands while a
 * simulator is genuinely in flight, which is the case that leaks.
 */
class CancellationTest {

    private static final Duration SETTLE = Duration.ofSeconds(20);

    @TempDir
    Path scriptDirectory;

    @Test
    void cancellingLeavesNoWorkingDirectoryBehind() throws Exception {
        long before = temporaryWorkDirectories();
        CalculateMarginUseCase useCase = useCase(500);
        CountDownLatch firstElementStarted = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread run = new Thread(() -> {
            try {
                useCase.execute(netlist(), Circuits.singleWindow(), new ProgressListener() {
                    @Override
                    public void elementStarted(int index, String elementName) {
                        firstElementStarted.countDown();
                    }
                });
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "cancellation-test");
        run.start();

        assertThat(firstElementStarted.await(30, TimeUnit.SECONDS)).isTrue();
        useCase.cancel();
        run.join(SETTLE.toMillis());

        assertThat(run.isAlive()).as("execute() must return once cancelled").isFalse();
        assertThat(failure.get()).isInstanceOf(CalculationCancelledException.class);
        assertThat(useCase.awaitTermination(SETTLE)).isTrue();
        assertThat(temporaryWorkDirectories())
                .as("a cancelled run must not leave a working directory in the temp folder")
                .isEqualTo(before);
    }

    @Test
    void anInterruptedSearchStopsBeforeRunningAnotherSimulator() throws Exception {
        long before = temporaryWorkDirectories();
        CircuitSimulator simulator = simulator(50);
        OperationEvaluator evaluator = new OperationEvaluator(simulator, new OperationJudge());

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> new BinarySearchMarginSearcher(evaluator)
                    .search(Circuits.singleResistor(1.0), 0, Circuits.singleWindow()))
                    .isInstanceOf(CalculationCancelledException.class);
        } finally {
            Thread.interrupted();
        }

        assertThat(temporaryWorkDirectories()).isEqualTo(before);
    }

    @Test
    void theShutdownPathDeletesAWorkingDirectoryThatIsStillOpen() throws Exception {
        // Ctrl+C does not let the worker threads unwind, so the hook has to do the deleting.
        long before = temporaryWorkDirectories();
        CalculateMarginUseCase useCase = useCase(2000);
        CountDownLatch started = new CountDownLatch(1);

        Thread run = new Thread(() -> {
            try {
                useCase.execute(netlist(), Circuits.singleWindow(), new ProgressListener() {
                    @Override
                    public void elementStarted(int index, String elementName) {
                        started.countDown();
                    }
                });
            } catch (RuntimeException ignored) {
                // Cancelled, which is the point of the test.
            }
        }, "shutdown-test");
        run.start();
        assertThat(started.await(30, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(300);

        // What MarginXCommand registers as its shutdown hook.
        useCase.cancel();
        useCase.awaitTermination(SETTLE);
        SimulatorWorkspaces.deleteRemaining();
        run.join(SETTLE.toMillis());

        assertThat(temporaryWorkDirectories()).isEqualTo(before);
    }

    @Test
    void everyElementIsReportedAsItStartsAndAsItFinishes() throws Exception {
        List<String> startedElements = new CopyOnWriteArrayList<>();
        List<ElementMargin> results = new CopyOnWriteArrayList<>();
        List<Integer> counts = new CopyOnWriteArrayList<>();

        useCase(0).execute(netlist(), Circuits.singleWindow(), new ProgressListener() {
            @Override
            public void elementStarted(int index, String elementName) {
                startedElements.add(elementName);
            }

            @Override
            public void elementCompleted(int index, ElementMargin result) {
                results.add(result);
            }

            @Override
            public void advanced(int completed, int total) {
                counts.add(completed);
            }
        });

        assertThat(startedElements).containsExactly("R01");
        assertThat(results).hasSize(1);
        // The GUI shows this row before the run is over, so the margin has to be filled in already.
        assertThat(results.get(0).margin().upperPercent()).isGreaterThan(0);
        assertThat(counts).containsExactly(1);
    }

    private CalculateMarginUseCase useCase(long delayMillis) throws IOException {
        return new CalculateMarginUseCase(
                new BinarySearchMarginSearcher(
                        new OperationEvaluator(simulator(delayMillis), new OperationJudge())),
                MarginResultRepository.NONE);
    }

    private CircuitSimulator simulator(long delayMillis) throws IOException {
        String command = StubJosimLauncher.write(scriptDirectory, StubJosim.class, 0.5, 1.5, delayMillis);
        return new JosimSimulator(
                SimulatorLocation.found(SimulatorKind.JOSIM, Path.of(command), SimulatorLocation.Source.USER_SETTING),
                Duration.ofSeconds(60), new NetlistRenderer(), new JosimCsvReader(), new ProcessExecutor());
    }

    private Netlist netlist() {
        return Circuits.singleResistor(1.0);
    }

    private long temporaryWorkDirectories() throws IOException {
        Path tempRoot = Path.of(System.getProperty("java.io.tmpdir"));
        try (var paths = Files.list(tempRoot)) {
            return paths.filter(path -> path.getFileName().toString().startsWith("marginx-")).count();
        }
    }
}
