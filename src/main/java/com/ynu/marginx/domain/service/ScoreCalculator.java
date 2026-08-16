package com.ynu.marginx.domain.service;

import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.model.optimize.ScoreWeights;

/** calc_score.cpp: the weighted sum of the four critical-margin readings. */
public final class ScoreCalculator {

    private final CriticalMarginCalculator criticalMargins;

    public ScoreCalculator(CriticalMarginCalculator criticalMargins) {
        this.criticalMargins = criticalMargins;
    }

    public double score(MarginTable table, ScoreWeights weights) {
        return weights.critical() * criticalMargins.critical(table)
                + weights.bias() * criticalMargins.criticalBias(table)
                + weights.upper() * criticalMargins.criticalUpper(table)
                + weights.lower() * criticalMargins.criticalLower(table);
    }
}
