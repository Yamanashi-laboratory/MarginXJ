package com.ynu.marginx.domain.service;

import com.ynu.marginx.domain.model.circuit.CircuitElement;
import com.ynu.marginx.domain.model.circuit.ElementType;
import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.Margin;

/**
 * Widens the parameter one decade at a time and refines the step four times, the behaviour of
 * calc_margin/margin_ele.cpp.
 *
 * <p>In synchronised mode the candidate value is applied to the target's whole synchronisation
 * group instead of the target alone. That is the only difference between margin_ele.cpp and
 * margin_ele_syn.cpp, which is why this is a mode rather than a second algorithm.
 */
public final class ExhaustiveMarginSearcher implements MarginSearcher {

    private static final int REFINEMENT_STEPS = 4;
    private static final double MARGIN_UPPER = 2;
    private static final double LOWER_FLOOR = 0.001;

    private final OperationEvaluator evaluator;
    private final boolean synchronizeGroups;

    public ExhaustiveMarginSearcher(OperationEvaluator evaluator) {
        this(evaluator, false);
    }

    private ExhaustiveMarginSearcher(OperationEvaluator evaluator, boolean synchronizeGroups) {
        this.evaluator = evaluator;
        this.synchronizeGroups = synchronizeGroups;
    }

    /** The searcher behind menu option 4, Margin_syn in the C++ tool. */
    public static ExhaustiveMarginSearcher synchronizingGroups(OperationEvaluator evaluator) {
        return new ExhaustiveMarginSearcher(evaluator, true);
    }

    @Override
    public Margin search(Netlist netlist, int elementIndex, JudgementSpec spec) {
        CircuitElement element = netlist.element(elementIndex);
        double base = element.value();
        if (base == 0) {
            return Margin.none();
        }
        int order = orderOfMagnitude(base);
        boolean coupling = element.type() == ElementType.COUPLING;

        return base > 0
                ? searchPositive(netlist, elementIndex, spec, base, order, coupling)
                : searchNegative(netlist, elementIndex, spec, base, order, coupling);
    }

    private Margin searchPositive(Netlist netlist, int index, JudgementSpec spec,
                                  double base, int order, boolean coupling) {
        double ceiling = base * MARGIN_UPPER;
        double high = base;
        int check = 0;
        while (check < REFINEMENT_STEPS) {
            high += Math.pow(10, order - check);
            if (coupling && high > 1) {
                high -= Math.pow(10, order - check);
                check++;
            }
            if (!operates(netlist, index, spec, high)) {
                high -= Math.pow(10, order - check);
                check++;
            } else if (high > ceiling) {
                high = ceiling;
                break;
            }
        }

        double low = base;
        check = 0;
        while (check < REFINEMENT_STEPS) {
            low -= Math.pow(10, order - check);
            if (low < LOWER_FLOOR) {
                low += Math.pow(10, order - check);
                check++;
            }
            if (!operates(netlist, index, spec, low)) {
                low += Math.pow(10, order - check);
                check++;
            }
        }
        return new Margin(-(base - low) / base * 100, (high - base) / base * 100);
    }

    private Margin searchNegative(Netlist netlist, int index, JudgementSpec spec,
                                  double base, int order, boolean coupling) {
        double high = base;
        int check = 0;
        while (check < REFINEMENT_STEPS) {
            high += Math.pow(10, order - check);
            if (high > 0) {
                high -= Math.pow(10, order - check);
                check++;
            }
            if (!operates(netlist, index, spec, high)) {
                high -= Math.pow(10, order - check);
                check++;
            }
        }

        double floor = base * MARGIN_UPPER;
        double low = base;
        check = 0;
        while (check < REFINEMENT_STEPS) {
            low -= Math.pow(10, order - check);
            if (coupling && low < -1) {
                low += Math.pow(10, order - check);
                check++;
            }
            if (!operates(netlist, index, spec, low)) {
                low += Math.pow(10, order - check);
                check++;
            } else if (low < floor) {
                low = floor;
                break;
            }
        }
        return new Margin(-(-(base - low) / base) * 100, -(high - base) / base * 100);
    }

    private boolean operates(Netlist netlist, int index, JudgementSpec spec, double value) {
        Netlist candidate = synchronizeGroups
                ? netlist.withSynchronizedValue(index, value)
                : netlist.withElementValue(index, value);
        return evaluator.operatesCorrectly(candidate, spec);
    }

    static int orderOfMagnitude(double value) {
        return value == 0 ? 0 : (int) Math.floor(Math.log10(Math.abs(value)));
    }

    @Override
    public String description() {
        return synchronizeGroups
                ? "exhaustive (decade refinement, synchronised groups)"
                : "exhaustive (decade refinement)";
    }
}
