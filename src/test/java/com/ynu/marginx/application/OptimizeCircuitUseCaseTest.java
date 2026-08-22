package com.ynu.marginx.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.model.optimize.ScoreWeights;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.domain.port.NetlistRepository;
import com.ynu.marginx.domain.service.BinarySearchMarginSearcher;
import com.ynu.marginx.domain.service.CenterOfGravityOptimizer;
import com.ynu.marginx.domain.service.CenterOfGravityOptimizer.Settings;
import com.ynu.marginx.domain.service.CriticalElementFinder;
import com.ynu.marginx.domain.service.CriticalMarginCalculator;
import com.ynu.marginx.domain.service.CriticalMarginMethod;
import com.ynu.marginx.domain.service.ExhaustiveMarginSearcher;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationJudge;
import com.ynu.marginx.domain.service.RandomSource;
import com.ynu.marginx.domain.service.ScoreCalculator;
import com.ynu.marginx.shared.exception.CalculationCancelledException;
import com.ynu.marginx.testsupport.Circuits;
import com.ynu.marginx.testsupport.WindowSimulator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;

/**
 * The two things the optimisers do not do for themselves: say how far along they are, and stop.
 *
 * <p>Both come from the collaborators handed in here rather than from the optimisers, so what these
 * check is that counting and cancelling reach the work without the domain having been told about
 * either.
 */
class OptimizeCircuitUseCaseTest {

    /** Few cycles, full trials: the widening step is compared against a count out of a hundred. */
    private static final Settings SMALL = new Settings(6, 100, 5, 60, 100);

    private final JudgementSpec spec = Circuits.singleWindow();
    private final ScoreWeights weights = ScoreWeights.criticalOnly();
    private final RecordingListener listener = new RecordingListener();
    private final RecordingNetlists netlists = new RecordingNetlists();

    @Test
    void reportsACycleForEveryBatchOfTrials() {
        OptimizeCircuitUseCase useCase = useCase();

        useCase.withCenterOfGravity(optimizer(useCase), Circuits.singleResistor(1.35), spec, weights);

        // One cycle per call to the sampler, numbered from zero and never repeated.
        assertThat(listener.cycles).isNotEmpty().containsExactlyElementsOf(sequence(listener.cycles.size()));
        assertThat(listener.cycles.size()).isLessThanOrEqualTo(SMALL.cycles());
        assertThat(listener.totalCycles).containsOnly(SMALL.cycles());
    }

    @Test
    void reportsEveryReMeasurementWithTheTableItProduced() {
        OptimizeCircuitUseCase useCase = useCase();

        useCase.withCenterOfGravity(optimizer(useCase), Circuits.singleResistor(1.35), spec, weights);

        // The optimiser measures before the loop and again at the end, so there are always two.
        assertThat(listener.measurementsStarted).hasSizeGreaterThanOrEqualTo(2);
        assertThat(listener.measurementsCompleted).containsExactlyElementsOf(listener.measurementsStarted);
        assertThat(listener.tables).allSatisfy(table -> assertThat(table.size()).isEqualTo(1));
    }

    @Test
    void theCriticalMarginMethodIsCountedTheSameWay() {
        OptimizeCircuitUseCase useCase = useCase();
        OperationEvaluator evaluator = evaluator();
        CriticalMarginMethod method = new CriticalMarginMethod(
                useCase.measurements(new ExhaustiveMarginSearcher(evaluator)),
                useCase.measurements(new BinarySearchMarginSearcher(evaluator)),
                new CriticalElementFinder());

        useCase.withCriticalMarginMethod(method, Circuits.singleResistor(1.35), spec);

        // It has no cycles to report - only measurements, one per trial plus the first.
        assertThat(listener.cycles).isEmpty();
        assertThat(listener.measurementsCompleted).isNotEmpty()
                .containsExactlyElementsOf(sequence(listener.measurementsCompleted.size()));
    }

    @Test
    void cancellingEndsTheRunWhereItStands() {
        OptimizeCircuitUseCase useCase = useCase();
        // Stop it part-way, the way the cancel button does: from outside, mid-run.
        listener.onCycle = cycle -> {
            if (cycle == 2) {
                useCase.cancel();
            }
        };

        assertThatThrownBy(() -> useCase.withCenterOfGravity(
                optimizer(useCase), Circuits.singleResistor(1.35), spec, weights))
                .isInstanceOf(CalculationCancelledException.class);

        // Cancelled part-way through is not an answer, so nothing is filed as if it were one.
        assertThat(netlists.saved).isEmpty();
        assertThat(listener.cycles).hasSize(3);
    }

    @Test
    void aCancelledUseCaseStaysCancelled() {
        OptimizeCircuitUseCase useCase = useCase();
        useCase.cancel();

        assertThatThrownBy(() -> useCase.withCenterOfGravity(
                optimizer(useCase), Circuits.singleResistor(1.35), spec, weights))
                .isInstanceOf(CalculationCancelledException.class);

        // It stopped before simulating anything at all.
        assertThat(listener.cycles).isEmpty();
        assertThat(listener.measurementsStarted).isEmpty();
    }

    @Test
    void awaitingTerminationIsSafeBeforeAnythingHasRun() {
        assertThat(useCase().awaitTermination(Duration.ofMillis(50))).isTrue();
    }

    private OptimizeCircuitUseCase useCase() {
        return new OptimizeCircuitUseCase(netlists, MarginResultRepository.NONE, listener);
    }

    private CenterOfGravityOptimizer optimizer(OptimizeCircuitUseCase useCase) {
        CriticalMarginCalculator criticalMargins = new CriticalMarginCalculator();
        return new CenterOfGravityOptimizer(
                useCase.sampling(evaluator(), SMALL.cycles()),
                useCase.measurements(new BinarySearchMarginSearcher(evaluator())), criticalMargins,
                new ScoreCalculator(criticalMargins), RandomSource.seeded(20260822), SMALL,
                CenterOfGravityOptimizer.Variant.YIELD_UP);
    }

    private OperationEvaluator evaluator() {
        return new OperationEvaluator(new WindowSimulator(0, 0.5, 1.5), new OperationJudge());
    }

    private static List<Integer> sequence(int size) {
        List<Integer> expected = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            expected.add(index);
        }
        return expected;
    }

    private static final class RecordingListener implements OptimizationProgressListener {

        private final List<Integer> cycles = new ArrayList<>();
        private final List<Integer> totalCycles = new ArrayList<>();
        private final List<Integer> measurementsStarted = new ArrayList<>();
        private final List<Integer> measurementsCompleted = new ArrayList<>();
        private final List<MarginTable> tables = new ArrayList<>();
        private IntConsumer onCycle = cycle -> {
        };

        @Override
        public void cycleStarted(int cycle, int total) {
            cycles.add(cycle);
            totalCycles.add(total);
            onCycle.accept(cycle);
        }

        @Override
        public void measurementStarted(int measurement) {
            measurementsStarted.add(measurement);
        }

        @Override
        public void measurementCompleted(int measurement, MarginTable table) {
            measurementsCompleted.add(measurement);
            tables.add(table);
        }
    }

    private static final class RecordingNetlists implements NetlistRepository {

        private final List<String> saved = new ArrayList<>();

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
