package com.ynu.marginx.domain.model.optimize;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.margin.MarginTable;

/**
 * What an optimisation run ended up with: the circuit as it now stands, its margins, and why the
 * run stopped. The C++ tool printed the reason and threw the rest away; keeping it makes the
 * outcome testable and lets the caller write the optimised netlist out.
 */
public record OptimizationOutcome(Netlist netlist, MarginTable margins, int trials, StopReason reason) {

    public enum StopReason {
        /** Ran out of trials - CRITICAL_NUM in the C++ tool. */
        TRIALS_EXHAUSTED,
        /** The same element came up critical twice running, so moving it is not helping. */
        SAME_CRITICAL_ELEMENT,
        /** The critical element is marked *FIX and must not be moved. */
        CRITICAL_ELEMENT_IS_FIXED,
        /** No element could be measured at all. */
        NOTHING_TO_OPTIMIZE,
        /** The yield stopped improving for long enough that the run gave up - not_upd in the C++. */
        YIELD_STALLED
    }
}
