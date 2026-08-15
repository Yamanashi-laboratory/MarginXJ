package com.ynu.marginx.domain.service;

import com.ynu.marginx.domain.model.judge.JudgementOutcome;
import com.ynu.marginx.domain.model.judge.JudgementRule;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.judge.SimulationResult;
import com.ynu.marginx.shared.exception.SimulationFailedException;
import java.util.Locale;

public final class OperationJudge {

    public boolean passes(SimulationResult result, JudgementSpec spec) {
        return evaluate(result, spec).passed();
    }

    public JudgementOutcome evaluate(SimulationResult result, JudgementSpec spec) {
        if (result.isEmpty()) {
            throw new SimulationFailedException("Simulator produced fewer than two samples.");
        }
        double startTime = result.startTime();
        double timeScale = result.timeScale();

        for (int column = 1; column < result.columnCount(); column++) {
            for (JudgementRule rule : spec.rulesFor(column - 1)) {
                int beginRow = (int) ((rule.beginTime() - startTime) / timeScale);
                int endRow = (int) ((rule.endTime() - startTime) / timeScale);
                if (endRow > result.rowCount()) {
                    throw new SimulationFailedException(
                            "Judgement window %d..%d exceeds the simulated time range."
                                    .formatted(rule.beginTime(), rule.endTime()));
                }
                if (!satisfies(result, column, beginRow, endRow, rule)) {
                    return JudgementOutcome.violated(String.format(Locale.ROOT,
                            "element No. %d, window %d..%d, phase %.3f",
                            column, rule.beginTime(), rule.endTime(), rule.phase()));
                }
            }
        }
        return JudgementOutcome.pass();
    }

    private boolean satisfies(SimulationResult result, int column, int beginRow, int endRow, JudgementRule rule) {
        double threshold = rule.phaseThreshold();
        boolean crossesUpward = result.at(beginRow, column) < threshold;
        boolean crossed = false;
        for (int row = beginRow; row < endRow; row++) {
            double sample = result.at(row, column);
            if (crossesUpward ? sample > threshold : sample < threshold) {
                crossed = true;
                break;
            }
        }
        return rule.inverted() != crossed;
    }
}
