package com.ynu.marginx.application;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationSampler;
import com.ynu.marginx.shared.exception.MarginXException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simulates a cycle of Monte Carlo candidates in parallel, the way optimize_yield_up.cpp forks one
 * process per trial. Without it an optimisation is a hundred simulations deep per cycle, one after
 * the other.
 */
public final class ParallelOperationSampler implements OperationSampler {

    private final OperationEvaluator evaluator;

    public ParallelOperationSampler(OperationEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public boolean[] sample(List<Netlist> candidates, JudgementSpec spec) {
        boolean[] operating = new boolean[candidates.size()];
        if (candidates.isEmpty()) {
            return operating;
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(candidates.size(), Runtime.getRuntime().availableProcessors()))) {
            List<CompletableFuture<Boolean>> futures = new ArrayList<>(candidates.size());
            for (Netlist candidate : candidates) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> evaluator.operatesCorrectly(candidate, spec), pool));
            }
            for (int index = 0; index < futures.size(); index++) {
                operating[index] = join(futures.get(index));
            }
        }
        return operating;
    }

    private boolean join(CompletableFuture<Boolean> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MarginXException("Interrupted while sampling the circuit", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof MarginXException cause) {
                throw cause;
            }
            throw new MarginXException("A Monte Carlo trial failed", e.getCause());
        }
    }
}
