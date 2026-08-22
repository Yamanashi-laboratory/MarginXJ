package com.ynu.marginx.application;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationSampler;
import com.ynu.marginx.shared.exception.CalculationCancelledException;
import com.ynu.marginx.shared.exception.MarginXException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simulates a cycle of Monte Carlo candidates in parallel, the way optimize_yield_up.cpp forks one
 * process per trial. Without it an optimisation is a hundred simulations deep per cycle, one after
 * the other.
 *
 * <p>A cycle can be abandoned part-way with {@link #cancel()}. Waiting for a hundred simulations to
 * finish before an optimisation would notice it had been cancelled is the difference between a
 * cancel button that works and one that appears not to.
 */
public final class ParallelOperationSampler implements OperationSampler {

    private final OperationEvaluator evaluator;
    private final AtomicReference<ExecutorService> running = new AtomicReference<>();
    private final AtomicReference<List<CompletableFuture<Boolean>>> outstanding = new AtomicReference<>();
    private volatile boolean cancelled;

    public ParallelOperationSampler(OperationEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public boolean[] sample(List<Netlist> candidates, JudgementSpec spec) {
        boolean[] operating = new boolean[candidates.size()];
        if (candidates.isEmpty()) {
            return operating;
        }
        stopIfCancelled();
        try (ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(candidates.size(), Runtime.getRuntime().availableProcessors()))) {
            running.set(pool);
            List<CompletableFuture<Boolean>> futures = new ArrayList<>(candidates.size());
            outstanding.set(futures);
            try {
                for (Netlist candidate : candidates) {
                    futures.add(CompletableFuture.supplyAsync(
                            () -> evaluator.operatesCorrectly(candidate, spec), pool));
                }
            } catch (RejectedExecutionException e) {
                // cancel() shut the pool down while the trials were still being handed to it.
                throw new CalculationCancelledException("The optimisation was cancelled", e);
            }
            for (int index = 0; index < futures.size(); index++) {
                operating[index] = join(futures.get(index));
            }
        } finally {
            outstanding.set(null);
        }
        return operating;
    }

    /**
     * Abandons the cycle now in progress. The trials already running are interrupted, which is what
     * kills their simulators and deletes the directories they were working in.
     */
    public void cancel() {
        cancelled = true;
        ExecutorService pool = running.get();
        if (pool != null) {
            pool.shutdownNow();
        }
        // shutdownNow() discards what was still queued without completing it, so those trials would
        // never resolve and sample() would wait for them forever.
        List<CompletableFuture<Boolean>> futures = outstanding.get();
        if (futures != null) {
            CalculationCancelledException cancellation =
                    new CalculationCancelledException("The optimisation was cancelled");
            for (CompletableFuture<Boolean> future : List.copyOf(futures)) {
                future.completeExceptionally(cancellation);
            }
        }
    }

    private void stopIfCancelled() {
        if (cancelled) {
            throw new CalculationCancelledException("The optimisation was cancelled");
        }
    }

    private boolean join(CompletableFuture<Boolean> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CalculationCancelledException("Interrupted while sampling the circuit", e);
        } catch (ExecutionException e) {
            stopIfCancelled();
            if (e.getCause() instanceof MarginXException cause) {
                throw cause;
            }
            throw new MarginXException("A Monte Carlo trial failed", e.getCause());
        }
    }
}
