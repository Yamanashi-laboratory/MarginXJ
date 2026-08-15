package com.ynu.marginx.infrastructure.simulator;

import com.ynu.marginx.shared.exception.SimulationFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ProcessExecutor {

    static final String ERROR_LOG = "simulator-error.log";

    private static final int REPORTED_ERROR_LINES = 10;

    public void run(List<String> command, Path workingDirectory, Duration timeout) {
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

        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new SimulationFailedException(
                        "Simulator did not finish within " + timeout.toSeconds() + "s: " + String.join(" ", command));
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new SimulationFailedException("Interrupted while waiting for the simulator", e);
        }

        if (process.exitValue() != 0) {
            throw new SimulationFailedException("Simulator exited with " + process.exitValue() + ": "
                    + String.join(" ", command) + diagnostics(errorLog));
        }
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
