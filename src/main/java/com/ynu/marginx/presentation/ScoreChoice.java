package com.ynu.marginx.presentation;

import com.ynu.marginx.domain.model.optimize.ScoreWeights;
import com.ynu.marginx.shared.exception.MarginXException;
import java.util.Arrays;

/**
 * What an optimisation run is trying to maximise - the choice select_score.cpp puts to the user.
 *
 * <p>It is a command-line option here rather than a prompt, so an optimisation can be scripted;
 * the numbering follows the original menu.
 */
public enum ScoreChoice {

    CRITICAL(1, "Only Critical Margin", ScoreWeights.criticalOnly()),
    BIAS(2, "Only Bias Margin", ScoreWeights.biasOnly()),
    UPPER(3, "Only upper critical Margin", ScoreWeights.upperOnly()),
    LOWER(4, "Only lower critical Margin", ScoreWeights.lowerOnly()),
    CRITICAL_AND_BIAS(5, "Sum of Critical Margin and Bias Margin", ScoreWeights.criticalAndBias()),
    CRITICAL_AND_DOUBLE_BIAS(6, "Sum of Critical Margin and Bias Margin * 2",
            ScoreWeights.criticalAndDoubleBias());

    private final int code;
    private final String label;
    private final ScoreWeights weights;

    ScoreChoice(int code, String label, ScoreWeights weights) {
        this.code = code;
        this.label = label;
        this.weights = weights;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public ScoreWeights weights() {
        return weights;
    }

    public static ScoreChoice fromCode(int code) {
        return Arrays.stream(values())
                .filter(choice -> choice.code == code)
                .findFirst()
                .orElseThrow(() -> new MarginXException("Unknown score: " + code));
    }
}
