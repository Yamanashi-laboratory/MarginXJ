package com.ynu.marginx.application;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementOutcome;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.service.OperationEvaluator;

public final class JudgeOperationUseCase {

    private final OperationEvaluator evaluator;

    public JudgeOperationUseCase(OperationEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public JudgementOutcome execute(Netlist netlist, JudgementSpec spec) {
        return evaluator.evaluate(netlist, spec);
    }
}
