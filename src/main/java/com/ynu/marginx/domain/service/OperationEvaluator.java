package com.ynu.marginx.domain.service;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementOutcome;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.shared.exception.CalculationCancelledException;

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
        stopIfCancelled();
        return judge.evaluate(simulator.simulate(netlist), spec);
    }

    /**
     * Every search and every optimiser reaches the simulator through here, so this one check makes
     * all of them interruptible - a margin run between elements, and a Monte Carlo cycle between
     * trials. The flag is read rather than cleared: the threads unwinding above still need it.
     */
    private void stopIfCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CalculationCancelledException("Cancelled before running the simulator");
        }
    }
}
