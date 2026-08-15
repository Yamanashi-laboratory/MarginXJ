package com.ynu.marginx.domain.service;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementOutcome;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.port.CircuitSimulator;

public final class OperationEvaluator {

    private final CircuitSimulator simulator;
    private final OperationJudge judge;

    public OperationEvaluator(CircuitSimulator simulator, OperationJudge judge) {
        this.simulator = simulator;
        this.judge = judge;
    }

    public boolean operatesCorrectly(Netlist netlist, JudgementSpec spec) {
        return evaluate(netlist, spec).passed();
    }

    public JudgementOutcome evaluate(Netlist netlist, JudgementSpec spec) {
        return judge.evaluate(simulator.simulate(netlist), spec);
    }
}
