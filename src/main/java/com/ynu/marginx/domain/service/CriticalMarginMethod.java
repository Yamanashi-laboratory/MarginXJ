package com.ynu.marginx.domain.service;

import com.ynu.marginx.domain.model.circuit.CircuitElement;
import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.Margin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.model.optimize.OptimizationOutcome;
import com.ynu.marginx.domain.model.optimize.OptimizationOutcome.StopReason;
import java.util.Optional;

/**
 * critical_margin_method.cpp: repeatedly move whichever element has the tightest margin into the
 * middle of its own operating window, and measure again.
 *
 * <p>The C++ version measures once with the exhaustive search and then re-measures with the binary
 * search after every step; the two searchers are passed in separately here for that reason.
 */
public final class CriticalMarginMethod {

    /** CRITICAL_NUM. */
    private static final int MAX_TRIALS = 10;

    private final MarginTableCalculator initialMargins;
    private final MarginTableCalculator refinedMargins;
    private final CriticalElementFinder criticalElements;

    public CriticalMarginMethod(MarginTableCalculator initialMargins, MarginTableCalculator refinedMargins,
                                CriticalElementFinder criticalElements) {
        this.initialMargins = initialMargins;
        this.refinedMargins = refinedMargins;
        this.criticalElements = criticalElements;
    }

    public OptimizationOutcome optimize(Netlist netlist, JudgementSpec spec) {
        Netlist current = netlist;
        MarginTable margins = initialMargins.calculate(current, spec);
        int previousCritical = -1;

        for (int trial = 0; trial < MAX_TRIALS; trial++) {
            Optional<ElementMargin> critical = criticalElements.findCritical(margins);
            if (critical.isEmpty()) {
                return new OptimizationOutcome(current, margins, trial, StopReason.NOTHING_TO_OPTIMIZE);
            }
            int index = indexOf(margins, critical.get());
            if (index == previousCritical) {
                // Moving it again would only walk the same element back and forth.
                return new OptimizationOutcome(current, margins, trial, StopReason.SAME_CRITICAL_ELEMENT);
            }
            if (critical.get().element().fixed()) {
                return new OptimizationOutcome(current, margins, trial, StopReason.CRITICAL_ELEMENT_IS_FIXED);
            }
            previousCritical = index;

            double centred = centre(critical.get());
            // synchro_opt(): the group follows the element that was just moved.
            current = current.withSynchronizedValue(index, centred);
            margins = refinedMargins.calculate(current, spec);
        }
        return new OptimizationOutcome(current, margins, MAX_TRIALS, StopReason.TRIALS_EXHAUSTED);
    }

    /**
     * The centre of the operating window, clamped to the element's declared range.
     *
     * <p>A negative element is moved by subtracting the same correction a positive one has added,
     * which walks it the other way; that asymmetry is what make_cir.cpp's callers do, so the port
     * keeps it rather than treating it as a sign error.
     */
    private double centre(ElementMargin entry) {
        CircuitElement element = entry.element();
        Margin margin = entry.margin();
        double correction = (margin.upperPercent() + margin.lowerPercent()) / 200 * element.value();
        double moved = element.value() > 0 ? element.value() + correction : element.value() - correction;
        return element.range().clamp(moved);
    }

    private int indexOf(MarginTable margins, ElementMargin entry) {
        for (int index = 0; index < margins.size(); index++) {
            if (margins.get(index) == entry) {
                return index;
            }
        }
        throw new IllegalStateException("The critical element is not part of the table it came from");
    }
}
