package com.ynu.marginx.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.model.optimize.OptimizationOutcome;
import com.ynu.marginx.domain.model.optimize.ScoreWeights;
import com.ynu.marginx.domain.service.CenterOfGravityOptimizer.Settings;
import com.ynu.marginx.testsupport.Circuits;
import com.ynu.marginx.testsupport.WindowSimulator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The optimiser is driven by random draws, so these pin the behaviour that must hold for any seed
 * rather than a particular sequence of values: it walks towards the middle of the window it can
 * survive in, it never hands back a worse circuit than it was given, and it leaves *FIX alone.
 */
class CenterOfGravityOptimizerTest {

    /**
     * Fewer cycles, but the full hundred trials each: the yield threshold is compared against the
     * count of surviving trials, which only means "percent" because MULTI_NUM is 100. Shrinking the
     * trial count would put the widening step out of reach and leave half the loop untested.
     */
    private static final Settings SMALL = new Settings(10, 100, 5, 60, 100);

    private final JudgementSpec spec = Circuits.singleWindow();
    private final ScoreWeights weights = ScoreWeights.criticalOnly();

    @Test
    void walksAnOffCentreCircuitTowardsTheMiddleOfItsWindow() {
        // Window 0.5..1.5, so 1.0 is the value with the most room on both sides.
        OptimizationOutcome outcome = optimizer(0.5, 1.5, 12345)
                .optimize(Circuits.singleResistor(1.35), spec, weights);

        double optimised = outcome.netlist().element(0).value();
        assertThat(Math.abs(optimised - 1.0)).isLessThan(Math.abs(1.35 - 1.0));
    }

    @Test
    void neverReturnsACircuitThatScoresWorseThanTheOneItWasGiven() {
        // Where the C++ would hand back its all-zero best_value, the port keeps the input.
        Netlist start = Circuits.singleResistor(1.0);
        ScoreCalculator scores = new ScoreCalculator(new CriticalMarginCalculator());
        MarginTableCalculator margins = margins(0.5, 1.5);

        OptimizationOutcome outcome = optimizer(0.5, 1.5, 7).optimize(start, spec, weights);

        assertThat(scores.score(outcome.margins(), weights))
                .isGreaterThanOrEqualTo(scores.score(margins.calculate(start, spec), weights));
        assertThat(outcome.netlist().element(0).value()).isPositive();
    }

    @Test
    void leavesAFixedElementOnItsStartingValue() {
        Netlist fixed = Circuits.singleResistor(1.35);
        fixed = fixed.withElements(List.of(fixed.element(0).fixedValue()));

        OptimizationOutcome outcome = optimizer(0.5, 1.5, 99).optimize(fixed, spec, weights);

        assertThat(outcome.netlist().element(0).value()).isEqualTo(1.35);
    }

    @Test
    void keepsASynchronisedGroupInStep() {
        OptimizationOutcome outcome = optimizer(0.5, 1.5, 4242)
                .optimize(Circuits.synchronizedPair(1.2), spec, weights);

        assertThat(outcome.netlist().element(0).value())
                .isEqualTo(outcome.netlist().element(1).value());
    }

    @Test
    void theSequentialVariantAlsoImprovesAnOffCentreCircuit() {
        OptimizationOutcome outcome = optimizer(0.5, 1.5, 2024, CenterOfGravityOptimizer.Variant.SEQUENTIAL)
                .optimize(Circuits.singleResistor(1.35), spec, weights);

        double optimised = outcome.netlist().element(0).value();
        assertThat(Math.abs(optimised - 1.0)).isLessThan(Math.abs(1.35 - 1.0));
    }

    @Test
    void theTwoVariantsDifferOnTheWideningBoundary() {
        // optimize_yield_up.cpp widens at the threshold, optimize_seq.cpp only past it. Everywhere
        // else they agree, which is why a whole run can come out identical for both.
        assertThat(CenterOfGravityOptimizer.Variant.YIELD_UP.widensAt(60, 60)).isTrue();
        assertThat(CenterOfGravityOptimizer.Variant.SEQUENTIAL.widensAt(60, 60)).isFalse();
        assertThat(CenterOfGravityOptimizer.Variant.SEQUENTIAL.widensAt(61, 60)).isTrue();
    }

    private CenterOfGravityOptimizer optimizer(double lower, double upper, long seed) {
        return optimizer(lower, upper, seed, CenterOfGravityOptimizer.Variant.YIELD_UP);
    }

    private CenterOfGravityOptimizer optimizer(double lower, double upper, long seed,
                                               CenterOfGravityOptimizer.Variant variant) {
        return new CenterOfGravityOptimizer(
                OperationSampler.sequential(evaluator(lower, upper)), margins(lower, upper),
                new CriticalMarginCalculator(), new ScoreCalculator(new CriticalMarginCalculator()),
                RandomSource.seeded(seed), SMALL, variant);
    }

    private OperationEvaluator evaluator(double lower, double upper) {
        return new OperationEvaluator(new WindowSimulator(0, lower, upper), new OperationJudge());
    }

    private MarginTableCalculator margins(double lower, double upper) {
        MarginSearcher searcher = new BinarySearchMarginSearcher(evaluator(lower, upper));
        return (netlist, judgement) -> {
            List<ElementMargin> entries = new ArrayList<>();
            for (int index = 0; index < netlist.elementCount(); index++) {
                entries.add(new ElementMargin(netlist.element(index), searcher.search(netlist, index, judgement)));
            }
            return new MarginTable(entries);
        };
    }
}
