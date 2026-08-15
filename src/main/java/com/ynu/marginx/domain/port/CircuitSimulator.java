package com.ynu.marginx.domain.port;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.SimulationResult;

public interface CircuitSimulator {

    SimulationResult simulate(Netlist netlist);

    String name();
}
