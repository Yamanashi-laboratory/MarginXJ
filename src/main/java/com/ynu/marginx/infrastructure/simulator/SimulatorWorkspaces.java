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

    static void delete(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A leftover temp file must not fail the margin run.
                }
            });
        } catch (IOException ignored) {
            // Same reasoning: cleanup is best effort.
        }
    }
}
