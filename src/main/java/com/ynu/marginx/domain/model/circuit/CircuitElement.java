package com.ynu.marginx.domain.model.circuit;

import java.util.Locale;
import java.util.Objects;

public record CircuitElement(
        ElementType type,
        int lineNumber,
        String name,
        String node1,
        String node2,
        double value,
        String unit,
        ParameterRange range,
        boolean fixed,
        int synchronizationGroup,
        ShuntSpec shunt,
        String junctionModel) {

    public CircuitElement {
        Objects.requireNonNull(type);
        Objects.requireNonNull(name);
        Objects.requireNonNull(range);
        Objects.requireNonNull(shunt);
    }

    public CircuitElement withValue(double newValue) {
        return new CircuitElement(type, lineNumber, name, node1, node2, newValue, unit,
                range, fixed, synchronizationGroup, shunt, junctionModel);
    }

    public CircuitElement withRange(ParameterRange newRange) {
        return new CircuitElement(type, lineNumber, name, node1, node2, value, unit,
                newRange, fixed, synchronizationGroup, shunt, junctionModel);
    }

    public CircuitElement withShunt(ShuntSpec newShunt) {
        return new CircuitElement(type, lineNumber, name, node1, node2, value, unit,
                range, fixed, synchronizationGroup, newShunt, junctionModel);
    }

    public CircuitElement fixedValue() {
        return new CircuitElement(type, lineNumber, name, node1, node2, value, unit,
                range, true, synchronizationGroup, shunt, junctionModel);
    }

    public CircuitElement synchronizedWith(int group) {
        return new CircuitElement(type, lineNumber, name, node1, node2, value, unit,
                range, fixed, group, shunt, junctionModel);
    }

    public String displayName() {
        return type.displayName(name);
    }

    public String lowerCaseName() {
        return name.toLowerCase(Locale.ROOT);
    }

    public String render(double newValue) {
        return type.render(this, newValue);
    }

    public boolean hasShuntLine() {
        return type.isJunction() && shunt.emitsResistorLine();
    }

    /**
     * The shunt resistance is derived from the element's own stored area, not from the value
     * currently being swept - matching make_cir.cpp, whose golden results depend on it.
     */
    public String renderShuntLine() {
        String shuntName = "RS" + lowerCaseName().substring(1);
        return String.format(Locale.ROOT, "%s%6s%6s%20.3f%s  %s%.3f",
                shuntName, node1, node2, shunt.resistance(value), "ohm", shunt.directive(), shunt.parameter());
    }
}
