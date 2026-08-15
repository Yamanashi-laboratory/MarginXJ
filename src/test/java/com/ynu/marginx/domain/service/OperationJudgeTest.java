package com.ynu.marginx.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ynu.marginx.domain.model.judge.JudgementRule;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.judge.SimulationResult;
import com.ynu.marginx.shared.exception.SimulationFailedException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OperationJudgeTest {

    private final OperationJudge judge = new OperationJudge();

    @Test
    void passesWhenThePhaseCrossesTheThresholdInsideTheWindow() {
        SimulationResult result = ramp(5);

        assertThat(judge.passes(result, spec(new JudgementRule(0, 10, 1, false)))).isTrue();
    }

    @Test
    void failsWhenThePhaseNeverCrossesTheThreshold() {
        SimulationResult result = ramp(Integer.MAX_VALUE);

        assertThat(judge.passes(result, spec(new JudgementRule(0, 10, 1, false)))).isFalse();
    }

    @Test
    void invertedRulesRequireTheAbsenceOfACrossing() {
        SimulationResult result = ramp(Integer.MAX_VALUE);

        assertThat(judge.passes(result, spec(new JudgementRule(0, 10, 1, true)))).isTrue();
    }

    @Test
    void reportsWhichWindowWasViolated() {
        SimulationResult result = ramp(Integer.MAX_VALUE);

        assertThat(judge.evaluate(result, spec(new JudgementRule(2, 9, 1, false))).violation())
                .contains("window 2..9");
    }

    @Test
    void rejectsAWindowLongerThanTheSimulation() {
        SimulationResult result = ramp(5);

        assertThatThrownBy(() -> judge.passes(result, spec(new JudgementRule(0, 999, 1, false))))
                .isInstanceOf(SimulationFailedException.class);
    }

    private SimulationResult ramp(int crossingStep) {
        List<double[]> rows = new ArrayList<>();
        for (int step = 0; step < 12; step++) {
            rows.add(new double[] {step, step >= crossingStep ? 4.0 : 0.0});
        }
        return new SimulationResult(rows);
    }

    private JudgementSpec spec(JudgementRule rule) {
        return new JudgementSpec(List.of(List.of(rule)));
    }
}
