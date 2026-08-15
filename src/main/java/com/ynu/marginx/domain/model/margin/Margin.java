package com.ynu.marginx.domain.model.margin;

public record Margin(double lowerPercent, double upperPercent) {

    public static Margin none() {
        return new Margin(0, 0);
    }

    /** The tighter of the two sides, expressed as a positive percentage. */
    public double criticalPercent() {
        return Math.min(-lowerPercent, upperPercent);
    }
}
