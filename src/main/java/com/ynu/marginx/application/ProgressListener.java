package com.ynu.marginx.application;

import com.ynu.marginx.domain.model.margin.ElementMargin;

/**
 * Follows a margin calculation as it happens.
 *
 * <p>Every method is a no-op by default, so a listener implements only the events it cares about:
 * a terminal progress bar wants the counts, a GUI wants the per-element events so it can fill a
 * table row by row instead of waiting for the whole run.
 *
 * <p>The per-element events arrive from the worker threads that ran the search, in whatever order
 * the elements finish. An implementation has to be safe to call from several threads, and a GUI
 * has to hand the event to its own thread before touching any widget.
 */
public interface ProgressListener {

    ProgressListener NOOP = new ProgressListener() {
    };

    /** Called once, before any element is searched. */
    default void started(int total) {
    }

    /** The search for one element is about to begin. */
    default void elementStarted(int index, String elementName) {
    }

    /** One element is done, with the margin that was measured for it. */
    default void elementCompleted(int index, ElementMargin result) {
    }

    default void advanced(int completed, int total) {
    }

    /** Called once when every element is done. Not called when a run is cancelled. */
    default void finished() {
    }
}
