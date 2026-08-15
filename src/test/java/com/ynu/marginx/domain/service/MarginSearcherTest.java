package com.ynu.marginx.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.Margin;
import com.ynu.marginx.testsupport.Circuits;
import com.ynu.marginx.testsupport.WindowSimulator;
import org.junit.jupiter.api.Test;

class MarginSearcherTest {

    private final JudgementSpec spec = Circuits.singleWindow();

    @Test
    void exhaustiveSearchFindsTheOperatingWindow() {
        Margin margin = search(new ExhaustiveMarginSearcher(evaluator(0.5, 1.5)), Circuits.singleResistor(1.0));

        assertThat(margin.lowerPercent()).isCloseTo(-50, within(1.0));
        assertThat(margin.upperPercent()).isCloseTo(50, within(1.0));
    }

    @Test
    void binarySearchFindsTheOperatingWindow() {
        Margin margin = search(new BinarySearchMarginSearcher(evaluator(0.5, 1.5)), Circuits.singleResistor(1.0));

        assertThat(margin.lowerPercent()).isCloseTo(-50, within(1.0));
        assertThat(margin.upperPercent()).isCloseTo(50, within(1.0));
    }

    @Test
    void binarySearchHandlesAnAsymmetricWindow() {
        Margin margin = search(new BinarySearchMarginSearcher(evaluator(0.9, 1.8)), Circuits.singleResistor(1.0));

        assertThat(margin.lowerPercent()).isCloseTo(-10, within(1.0));
        assertThat(margin.upperPercent()).isCloseTo(80, within(1.0));
    }

    @Test
    void exhaustiveSearchStopsAtTwiceTheNominalValue() {
        Margin margin = search(new ExhaustiveMarginSearcher(evaluator(0.5, 10.0)), Circuits.singleResistor(1.0));

        assertThat(margin.upperPercent()).isCloseTo(100, within(0.001));
    }

    @Test
    void aZeroValuedElementHasNoMargin() {
        Margin margin = search(new BinarySearchMarginSearcher(evaluator(0.5, 1.5)), Circuits.singleResistor(0.0));

        assertThat(margin).isEqualTo(Margin.none());
    }

    private Margin search(MarginSearcher searcher, Netlist netlist) {
        return searcher.search(netlist, 0, spec);
    }

    private OperationEvaluator evaluator(double lower, double upper) {
        return new OperationEvaluator(new WindowSimulator(0, lower, upper), new OperationJudge());
    }
}
