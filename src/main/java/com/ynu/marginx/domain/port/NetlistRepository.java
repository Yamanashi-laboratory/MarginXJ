package com.ynu.marginx.domain.port;

import com.ynu.marginx.domain.model.circuit.Netlist;

public interface NetlistRepository {

    Netlist load(String baseName);
}
