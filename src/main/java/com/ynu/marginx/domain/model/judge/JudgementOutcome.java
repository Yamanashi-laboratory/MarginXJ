package com.ynu.marginx.domain.model.judge;

public record JudgementOutcome(boolean passed, String violation) {

    private static final JudgementOutcome PASSED = new JudgementOutcome(true, null);

    public static JudgementOutcome pass() {
        return PASSED;
    }

    public static JudgementOutcome violated(String description) {
        return new JudgementOutcome(false, description);
    }
}
