package com.ynu.marginx.presentation.gui.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.judge.SimulationResult;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.model.optimize.OptimizationOutcome;
import com.ynu.marginx.domain.model.optimize.ScoreWeights;
import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.domain.port.NetlistRepository;
import com.ynu.marginx.domain.service.BinarySearchMarginSearcher;
import com.ynu.marginx.domain.service.CenterOfGravityOptimizer;
import com.ynu.marginx.domain.service.CriticalElementFinder;
import com.ynu.marginx.domain.service.CriticalMarginCalculator;
import com.ynu.marginx.domain.service.CriticalMarginMethod;
import com.ynu.marginx.domain.service.ExhaustiveMarginSearcher;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationJudge;
import com.ynu.marginx.domain.service.RandomSource;
import com.ynu.marginx.domain.service.ScoreCalculator;
import com.ynu.marginx.testsupport.Circuits;
import com.ynu.marginx.testsupport.FxToolkit;
import com.ynu.marginx.testsupport.WindowSimulator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * An optimisation is the longest thing this window can be asked to do, so what matters is that it
 * reports where it has got to while it is still going, and that the cancel button ends it.
 */
class OptimizationTaskTest {

    private static final int TIMEOUT_SECONDS = 30;

    private final JudgementSpec spec = Circuits.singleWindow();
    private final RecordingNetlists netlists = new RecordingNetlists();

    @BeforeAll
    static void toolkit() {
        FxToolkit.startOrSkip();
    }

    @Test
    void handsOverEveryMeasurementWhileTheRunIsStillGoing() throws Exception {
        List<MarginTable> measured = new CopyOnWriteArrayList<>();
        CountDownLatch firstArrived = new CountDownLatch(1);
        OperationEvaluator evaluator = evaluator(new WindowSimulator(0, 0.5, 1.5));

        OptimizationTask task = new OptimizationTask(netlists, MarginResultRepository.NONE,
                0, CriticalMarginMethod.MAX_TRIALS + 1,
                table -> {
                    measured.add(table);
                    firstArrived.countDown();
                },
                useCase -> useCase.withCriticalMarginMethod(new CriticalMarginMethod(
                        useCase.measurements(new ExhaustiveMarginSearcher(evaluator)),
                        useCase.measurements(new BinarySearchMarginSearcher(evaluator)),
                        new CriticalElementFinder()), Circuits.singleResistor(1.35), spec));

        OptimizationOutcome outcome = run(task);

        assertThat(firstArrived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("a measurement must reach the window before the run has finished")
                .isTrue();
        assertThat(measured).hasSizeGreaterThanOrEqualTo(2);
        assertThat(outcome.netlist().element(0).value()).isNotEqualTo(1.35);
        // The circuit it settled on is filed; the tables on the way there are not.
        assertThat(netlists.saved).containsExactly("single");
    }

    @Test
    void cancellingStopsTheRunAndFilesNothing() throws Exception {
        CountDownLatch simulating = new CountDownLatch(1);
        // Slow enough that the run is still going when the cancel arrives, without a fixed sleep.
        OperationEvaluator evaluator = evaluator(new SlowSimulator(simulating));
        CenterOfGravityOptimizer.Settings settings = CenterOfGravityOptimizer.Settings.defaults();

        OptimizationTask task = new OptimizationTask(netlists, MarginResultRepository.NONE,
                settings.cycles(), 0, table -> { },
                useCase -> {
                    CriticalMarginCalculator criticalMargins = new CriticalMarginCalculator();
                    return useCase.withCenterOfGravity(new CenterOfGravityOptimizer(
                            useCase.sampling(evaluator, settings.cycles()),
                            useCase.measurements(new BinarySearchMarginSearcher(evaluator)),
                            criticalMargins, new ScoreCalculator(criticalMargins),
                            RandomSource.seeded(20260822), settings,
                            CenterOfGravityOptimizer.Variant.YIELD_UP),
                            Circuits.singleResistor(1.0), spec, ScoreWeights.criticalOnly());
                });

        Thread worker = new Thread(task, "optimisation-test");
        worker.setDaemon(true);
        worker.start();
        assertThat(simulating.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("the run must have started before it is cancelled")
                .isTrue();

        FxToolkit.run(() -> task.cancel());
        task.awaitCleanup();
        worker.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

        assertThat(worker.isAlive()).as("the optimisation must not outlive the cancel").isFalse();
        // Cancelled part-way is not an answer, so no optimised circuit is written out.
        assertThat(netlists.saved).isEmpty();
    }

    private OptimizationOutcome run(OptimizationTask task) throws Exception {
        Thread worker = new Thread(task, "optimisation-test");
        worker.setDaemon(true);
        worker.start();
        OptimizationOutcome outcome = task.get();
        worker.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        return outcome;
    }

    private OperationEvaluator evaluator(CircuitSimulator simulator) {
        return new OperationEvaluator(simulator, new OperationJudge());
    }

    /** Takes long enough per simulation that a cancel lands mid-cycle rather than after it. */
    private static final class SlowSimulator implements CircuitSimulator {

        private final CountDownLatch started;
        private final WindowSimulator window = new WindowSimulator(0, 0.5, 1.5);

        private SlowSimulator(CountDownLatch started) {
            this.started = started;
        }

        @Override
        public SimulationResult simulate(Netlist netlist) {
            started.countDown();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // The cancel arrived. Restoring the flag is what lets the worker unwind.
                Thread.currentThread().interrupt();
            }
            return window.simulate(netlist);
        }

        @Override
        public String name() {
            return "slow";
        }
    }

    private static final class RecordingNetlists implements NetlistRepository {

        private final List<String> saved = new CopyOnWriteArrayList<>();

        @Override
        public Netlist load(String baseName) {
            throw new UnsupportedOperationException("The circuit is handed in already loaded");
        }

        @Override
        public void save(String baseName, Netlist netlist) {
            saved.add(baseName);
        }
    }
}
