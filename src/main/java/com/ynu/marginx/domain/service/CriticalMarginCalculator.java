package com.ynu.marginx.domain.service;

import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * The four critical-margin readings the score is built from: calc_critical.cpp and its siblings.
 *
 * <p>All of them start at 100 and keep the smallest value they see, so a table with nothing to
 * measure reads as 100 rather than as zero - the C++ behaviour, and the reason an optimiser cannot
 * mistake "no elements of this kind" for "no margin at all".
 */
public final class CriticalMarginCalculator {

    private static final double NO_MEASUREMENT = 100;

    /** The tightest margin among the elements that are not bias sources. */
    public double critical(MarginTable table) {
        return tightest(table, entry -> !entry.element().type().isBiasSource(),
                entry -> entry.margin().criticalPercent());
    }

    /** The same, over the bias sources alone. */
    public double criticalBias(MarginTable table) {
        return tightest(table, entry -> entry.element().type().isBiasSource(),
                entry -> entry.margin().criticalPercent());
    }

    public double criticalUpper(MarginTable table) {
        return tightest(table, entry -> !entry.element().type().isBiasSource(),
                entry -> entry.margin().upperPercent());
    }

    public double criticalLower(MarginTable table) {
        return tightest(table, entry -> !entry.element().type().isBiasSource(),
                entry -> -entry.margin().lowerPercent());
    }

    private double tightest(MarginTable table, Predicate<ElementMargin> filter,
                            ToDoubleFunction<ElementMargin> reading) {
        double critical = NO_MEASUREMENT;
        for (ElementMargin entry : table.entries()) {
            if (filter.test(entry)) {
                critical = Math.min(critical, reading.applyAsDouble(entry));
            }
        }
        return critical;
    }
}
