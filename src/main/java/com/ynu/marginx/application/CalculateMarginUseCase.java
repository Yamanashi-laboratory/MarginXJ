package com.ynu.marginx.application;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.Margin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.domain.service.MarginSearcher;
import com.ynu.marginx.shared.exception.CalculationCancelledException;
import com.ynu.marginx.shared.exception.MarginXException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Measures every element, one worker per processor.
 *
 * <p>A run can be stopped from another thread with {@link #cancel()}, which is what a GUI needs:
 * a full margin sweep is minutes of simulation and the user has to be able to change their mind.
 * The pool is therefore shut down explicitly rather than with try-with-resources, whose close()
 * waits for every task to finish - exactly what cancelling is trying to avoid.
 */
public final class CalculateMarginUseCase {

    private final MarginSearcher searcher;
    private final MarginResultRepository resultRepository;
    /** The most recent pool. Kept after the run so awaitTermination() can still wait on it. */
    private final AtomicReference<ExecutorService> running = new AtomicReference<>();
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicReference<List<CompletableFuture<Margin>>> outstanding = new AtomicReference<>();
    private volatile boolean cancelled;

    public CalculateMarginUseCase(MarginSearcher searcher, MarginResultRepository resultRepository) {
        this.searcher = searcher;
        this.resultRepository = resultRepository;
    }

    public MarginTable execute(Netlist netlist, JudgementSpec spec, ProgressListener listener) {
        int total = netlist.elementCount();
        listener.started(total);
        AtomicInteger completed = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(total, Runtime.getRuntime().availableProcessors()));
        if (!inFlight.compareAndSet(false, true)) {
            pool.shutdownNow();
            throw new MarginXException("This calculation is already running");
        }
        running.set(pool);

        List<CompletableFuture<Margin>> futures = new ArrayList<>(total);
        outstanding.set(futures);

        List<Margin> margins;
        try {
            for (int index = 0; index < total; index++) {
                int elementIndex = index;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    listener.elementStarted(elementIndex, netlist.element(elementIndex).displayName());
                    Margin margin = searcher.search(netlist, elementIndex, spec);
                    listener.elementCompleted(elementIndex,
                            new ElementMargin(netlist.element(elementIndex), margin));
                    return margin;
                }, pool).whenComplete((margin, error) -> listener.advanced(completed.incrementAndGet(), total)));
            }
            margins = join(futures);
        } catch (RejectedExecutionException e) {
            // cancel() shut the pool down while the tasks were still being handed to it.
            throw new CalculationCancelledException("Margin calculation was cancelled", e);
        } finally {
            // shutdown(), not shutdownNow(): on the ordinary path the work is already done, and on
            // the cancelled path cancel() has interrupted the workers itself.
            // execute() returns as soon as the futures resolve, but a cancelled worker is still
            // killing its simulator and deleting its working directory. Leaving the pool in place
            // is what lets awaitTermination() wait for that to finish.
            pool.shutdown();
            inFlight.set(false);
            outstanding.set(null);
        }
        listener.finished();

        List<ElementMargin> entries = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            entries.add(new ElementMargin(netlist.element(index), margins.get(index)));
        }
        MarginTable table = new MarginTable(entries);
        resultRepository.save(netlist.baseName(), table);
        return table;
    }

    /**
     * Stops a run in progress. Returns straight away - a GUI calls this from the thread handling
     * the button - and interrupts the workers, which unwind through the simulator adapter and let
     * it kill the external process and delete its working directory.
     */
    public void cancel() {
        cancelled = true;
        ExecutorService pool = running.get();
        if (pool != null) {
            pool.shutdownNow();
        }
        // shutdownNow() interrupts what is running but simply discards what was still queued, so
        // those futures would never complete and the caller would wait for them forever. Finishing
        // them here is what actually ends the run. Already-completed futures ignore this.
        List<CompletableFuture<Margin>> futures = outstanding.get();
        if (futures != null) {
            CalculationCancelledException cancellation =
                    new CalculationCancelledException("Margin calculation was cancelled");
            for (CompletableFuture<Margin> future : List.copyOf(futures)) {
                future.completeExceptionally(cancellation);
            }
        }
    }

    /** Waits for the workers to finish unwinding, for a caller that is about to exit the JVM. */
    public boolean awaitTermination(Duration timeout) {
        ExecutorService pool = running.get();
        if (pool == null) {
            return true;
        }
        try {
            return pool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private List<Margin> join(List<CompletableFuture<Margin>> futures) {
        List<Margin> margins = new ArrayList<>(futures.size());
        for (CompletableFuture<Margin> future : futures) {
            try {
                margins.add(future.get());
            } catch (ExecutionException e) {
                if (cancelled) {
                    throw new CalculationCancelledException("Margin calculation was cancelled", e.getCause());
                }
                throw e.getCause() instanceof MarginXException cause
                        ? cause
                        : new MarginXException("Margin search failed", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CalculationCancelledException("Interrupted while calculating margins", e);
            }
        }
        return margins;
    }
}
