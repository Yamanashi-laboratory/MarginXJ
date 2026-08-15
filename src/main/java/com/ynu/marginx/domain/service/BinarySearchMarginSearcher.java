package com.ynu.marginx.domain.service;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.Margin;

/**
 * Bisects each side of the operating window, the behaviour of calc_margin_low/margin_ele_low.cpp.
 */
public final class BinarySearchMarginSearcher implements MarginSearcher {

    private static final int ITERATIONS = 10;

    private final OperationEvaluator evaluator;

    public BinarySearchMarginSearcher(OperationEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public Margin search(Netlist netlist, int elementIndex, JudgementSpec spec) {
        double base = netlist.element(elementIndex).value();
        if (base == 0) {
            return Margin.none();
        }

        double high = bisect(netlist, elementIndex, spec, base, 1);
        double low = bisect(netlist, elementIndex, spec, base, -1);

        if (base < 0) {
            double swap = high;
            high = low;
            low = swap;
            return new Margin(-(-(base - low) / base) * 100, -(high - base) / base * 100);
        }
        return new Margin(-(base - low) / base * 100, (high - base) / base * 100);
    }

    private double bisect(Netlist netlist, int index, JudgementSpec spec, double base, int direction) {
        double bound = base;
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            double step = base / Math.pow(2, iteration + 1);
            bound += operates(netlist, index, spec, bound) ? direction * step : -direction * step;
        }
        if (!operates(netlist, index, spec, bound)) {
            bound -= direction * base / Math.pow(2, ITERATIONS);
        }
        return bound;
    }

    private boolean operates(Netlist netlist, int index, JudgementSpec spec, double value) {
        return evaluator.operatesCorrectly(netlist.withElementValue(index, value), spec);
    }

    @Override
    public String description() {
        return "binary search (%d iterations)".formatted(ITERATIONS);
    }
}
