package com.ynu.marginx.domain.service;

import com.ynu.marginx.domain.model.circuit.CircuitElement;
import com.ynu.marginx.domain.model.circuit.ElementType;
import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.model.optimize.OptimizationOutcome;
import com.ynu.marginx.domain.model.optimize.OptimizationOutcome.StopReason;
import com.ynu.marginx.domain.model.optimize.ScoreWeights;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * optimize_yield_up.cpp, the Center of Gravity Method: scatter the whole circuit at random, keep
 * the trials that still work, and move every parameter to the centre of gravity of those.
 *
 * <p>The spread widens whenever the recent yield is comfortable, which walks the circuit outwards
 * until it stops surviving. The best circuit seen on the way is what the run returns.
 */
public final class CenterOfGravityOptimizer {

    /**
     * MULTI_NUM, MONTE_CARLO and the thresholds around them. {@link Settings#defaults()} are the
     * C++ values; a test shrinks them so a run finishes without 50,000 simulations.
     */
    public record Settings(int cycles, int trialsPerCycle, int yieldWindow, int yieldThreshold,
                           int stallLimit) {

        public static Settings defaults() {
            return new Settings(500, 100, 5, 60, 100);
        }
    }

    /**
     * Which of the two CGM sources this run follows.
     *
     * <p>optimize_seq.cpp exists because its author suspected the forked version of racing on the
     * shared opt_num, so it repeats the same search with the trials run one at a time. Candidates
     * here are immutable and nothing is shared, so the port keeps the batching for both and takes
     * only the two comparisons that genuinely differ.
     */
    public enum Variant {

        /** optimize_yield_up.cpp. */
        YIELD_UP {
            @Override
            boolean widensAt(int averageYield, int threshold) {
                return averageYield >= threshold;
            }
        },

        /**
         * optimize_seq.cpp: the stall counter restarts whenever a cycle matches the best yield so
         * far, and the spread only widens once the average is strictly above the threshold.
         */
        SEQUENTIAL {
            @Override
            boolean widensAt(int averageYield, int threshold) {
                return averageYield > threshold;
            }
        };

        /** Whether this cycle earns a wider spread. The variants differ only on the boundary. */
        abstract boolean widensAt(int averageYield, int threshold);
    }

    /** rand_global_yield() ignores its argument and always draws from N(1, 0.01). */
    private static final double GLOBAL_SPREAD = 0.01;
    private static final double SPREAD_STEP = 0.01;

    private final OperationSampler sampler;
    private final MarginTableCalculator margins;
    private final CriticalMarginCalculator criticalMargins;
    private final ScoreCalculator scores;
    private final RandomSource random;
    private final Settings settings;
    private final Variant variant;

    public CenterOfGravityOptimizer(OperationSampler sampler, MarginTableCalculator margins,
                                    CriticalMarginCalculator criticalMargins, ScoreCalculator scores,
                                    RandomSource random, Settings settings) {
        this(sampler, margins, criticalMargins, scores, random, settings, Variant.YIELD_UP);
    }

    public CenterOfGravityOptimizer(OperationSampler sampler, MarginTableCalculator margins,
                                    CriticalMarginCalculator criticalMargins, ScoreCalculator scores,
                                    RandomSource random, Settings settings, Variant variant) {
        this.variant = variant;
        this.sampler = sampler;
        this.margins = margins;
        this.criticalMargins = criticalMargins;
        this.scores = scores;
        this.random = random;
        this.settings = settings;
    }

    public OptimizationOutcome optimize(Netlist netlist, JudgementSpec spec, ScoreWeights weights) {
        Netlist initial = netlist;
        Netlist current = netlist;
        MarginTable table = margins.calculate(current, spec);

        // The C++ starts its best-so-far at an all-zero circuit scoring 0, which hands back a
        // circuit of zeroes when nothing ever scores above it. Starting from the circuit we were
        // given means the worst case is that the input comes back unchanged.
        Netlist best = current;
        double bestScore = scores.score(table, weights);

        double spread = Math.round(criticalMargins.critical(table) / 2) / 100.0;
        int[] yields = new int[settings.yieldWindow()];
        int bestYield = 0;
        int stalled = 0;
        int cycles = 0;
        StopReason reason = StopReason.TRIALS_EXHAUSTED;

        for (int cycle = 0; cycle < settings.cycles(); cycle++) {
            cycles++;
            double[] globalScale = globalScales();
            double[] sumOperating = new double[current.elementCount()];
            double[] sumFailing = new double[current.elementCount()];
            int success = 0;

            // The draws are cheap and have to stay in sequence; only the simulations are batched.
            List<Netlist> candidates = new ArrayList<>(settings.trialsPerCycle());
            for (int trial = 0; trial < settings.trialsPerCycle(); trial++) {
                candidates.add(perturb(current, globalScale, spread));
            }
            boolean[] operating = sampler.sample(candidates, spec);
            for (int trial = 0; trial < candidates.size(); trial++) {
                if (operating[trial]) {
                    success++;
                    accumulate(sumOperating, candidates.get(trial));
                } else {
                    accumulate(sumFailing, candidates.get(trial));
                }
            }

            yields[cycle % yields.length] = success;
            stalled++;
            if (success != 0) {
                current = recentre(current, initial, sumOperating, sumFailing, success);
                if (variant == Variant.SEQUENTIAL && success >= bestYield) {
                    bestYield = success;
                    stalled = 0;
                }
            }

            if (variant.widensAt(average(yields), settings.yieldThreshold())) {
                spread += SPREAD_STEP;
                stalled = 0;
                Arrays.fill(yields, 0);
                table = margins.calculate(current, spec);
                double score = scores.score(table, weights);
                if (score > bestScore) {
                    bestScore = score;
                    best = current;
                }
            }
            if (stalled >= settings.stallLimit()) {
                reason = StopReason.YIELD_STALLED;
                break;
            }
        }

        table = margins.calculate(current, spec);
        if (scores.score(table, weights) > bestScore) {
            best = current;
        } else if (best != current) {
            table = margins.calculate(best, spec);
        }
        return new OptimizationOutcome(best, table, cycles, reason);
    }

    /** One draw per element type: the C++ indexes global_rand by ide_num, which is the enum order. */
    private double[] globalScales() {
        double[] scales = new double[ElementType.values().length];
        for (int type = 0; type < scales.length; type++) {
            scales[type] = Math.abs(random.nextNormal(1, GLOBAL_SPREAD));
        }
        return scales;
    }

    /**
     * One Monte Carlo trial: every element is scaled by its own local draw and by the draw its type
     * got for this cycle.
     *
     * <p>Elements are walked in order and each group is synchronised straight away, so a later
     * member of the same group perturbs an already-perturbed value. That compounding is what
     * opt_ele_yield.cpp does.
     */
    private Netlist perturb(Netlist netlist, double[] globalScale, double spread) {
        Netlist candidate = netlist;
        for (int index = 0; index < candidate.elementCount(); index++) {
            CircuitElement element = candidate.element(index);
            if (element.fixed()) {
                continue;
            }
            double local = Math.abs(random.nextNormal(1, spread));
            double value = element.value() * local * globalScale[element.type().ordinal()];
            if (element.type() == ElementType.COUPLING) {
                value = Math.clamp(value, -1, 1);
            }
            candidate = candidate.withSynchronizedValue(index, value);
        }
        return candidate;
    }

    private void accumulate(double[] sums, Netlist candidate) {
        for (int index = 0; index < sums.length; index++) {
            sums[index] += candidate.element(index).value();
        }
    }

    private Netlist recentre(Netlist current, Netlist initial, double[] sumOperating,
                             double[] sumFailing, int success) {
        // lambda is an int division in the original - (MULTI_NUM - success) / MULTI_NUM is 0 for
        // every success above zero - so the correction term drops out and what is left is the
        // plain centre of gravity of the trials that worked. That is what the method is named
        // after, so it stays rather than being turned into a fractional weight.
        int lambda = (settings.trialsPerCycle() - success) / settings.trialsPerCycle();
        int failing = success == settings.trialsPerCycle() ? 1 : settings.trialsPerCycle() - success;

        List<CircuitElement> updated = new ArrayList<>(current.elements());
        for (int index = 0; index < updated.size(); index++) {
            CircuitElement element = updated.get(index);
            double centre = sumOperating[index] / success
                    + lambda * (element.value() - sumFailing[index] / failing);
            double value = element.fixed()
                    ? initial.element(index).value()
                    : element.range().clamp(centre);
            updated.set(index, element.withValue(value));
        }
        return current.withElements(updated);
    }

    /** Integer division, as in the original: the average yield is compared as a whole percent. */
    private int average(int[] yields) {
        return Arrays.stream(yields).sum() / yields.length;
    }
}
