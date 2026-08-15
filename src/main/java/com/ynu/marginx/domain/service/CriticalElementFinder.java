package com.ynu.marginx.domain.service;

import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import java.util.Optional;
import java.util.function.Predicate;

public final class CriticalElementFinder {

    private static final double INITIAL_CRITICAL = 100;

    public Optional<ElementMargin> findCritical(MarginTable table) {
        return findTightest(table, entry -> !entry.element().type().isBiasSource());
    }

    public Optional<ElementMargin> findCriticalBias(MarginTable table) {
        return findTightest(table, entry -> entry.element().type().isBiasSource());
    }

    private Optional<ElementMargin> findTightest(MarginTable table, Predicate<ElementMargin> filter) {
        double critical = INITIAL_CRITICAL;
        ElementMargin tightest = null;
        for (ElementMargin entry : table.entries()) {
            if (!filter.test(entry)) {
                continue;
            }
            double candidate = entry.margin().criticalPercent();
            if (candidate < critical) {
                critical = candidate;
                tightest = entry;
            }
        }
        return Optional.ofNullable(tightest);
    }
}
