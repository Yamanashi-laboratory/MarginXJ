package com.ynu.marginx.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.model.optimize.OptimizationOutcome;
import com.ynu.marginx.domain.model.optimize.OptimizationOutcome.StopReason;
import com.ynu.marginx.testsupport.Circuits;
import com.ynu.marginx.testsupport.WindowSimulator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CriticalMarginMethodTest {

    private final JudgementSpec spec = Circuits.singleWindow();

    @Test
    void movesTheCriticalElementIntoTheMiddleOfItsWindow() {
        // Operating window 0.5..1.5, starting off-centre at 1.2.
        OptimizationOutcome outcome = method(0.5, 1.5).optimize(Circuits.singleResistor(1.2), spec);

        assertThat(outcome.netlist().element(0).value()).isCloseTo(1.0, within(0.02));
    }

    @Test
    void stopsOnceTheSameElementComesUpCriticalTwice() {
        OptimizationOutcome outcome = method(0.5, 1.5).optimize(Circuits.singleResistor(1.2), spec);

        // One element can only be centred once; a second pass would walk it back and forth.
        assertThat(outcome.reason()).isEqualTo(StopReason.SAME_CRITICAL_ELEMENT);
        assertThat(outcome.trials()).isEqualTo(1);
    }

    @Test
    void refusesToMoveAFixedElement() {
        Netlist fixed = Circuits.singleResistor(1.2);
        fixed = fixed.withElements(List.of(fixed.element(0).fixedValue()));

        OptimizationOutcome outcome = method(0.5, 1.5).optimize(fixed, spec);

        assertThat(outcome.reason()).isEqualTo(StopReason.CRITICAL_ELEMENT_IS_FIXED);
        assertThat(outcome.netlist().element(0).value()).isEqualTo(1.2);
    }

    @Test
    void keepsTheNewValueInsideTheElementRange() {
        Netlist narrowed = Circuits.singleResistor(1.2);
        narrowed = narrowed.withElements(List.of(narrowed.element(0).withRange(
                narrowed.element(0).range().withMin(1.15))));

        OptimizationOutcome outcome = method(0.5, 1.5).optimize(narrowed, spec);

        assertThat(outcome.netlist().element(0).value()).isEqualTo(1.15);
    }

    private CriticalMarginMethod method(double lower, double upper) {
        MarginTableCalculator margins = calculator(lower, upper);
        return new CriticalMarginMethod(margins, margins, new CriticalElementFinder());
    }

    /** Measures every element the way CalculateMarginUseCase does, only sequentially. */
    private MarginTableCalculator calculator(double lower, double upper) {
        OperationEvaluator evaluator =
                new OperationEvaluator(new WindowSimulator(0, lower, upper), new OperationJudge());
        MarginSearcher searcher = new BinarySearchMarginSearcher(evaluator);
        return (netlist, judgement) -> {
            List<ElementMargin> entries = new ArrayList<>();
            for (int index = 0; index < netlist.elementCount(); index++) {
                entries.add(new ElementMargin(netlist.element(index), searcher.search(netlist, index, judgement)));
            }
            return new MarginTable(entries);
        };
    }
}
