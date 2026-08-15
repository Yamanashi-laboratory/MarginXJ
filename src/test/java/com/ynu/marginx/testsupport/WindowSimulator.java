package com.ynu.marginx.testsupport;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.SimulationResult;
import com.ynu.marginx.domain.port.CircuitSimulator;
import java.util.ArrayList;
import java.util.List;

/**
 * Stands in for JoSIM: the circuit "works" while the target element stays inside a known window,
 * so a searcher can be asserted against an exactly known answer.
 */
public final class WindowSimulator implements CircuitSimulator {

    private static final double TOLERANCE = 1e-9;
    private static final double ABOVE_THRESHOLD = 4.0;

    private final int elementIndex;
    private final double lowerBound;
    private final double upperBound;

    public WindowSimulator(int elementIndex, double lowerBound, double upperBound) {
        this.elementIndex = elementIndex;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    @Override
    public SimulationResult simulate(Netlist netlist) {
        double value = netlist.element(elementIndex).value();
        boolean operating = value >= lowerBound - TOLERANCE && value <= upperBound + TOLERANCE;

        List<double[]> rows = new ArrayList<>();
        for (int step = 0; step < 12; step++) {
            rows.add(new double[] {step, operating && step >= 5 ? ABOVE_THRESHOLD : 0.0});
        }
        return new SimulationResult(rows);
    }

    @Override
    public String name() {
        return "window";
    }
}
