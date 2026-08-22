package com.ynu.marginx.application;

import com.ynu.marginx.domain.model.margin.MarginTable;

/**
 * Follows an optimisation as it happens.
 *
 * <p>An optimisation is a long run of two repeating pieces of work - a cycle of Monte Carlo trials,
 * and a re-measurement of every element - and neither optimiser reports anything about them. It
 * does not have to: both pieces reach the optimiser as collaborators the application layer hands
 * in, so counting them from the outside costs the domain nothing.
 *
 * <p>Every method is a no-op by default. The calls arrive on whichever thread is driving the
 * optimisation, so a GUI has to hand them to its own thread before touching a widget.
 */
public interface OptimizationProgressListener {

    OptimizationProgressListener NOOP = new OptimizationProgressListener() {
    };

    /**
     * A cycle of Monte Carlo trials is about to be simulated.
     *
     * <p>Only the Center of Gravity Method has cycles; the Critical Margin Method never calls this.
     *
     * @param cycle       zero-based, counting from the start of the run
     * @param totalCycles the limit the run would reach if it never stalled
     */
    default void cycleStarted(int cycle, int totalCycles) {
    }

    /** Every element is about to be measured again. Zero-based, over the whole run. */
    default void measurementStarted(int measurement) {
    }

    /**
     * A re-measurement finished. The table is the circuit as it now stands, which is what a window
     * plots while the run continues - and the last thing worth plotting if the run is cancelled.
     */
    default void measurementCompleted(int measurement, MarginTable table) {
    }
}
