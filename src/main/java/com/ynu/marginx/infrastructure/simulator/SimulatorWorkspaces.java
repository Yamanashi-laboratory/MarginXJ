package com.ynu.marginx.infrastructure.simulator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The temporary directories the simulators are working in right now.
 *
 * <p>Each run deletes its own on the way out, cancelled or not, because the worker thread unwinds
 * through a finally block. That does not hold when the JVM is told to exit: those threads may
 * never run again, and what is left behind is a directory per simulation still in flight. A
 * shutdown hook calls {@link #deleteRemaining()} to cover that case.
 */
public final class SimulatorWorkspaces {

    private static final Set<Path> OPEN = ConcurrentHashMap.newKeySet();

    static {
        // Registered here rather than left to each entry point, so a directory is swept up however
        // the JVM ends - the CLI, the window, a test worker. A run that unwinds normally has
        // already deleted its own, and this finds nothing.
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(SimulatorWorkspaces::deleteRemaining,
                    "marginx-workspace-cleanup"));
        } catch (IllegalStateException alreadyShuttingDown) {
            // The class was first loaded from another shutdown hook, which happens when the user
            // interrupts before the first simulation. There is nothing open to clean up, and
            // failing to initialise here would take that hook down with it.
        }
    }

    private SimulatorWorkspaces() {
    }

    static void opened(Path directory) {
        OPEN.add(directory);
    }

    static void closed(Path directory) {
        OPEN.remove(directory);
    }

    public static void deleteRemaining() {
        for (Path directory : Set.copyOf(OPEN)) {
            delete(directory);
            OPEN.remove(directory);
        }
    }

    /** Counts what is still open, so a test can assert that a cancelled run left nothing. */
    public static int openCount() {
        return OPEN.size();
    }

    private static final int DELETE_ATTEMPTS = 20;
    private static final long DELETE_PAUSE_MILLIS = 100;

    /**
     * Deletes a working directory, retrying briefly.
     *
     * <p>One attempt is not enough on Windows. Killing a simulator does not release its files at
     * the same instant: the process is gone, but the handle its error log was redirected to can
     * outlive it by a moment, and a single pass then deletes the netlist, fails on the log and
     * leaves the directory behind.
     *
     * <p>The interrupt is set aside while this runs and restored afterwards. This is called as a
     * cancelled run unwinds, so the flag is already set, and a retry that sleeps would be thrown
     * out of on its first pause - cancelling would be the very thing that left the litter.
     */
    static void delete(Path directory) {
        boolean interrupted = Thread.interrupted();
        try {
            for (int attempt = 0; attempt < DELETE_ATTEMPTS; attempt++) {
                deleteOnce(directory);
                if (!Files.exists(directory)) {
                    return;
                }
                Thread.sleep(DELETE_PAUSE_MILLIS);
            }
        } catch (InterruptedException e) {
            interrupted = true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void deleteOnce(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Retried by the caller; a leftover temp file must not fail the margin run.
                }
            });
        } catch (IOException ignored) {
            // Same reasoning: cleanup is best effort.
        }
    }
}
