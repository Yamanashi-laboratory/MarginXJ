package com.ynu.marginx.infrastructure.simulator;

import com.ynu.marginx.shared.exception.CalculationCancelledException;
import com.ynu.marginx.shared.exception.SimulationFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

public final class ProcessExecutor {

    static final String ERROR_LOG = "simulator-error.log";

    private static final int REPORTED_ERROR_LINES = 10;
    private static final int REAP_TIMEOUT_SECONDS = 10;

    /** The simulators running right now, so a shutdown hook can put them down. */
    private final Set<Process> live = ConcurrentHashMap.newKeySet();

    public void run(List<String> command, Path workingDirectory, Duration timeout) {
        // Cheaper than starting a process only to kill it, and it keeps a cancelled run from
        // spawning one more simulator per element on its way out.
        if (Thread.currentThread().isInterrupted()) {
            throw new CalculationCancelledException("Cancelled before starting " + String.join(" ", command));
        }
        Path errorLog = workingDirectory.resolve(ERROR_LOG);
        Process process;
        try {
            // Both streams are redirected: nothing drains a pipe here, so a chatty simulator
            // would otherwise fill the buffer and block forever.
            process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(errorLog.toFile())
                    .start();
        } catch (IOException e) {
            throw new SimulationFailedException("Cannot start " + String.join(" ", command), e);
        }

        live.add(process);
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                reap(process);
                throw new SimulationFailedException(
                        "Simulator did not finish within " + timeout.toSeconds() + "s: " + String.join(" ", command));
            }
        } catch (InterruptedException e) {
            // Kill it and wait for it to actually be gone before unwinding. The caller deletes the
            // working directory on the way out, and on Windows that fails silently while the child
            // still holds the netlist or the CSV open - which is how a cancelled run leaves litter.
            reap(process);
            Thread.currentThread().interrupt();
            throw new CalculationCancelledException("Cancelled while waiting for the simulator", e);
        } finally {
            live.remove(process);
        }

        if (process.exitValue() != 0) {
            throw new SimulationFailedException("Simulator exited with " + process.exitValue() + ": "
                    + String.join(" ", command) + diagnostics(errorLog));
        }
    }

    /**
     * Kills a process and waits for the operating system to finish with it, so its files are
     * released. Called with the interrupt flag still clear: {@code waitFor} on an interrupted
     * thread returns immediately and would defeat the point.
     */
    private void reap(Process process) {
        // Kill the whole tree, not just the child. A command may well be a wrapper script, and on
        // Windows that means cmd.exe with the simulator underneath it; destroying only cmd.exe
        // leaves the simulator running with the netlist and the error log still open, which is
        // what stops the working directory from being deleted afterwards.
        List<ProcessHandle> descendants = process.descendants().toList();
        process.destroyForcibly();
        descendants.forEach(ProcessHandle::destroyForcibly);
        try {
            process.waitFor(REAP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (ProcessHandle descendant : descendants) {
                awaitExit(descendant);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitExit(ProcessHandle handle) throws InterruptedException {
        try {
            handle.onExit().get(REAP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            // Already gone, or refusing to die; either way there is nothing further to wait for.
        }
    }

    /**
     * Destroys every simulator still running, for a shutdown hook: on Ctrl+C the threads that would
     * normally clean up are not guaranteed to run again.
     */
    public void destroyLiveProcesses() {
        for (Process process : live) {
            reap(process);
        }
        live.clear();
    }

    private String diagnostics(Path errorLog) {
        try {
            if (!Files.isRegularFile(errorLog)) {
                return "";
            }
            List<String> lines = Files.readAllLines(errorLog);
            if (lines.isEmpty()) {
                return "";
            }
            List<String> tail = lines.subList(Math.max(0, lines.size() - REPORTED_ERROR_LINES), lines.size());
            return System.lineSeparator() + String.join(System.lineSeparator(), tail);
        } catch (IOException e) {
            return "";
        }
    }
}
