package com.ynu.marginx.domain.port;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.SimulationResult;

public interface CircuitSimulator {

    SimulationResult simulate(Netlist netlist);

    /** The command this adapter runs. */
    String name();

    /** What to call this simulator when talking to a person. */
    default String displayName() {
        return name();
    }

    /**
     * Whether this simulator can actually be run. Adapters answer from a resolved location rather
     * than from the operating system, and hold on to the answer: a caller may ask once per element.
     */
    default boolean isAvailable() {
        return true;
    }

    /** Why {@link #isAvailable()} is false, in a sentence fit for a tooltip; empty when available. */
    default String unavailableReason() {
        return "";
    }
}
