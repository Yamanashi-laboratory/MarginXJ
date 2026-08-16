package com.ynu.marginx.domain.port;

import com.ynu.marginx.domain.model.circuit.Netlist;

public interface NetlistRepository {

    Netlist load(String baseName);

    /**
     * Writes the circuit back out with its current values, the way make_cir_last.cpp saves the
     * result of an optimisation run.
     */
    void save(String baseName, Netlist netlist);
}
