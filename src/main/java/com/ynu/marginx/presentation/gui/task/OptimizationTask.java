package com.ynu.marginx.presentation.gui.task;

import com.ynu.marginx.application.OptimizationProgressListener;
import com.ynu.marginx.application.OptimizeCircuitUseCase;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.model.optimize.OptimizationOutcome;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.domain.port.NetlistRepository;
import java.time.Duration;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.concurrent.Task;

/**
 * Runs an optimisation off the JavaFX thread and reports back on it.
 *
 * <p>An optimisation at the default settings is fifty thousand simulations, so the window has to
 * show that something is happening and be able to stop it. Every re-measurement is handed over as
 * it finishes, which is what lets the chart follow the circuit as it moves rather than jumping
 * from the original to the final answer.
 *
 * <p>The use case is built here rather than passed in because it is both the listener's subject and
 * the source of the collaborators the optimiser needs; {@link Optimisation} is where the caller
 * says which optimiser to build out of them.
 */
public final class OptimizationTask extends Task<OptimizationOutcome> {

    private static final Duration CLEANUP_GRACE = Duration.ofSeconds(10);

    /** Builds and runs an optimiser out of the collaborators the use case provides. */
    @FunctionalInterface
    public interface Optimisation {
        OptimizationOutcome run(OptimizeCircuitUseCase useCase);
    }

    private final OptimizeCircuitUseCase useCase;
    private final Optimisation optimisation;

    /**
     * @param totalCycles      the Monte Carlo cycle limit, or 0 for a method that has no cycles
     * @param totalMeasurements how many re-measurements the run can take, used for the progress of
     *                          a method with no cycles; 0 when it cannot be known in advance
     */
    public OptimizationTask(NetlistRepository netlists, MarginResultRepository results,
                            int totalCycles, int totalMeasurements,
                            Consumer<MarginTable> onMeasured, Optimisation optimisation) {
        this.optimisation = optimisation;
        this.useCase = new OptimizeCircuitUseCase(netlists, results, new OptimizationProgressListener() {

            @Override
            public void cycleStarted(int cycle, int total) {
                updateMessage("Cycle " + (cycle + 1) + " of at most " + total + "...");
                if (totalCycles > 0) {
                    updateProgress(cycle, totalCycles);
                }
            }

            @Override
            public void measurementStarted(int measurement) {
                updateMessage("Measuring every element (" + (measurement + 1) + ")...");
                if (totalCycles == 0 && totalMeasurements > 0) {
                    updateProgress(measurement, totalMeasurements);
                }
            }

            @Override
            public void measurementCompleted(int measurement, MarginTable table) {
                Platform.runLater(() -> onMeasured.accept(table));
            }
        });
    }

    @Override
    protected OptimizationOutcome call() {
        updateMessage("Starting...");
        return optimisation.run(useCase);
    }

    /**
     * Task.cancel() interrupts the thread running call(), which is not the thread doing the
     * simulating. Telling the use case is what stops the simulators and gets their working
     * directories deleted.
     */
    @Override
    protected void cancelled() {
        super.cancelled();
        useCase.cancel();
        updateMessage("Cancelled.");
    }

    /** Waits for the workers to finish unwinding, so a caller can start a new run safely. */
    public void awaitCleanup() {
        useCase.awaitTermination(CLEANUP_GRACE);
    }
}
