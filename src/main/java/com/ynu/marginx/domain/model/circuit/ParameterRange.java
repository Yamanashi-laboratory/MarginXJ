package com.ynu.marginx.domain.model.circuit;

public record ParameterRange(double min, double max) {

    public ParameterRange {
        if (min > max) {
            throw new IllegalArgumentException("min must not exceed max: " + min + " > " + max);
        }
    }

    public double clamp(double value) {
        return Math.min(max, Math.max(min, value));
    }

    public boolean contains(double value) {
        return min <= value && value <= max;
    }

    public ParameterRange withMin(double newMin) {
        return new ParameterRange(newMin, max);
    }

    public ParameterRange withMax(double newMax) {
        return new ParameterRange(min, newMax);
    }
}
