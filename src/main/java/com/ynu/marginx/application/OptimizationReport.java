package com.ynu.marginx.application;

import com.ynu.marginx.domain.model.optimize.OptimizationOutcome.StopReason;

/**
 * Why an optimisation stopped, in words.
 *
 * <p>Both front ends print the same sentence, and it is the part of the outcome a user actually
 * reads: "the yield stopped improving" and "the trial limit was reached" mean quite different
 * things about whether the circuit is finished.
 */
public final class OptimizationReport {

    private OptimizationReport() {
    }

    public static String explain(StopReason reason) {
        return switch (reason) {
            case SAME_CRITICAL_ELEMENT -> "the same element came up critical again";
            case CRITICAL_ELEMENT_IS_FIXED -> "the critical element is marked *FIX";
            case NOTHING_TO_OPTIMIZE -> "no element could be measured";
            case YIELD_STALLED -> "the yield stopped improving";
            case TRIALS_EXHAUSTED -> "the trial limit was reached";
        };
    }
}
