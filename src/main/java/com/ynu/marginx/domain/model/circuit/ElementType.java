package com.ynu.marginx.domain.model.circuit;

import java.util.Locale;

public enum ElementType {

    INDUCTOR("L", "H", new ParameterRange(0, 1000)) {
        @Override
        public String render(CircuitElement element, double value) {
            return String.format(Locale.ROOT, "%s%6s%6s%20.3f%s  fcheck",
                    element.lowerCaseName(), element.node1(), element.node2(), value, element.unit());
        }
    },

    COUPLING("K", "", new ParameterRange(-1, 1)) {
        @Override
        public String render(CircuitElement element, double value) {
            return String.format(Locale.ROOT, "%s%6s%6s%20.3f",
                    element.lowerCaseName(), element.node1(), element.node2(), value);
        }
    },

    JUNCTION("B", "", new ParameterRange(0.1, 3)) {
        @Override
        public String render(CircuitElement element, double value) {
            return renderJunction(element, value);
        }
    },

    JUNCTION_INDUCTANCE("BI", "", new ParameterRange(0, 3)) {
        @Override
        public String render(CircuitElement element, double value) {
            return renderJunction(element, value);
        }
    },

    CAPACITOR("C", "F", new ParameterRange(0, 3)) {
        @Override
        public String render(CircuitElement element, double value) {
            return renderPassive(element, value);
        }
    },

    RESISTOR("R", "ohm", new ParameterRange(0, 1000)) {
        @Override
        public String render(CircuitElement element, double value) {
            return renderPassive(element, value);
        }
    },

    VOLTAGE_SOURCE("V", "V", new ParameterRange(-10, 10)) {
        @Override
        public String render(CircuitElement element, double value) {
            return renderSource(element, value);
        }
    },

    CURRENT_SOURCE("I", "A", new ParameterRange(-10, 10)) {
        @Override
        public String render(CircuitElement element, double value) {
            return renderSource(element, value);
        }
    };

    private final String directiveKey;
    private final String baseUnit;
    private final ParameterRange defaultRange;

    ElementType(String directiveKey, String baseUnit, ParameterRange defaultRange) {
        this.directiveKey = directiveKey;
        this.baseUnit = baseUnit;
        this.defaultRange = defaultRange;
    }

    public abstract String render(CircuitElement element, double value);

    public String directiveKey() {
        return directiveKey;
    }

    public String baseUnit() {
        return baseUnit;
    }

    public ParameterRange defaultRange() {
        return defaultRange;
    }

    public boolean isJunction() {
        return this == JUNCTION || this == JUNCTION_INDUCTANCE;
    }

    public boolean isBiasSource() {
        return this == VOLTAGE_SOURCE || this == CURRENT_SOURCE;
    }

    /** Junctions are reported as J-something even though the netlist spells them B-something. */
    public String displayName(String elementName) {
        return isJunction() ? "J" + elementName.substring(1) : elementName;
    }

    private static String renderJunction(CircuitElement element, double value) {
        return String.format(Locale.ROOT, "%s%6s%6s  %s area=%-10.3f",
                element.lowerCaseName(), element.node1(), element.node2(), element.junctionModel(), value);
    }

    private static String renderPassive(CircuitElement element, double value) {
        return String.format(Locale.ROOT, "%s%6s%6s%20.3f%s",
                element.lowerCaseName(), element.node1(), element.node2(), value, element.unit());
    }

    private static String renderSource(CircuitElement element, double value) {
        return String.format(Locale.ROOT, "%s%6s%6s   PWL(0ps 0mV %10s%20.3f%s)",
                element.lowerCaseName(), element.node1(), element.node2(), "50ps   ", value, element.unit());
    }
}
