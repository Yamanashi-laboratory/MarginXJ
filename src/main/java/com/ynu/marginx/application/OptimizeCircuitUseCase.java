package com.ynu.marginx.application;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.optimize.OptimizationOutcome;
import com.ynu.marginx.domain.model.optimize.ScoreWeights;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.domain.port.NetlistRepository;
import com.ynu.marginx.domain.service.CenterOfGravityOptimizer;
import com.ynu.marginx.domain.service.CriticalMarginMethod;

/**
 * Runs an optimisation and files what it produced: the optimised circuit next to the original, and
 * the margins of the circuit it settled on. The C++ tool ends each optimisation the same way, with
 * a final Margin_low() and make_cir_last().
 */
public final class OptimizeCircuitUseCase {

    private final NetlistRepository netlists;
    private final MarginResultRepository results;

    public OptimizeCircuitUseCase(NetlistRepository netlists, MarginResultRepository results) {
        this.netlists = netlists;
        this.results = results;
    }

    public OptimizationOutcome withCriticalMarginMethod(CriticalMarginMethod method, Netlist netlist,
                                                        JudgementSpec spec) {
        return record(netlist.baseName(), method.optimize(netlist, spec));
    }

    public OptimizationOutcome withCenterOfGravity(CenterOfGravityOptimizer optimizer, Netlist netlist,
                                                   JudgementSpec spec, ScoreWeights weights) {
        return record(netlist.baseName(), optimizer.optimize(netlist, spec, weights));
    }

    private OptimizationOutcome record(String baseName, OptimizationOutcome outcome) {
        netlists.save(baseName, outcome.netlist());
        results.save(baseName, outcome.margins());
        return outcome;
    }
}
