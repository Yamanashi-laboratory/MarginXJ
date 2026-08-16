package com.ynu.marginx.domain.service;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import java.util.List;

/**
 * Answers "which of these circuits still work" for a batch of Monte Carlo candidates.
 *
 * <p>A cycle of the Center of Gravity Method is a hundred independent simulations; the C++ tool
 * forks a process for each one. Batching them behind this interface lets the application layer run
 * them in parallel without the optimiser knowing how.
 */
@FunctionalInterface
public interface OperationSampler {

    boolean[] sample(List<Netlist> candidates, JudgementSpec spec);

    static OperationSampler sequential(OperationEvaluator evaluator) {
        return (candidates, spec) -> {
            boolean[] operating = new boolean[candidates.size()];
            for (int index = 0; index < candidates.size(); index++) {
                operating[index] = evaluator.operatesCorrectly(candidates.get(index), spec);
            }
            return operating;
        };
    }
}
