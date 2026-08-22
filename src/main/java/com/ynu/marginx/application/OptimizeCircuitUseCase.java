package com.ynu.marginx.application;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.model.optimize.OptimizationOutcome;
import com.ynu.marginx.domain.model.optimize.ScoreWeights;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.domain.port.NetlistRepository;
import com.ynu.marginx.domain.service.CenterOfGravityOptimizer;
import com.ynu.marginx.domain.service.CriticalMarginMethod;
import com.ynu.marginx.domain.service.MarginSearcher;
import com.ynu.marginx.domain.service.MarginTableCalculator;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationSampler;
import com.ynu.marginx.shared.exception.CalculationCancelledException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs an optimisation and files what it produced: the optimised circuit next to the original, and
 * the margins of the circuit it settled on. The C++ tool ends each optimisation the same way, with
 * a final Margin_low() and make_cir_last().
 *
 * <p>The optimisers themselves know nothing about threads, progress or cancellation, and do not
 * need to: the two expensive things they do - measuring every element, and simulating a cycle of
 * Monte Carlo trials - are handed to them from here. {@link #measurements} and {@link #sampling}
 * build those collaborators, which is what lets this class count the work and stop it half way
 * without the domain being aware of either.
 */
public final class OptimizeCircuitUseCase implements CancellableRun {

    private final NetlistRepository netlists;
    private final MarginResultRepository results;
    private final OptimizationProgressListener listener;

    /** The measurement now in progress, so cancelling reaches the simulators it started. */
    private final AtomicReference<CalculateMarginUseCase> measuring = new AtomicReference<>();
    private final AtomicReference<ParallelOperationSampler> sampler = new AtomicReference<>();
    private final AtomicInteger measurements = new AtomicInteger();
    private final AtomicInteger cycles = new AtomicInteger();
    private volatile boolean cancelled;

    public OptimizeCircuitUseCase(NetlistRepository netlists, MarginResultRepository results) {
        this(netlists, results, OptimizationProgressListener.NOOP);
    }

    public OptimizeCircuitUseCase(NetlistRepository netlists, MarginResultRepository results,
                                  OptimizationProgressListener listener) {
        this.netlists = netlists;
        this.results = results;
        this.listener = listener;
    }

    /**
     * A margin measurement for an optimiser to re-measure with: every element in parallel, reported
     * as it starts and finishes, and stopped by {@link #cancel()}.
     *
     * <p>Nothing is written out for these intermediate tables. Only the circuit the run settles on
     * is filed, which is what make_cir_last() does at the end of the C++ loops.
     */
    public MarginTableCalculator measurements(MarginSearcher searcher) {
        return (netlist, spec) -> {
            stopIfCancelled();
            int index = measurements.getAndIncrement();
            listener.measurementStarted(index);
            CalculateMarginUseCase useCase =
                    new CalculateMarginUseCase(searcher, MarginResultRepository.NONE);
            measuring.set(useCase);
            MarginTable table = useCase.execute(netlist, spec, ProgressListener.NOOP);
            listener.measurementCompleted(index, table);
            return table;
        };
    }

    /** A Monte Carlo cycle for an optimiser to sample with, counted and cancellable. */
    public OperationSampler sampling(OperationEvaluator evaluator, int totalCycles) {
        ParallelOperationSampler parallel = new ParallelOperationSampler(evaluator);
        sampler.set(parallel);
        return (candidates, spec) -> {
            stopIfCancelled();
            listener.cycleStarted(cycles.getAndIncrement(), totalCycles);
            return parallel.sample(candidates, spec);
        };
    }

    public OptimizationOutcome withCriticalMarginMethod(CriticalMarginMethod method, Netlist netlist,
                                                        JudgementSpec spec) {
        return record(netlist.baseName(), method.optimize(netlist, spec));
    }

    public OptimizationOutcome withCenterOfGravity(CenterOfGravityOptimizer optimizer, Netlist netlist,
                                                   JudgementSpec spec, ScoreWeights weights) {
        return record(netlist.baseName(), optimizer.optimize(netlist, spec, weights));
    }

    /**
     * Stops the optimisation. The run ends where it stands, by way of a
     * {@link CalculationCancelledException} thrown out of whichever collaborator the optimiser is
     * inside; there is no partial outcome to hand back, and nothing is written out.
     */
    @Override
    public void cancel() {
        cancelled = true;
        ParallelOperationSampler sampling = sampler.get();
        if (sampling != null) {
            sampling.cancel();
        }
        CalculateMarginUseCase measurement = measuring.get();
        if (measurement != null) {
            measurement.cancel();
        }
    }

    @Override
    public boolean awaitTermination(Duration timeout) {
        CalculateMarginUseCase measurement = measuring.get();
        return measurement == null || measurement.awaitTermination(timeout);
    }

    private void stopIfCancelled() {
        if (cancelled) {
            throw new CalculationCancelledException("The optimisation was cancelled");
        }
    }

    private OptimizationOutcome record(String baseName, OptimizationOutcome outcome) {
        netlists.save(baseName, outcome.netlist());
        results.save(baseName, outcome.margins());
        return outcome;
    }
}
