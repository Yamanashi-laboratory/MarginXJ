package com.ynu.marginx.domain.model.margin;

import com.ynu.marginx.domain.model.circuit.CircuitElement;

public record ElementMargin(CircuitElement element, Margin margin) {

    public String displayName() {
        return element.displayName();
    }

    public double lowerValue() {
        return element.value() * (1 + margin.lowerPercent() / 100);
    }

    public double upperValue() {
        return element.value() * (1 + margin.upperPercent() / 100);
    }

    public double medianValue() {
        return element.value() * (1 + (margin.lowerPercent() + margin.upperPercent()) / 200);
    }
}
