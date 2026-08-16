package com.ynu.marginx.domain.model.optimize;

/**
 * How much each critical-margin reading counts towards the score, the {@code power} array
 * select_score.cpp fills in from the user's choice.
 */
public record ScoreWeights(double critical, double bias, double upper, double lower) {

    /** Menu option 1: only the critical margin. */
    public static ScoreWeights criticalOnly() {
        return new ScoreWeights(1, 0, 0, 0);
    }

    /** Menu option 2: only the bias margin. */
    public static ScoreWeights biasOnly() {
        return new ScoreWeights(0, 1, 0, 0);
    }

    /** Menu option 3. */
    public static ScoreWeights upperOnly() {
        return new ScoreWeights(0, 0, 1, 0);
    }

    /** Menu option 4. */
    public static ScoreWeights lowerOnly() {
        return new ScoreWeights(0, 0, 0, 1);
    }

    /** Menu option 5: critical and bias margin, weighted alike. */
    public static ScoreWeights criticalAndBias() {
        return new ScoreWeights(1, 1, 0, 0);
    }

    /** Menu option 6: the same, with the bias margin counting double. */
    public static ScoreWeights criticalAndDoubleBias() {
        return new ScoreWeights(1, 2, 0, 0);
    }
}
