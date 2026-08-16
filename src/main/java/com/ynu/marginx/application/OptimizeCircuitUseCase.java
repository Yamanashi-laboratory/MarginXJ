package com.ynu.marginx.application;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.optimize.OptimizationOutcome;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.domain.port.NetlistRepository;
import com.ynu.marginx.domain.service.CriticalMarginMethod;

/**
 * Runs an optimisation and files what it produced: the optimised circuit next to the original, and
 * the margins of the circuit it settled on. The C++ tool ends each optimisation the same way, with
 * a final Margin_low() and make_cir_last().
 */
public final class OptimizeCircuitUseCase {

    private final CriticalMarginMethod criticalMarginMethod;
    private final NetlistRepository netlists;
    private final MarginResultRepository results;

    public OptimizeCircuitUseCase(CriticalMarginMethod criticalMarginMethod, NetlistRepository netlists,
                                  MarginResultRepository results) {
        this.criticalMarginMethod = criticalMarginMethod;
        this.netlists = netlists;
        this.results = results;
    }

    public OptimizationOutcome withCriticalMarginMethod(Netlist netlist, JudgementSpec spec) {
        return record(netlist.baseName(), criticalMarginMethod.optimize(netlist, spec));
    }

    private OptimizationOutcome record(String baseName, OptimizationOutcome outcome) {
        netlists.save(baseName, outcome.netlist());
        results.save(baseName, outcome.margins());
        return outcome;
    }
}
