package com.ynu.marginx.presentation.gui.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.ynu.marginx.application.CalculateMarginUseCase;
import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.domain.service.BinarySearchMarginSearcher;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationJudge;
import com.ynu.marginx.infrastructure.config.SimulatorKind;
import com.ynu.marginx.infrastructure.config.SimulatorLocation;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.result.JosimCsvReader;
import com.ynu.marginx.infrastructure.simulator.JosimSimulator;
import com.ynu.marginx.infrastructure.simulator.ProcessExecutor;
import com.ynu.marginx.testsupport.Circuits;
import com.ynu.marginx.testsupport.FxToolkit;
import com.ynu.marginx.testsupport.StubJosim;
import com.ynu.marginx.testsupport.StubJosimLauncher;
import com.ynu.marginx.testsupport.WindowSimulator;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The task is what keeps the window responsive, so what matters here is that results arrive one at
 * a time while it is still running, and that cancelling it actually stops the calculation.
 */
class MarginCalculationTaskTest {

    @TempDir
    Path scriptDirectory;

    @BeforeAll
    static void toolkit() {
        FxToolkit.startOrSkip();
    }

    @Test
    void handsOverEachElementAsItFinishes() throws Exception {
        List<ElementMargin> delivered = new CopyOnWriteArrayList<>();
        CountDownLatch bothArrived = new CountDownLatch(2);
        CalculateMarginUseCase useCase = new CalculateMarginUseCase(
                new BinarySearchMarginSearcher(new OperationEvaluator(
                        new WindowSimulator(0, 0.5, 1.5), new OperationJudge())),
                MarginResultRepository.NONE);

        MarginCalculationTask task = new MarginCalculationTask(useCase,
                Circuits.synchronizedPair(1.0), Circuits.singleWindow(), (result, index) -> {
                    delivered.add(result);
                    bothArrived.countDown();
                });

        Thread worker = new Thread(task, "task-test");
        worker.start();
        MarginTable table = task.get();
        worker.join(TimeUnit.SECONDS.toMillis(20));

        assertThat(bothArrived.await(20, TimeUnit.SECONDS))
                .as("both elements must be handed over individually, not only at the end")
                .isTrue();
        assertThat(delivered).hasSize(2);
        assertThat(table.size()).isEqualTo(2);
        // The rows the table shows carry the measured margin, not a placeholder.
        assertThat(delivered).allSatisfy(row -> assertThat(row.margin().upperPercent()).isNotZero());
    }

    @Test
    void cancellingStopsTheCalculation() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CircuitSimulator slow = slowSimulator();
        CalculateMarginUseCase useCase = new CalculateMarginUseCase(
                new BinarySearchMarginSearcher(new OperationEvaluator(slow, new OperationJudge())),
                MarginResultRepository.NONE);
        MarginCalculationTask task = new MarginCalculationTask(useCase,
                Circuits.singleResistor(1.0), Circuits.singleWindow(), (result, index) -> { });
        task.messageProperty().addListener((observable, previous, message) -> {
            if (message != null && message.startsWith("Measuring R01")) {
                running.countDown();
            }
        });

        Thread worker = new Thread(task, "task-cancel-test");
        worker.start();
        assertThat(running.await(30, TimeUnit.SECONDS)).isTrue();
        FxToolkit.run(() -> task.cancel());
        worker.join(TimeUnit.SECONDS.toMillis(30));
        task.awaitCleanup();

        assertThat(worker.isAlive()).isFalse();
        assertThat(task.isCancelled() || task.isDone()).isTrue();
    }

    private CircuitSimulator slowSimulator() throws IOException {
        String command = StubJosimLauncher.write(scriptDirectory, StubJosim.class, 0.5, 1.5, 500);
        return new JosimSimulator(
                SimulatorLocation.found(SimulatorKind.JOSIM, Path.of(command), SimulatorLocation.Source.USER_SETTING),
                Duration.ofSeconds(60), new NetlistRenderer(), new JosimCsvReader(), new ProcessExecutor());
    }
}
