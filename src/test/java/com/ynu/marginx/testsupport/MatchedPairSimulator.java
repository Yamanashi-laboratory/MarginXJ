package com.ynu.marginx.testsupport;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.SimulationResult;
import com.ynu.marginx.domain.port.CircuitSimulator;
import java.util.ArrayList;
import java.util.List;

/**
 * A circuit that only works while its two targets hold the same value and that value sits inside a
 * known window. It separates a synchronised sweep, which keeps the pair in step, from a plain one,
 * which pulls them apart at the very first step.
 */
public final class MatchedPairSimulator implements CircuitSimulator {

    private static final double TOLERANCE = 1e-9;
    private static final double ABOVE_THRESHOLD = 4.0;

    private final double lowerBound;
    private final double upperBound;

    public MatchedPairSimulator(double lowerBound, double upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    @Override
    public SimulationResult simulate(Netlist netlist) {
        double first = netlist.element(0).value();
        double second = netlist.element(1).value();
        boolean matched = Math.abs(first - second) <= TOLERANCE;
        boolean inWindow = first >= lowerBound - TOLERANCE && first <= upperBound + TOLERANCE;
        boolean operating = matched && inWindow;

        List<double[]> rows = new ArrayList<>();
        for (int step = 0; step < 12; step++) {
            rows.add(new double[] {step, operating && step >= 5 ? ABOVE_THRESHOLD : 0.0});
        }
        return new SimulationResult(rows);
    }

    @Override
    public String name() {
        return "matched-pair";
    }
}
