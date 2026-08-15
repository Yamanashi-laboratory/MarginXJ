package com.ynu.marginx.domain.model.judge;

public record JudgementRule(int beginTime, int endTime, double phase, boolean inverted) {

    public double phaseThreshold() {
        return phase * Math.PI;
    }
}
