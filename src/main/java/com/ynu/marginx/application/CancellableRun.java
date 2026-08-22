package com.ynu.marginx.application;

import java.time.Duration;

/**
 * A long run that something outside it can stop.
 *
 * <p>Both front ends need this and neither cares which kind of run it is holding: Ctrl+C and the
 * cancel button do the same two things - stop it, then wait for the workers to kill their
 * simulators and delete their working directories before the process goes away.
 */
public interface CancellableRun {

    /**
     * Stops the run. Returns straight away, because the caller is usually the thread handling a
     * button press or a shutdown hook.
     */
    void cancel();

    /** Waits for the workers to finish unwinding. False if they were still going at the timeout. */
    boolean awaitTermination(Duration timeout);
}
