package com.ynu.marginx.presentation.gui.task;

import com.ynu.marginx.application.CalculateMarginUseCase;
import com.ynu.marginx.application.ProgressListener;
import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import java.time.Duration;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.concurrent.Task;

/**
 * Runs a margin calculation off the JavaFX thread and reports back on it.
 *
 * <p>Results are handed over one element at a time as they finish, so the table fills while the
 * run is still going. The listener is called from the worker threads inside the use case, which is
 * why everything that touches the UI is bounced through {@link Platform#runLater}.
 */
public final class MarginCalculationTask extends Task<MarginTable> {

    private static final Duration CLEANUP_GRACE = Duration.ofSeconds(10);

    private final CalculateMarginUseCase useCase;
    private final Netlist netlist;
    private final JudgementSpec spec;
    private final Consumer<ElementMargin> onElementCompleted;

    public MarginCalculationTask(CalculateMarginUseCase useCase, Netlist netlist, JudgementSpec spec,
                                 Consumer<ElementMargin> onElementCompleted) {
        this.useCase = useCase;
        this.netlist = netlist;
        this.spec = spec;
        this.onElementCompleted = onElementCompleted;
    }

    @Override
    protected MarginTable call() {
        return useCase.execute(netlist, spec, new ProgressListener() {
            @Override
            public void started(int total) {
                updateProgress(0, total);
                updateMessage("Measuring " + total + " elements...");
            }

            @Override
            public void elementStarted(int index, String elementName) {
                updateMessage("Measuring " + elementName + "...");
            }

            @Override
            public void elementCompleted(int index, ElementMargin result) {
                Platform.runLater(() -> onElementCompleted.accept(result));
            }

            @Override
            public void advanced(int completed, int total) {
                updateProgress(completed, total);
            }

            @Override
            public void finished() {
                updateMessage("Done.");
            }
        });
    }

    /**
     * Task.cancel() only sets a flag and interrupts the thread running call(); the pool doing the
     * actual simulating knows nothing about it. Telling the use case is what stops the simulators
     * and gets their working directories deleted.
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
