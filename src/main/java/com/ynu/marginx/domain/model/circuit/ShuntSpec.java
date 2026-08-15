package com.ynu.marginx.domain.model.circuit;

public record ShuntSpec(ShuntMode mode, double parameter) {

    private static final ShuntSpec UNSHUNTED = new ShuntSpec(ShuntMode.UNSHUNTED, 0);

    private static final double R0 = 100;
    private static final double CAPACITANCE = 0.064;
    private static final double UNSHUNT_RESISTANCE = 1;

    public static ShuntSpec unshunted() {
        return UNSHUNTED;
    }

    /** CALCULATED shunts are registered as their own margin target and render themselves. */
    public boolean emitsResistorLine() {
        return mode == ShuntMode.IC_RS || mode == ShuntMode.BC;
    }

    public String directive() {
        return switch (mode) {
            case IC_RS, CALCULATED -> "*SHUNT=";
            case BC -> "*Bc=";
            case UNSHUNTED -> "";
        };
    }

    public double resistance(double junctionArea) {
        double resistance = switch (mode) {
            case IC_RS, CALCULATED -> parameter / junctionArea;
            case BC -> {
                double critical = Math.sqrt(parameter * 1.055
                        / (2 * 1.602 * junctionArea / 10 * CAPACITANCE * junctionArea));
                yield critical * R0 / (R0 - critical);
            }
            case UNSHUNTED -> UNSHUNT_RESISTANCE;
        };
        return Math.max(resistance, 0);
    }
}
