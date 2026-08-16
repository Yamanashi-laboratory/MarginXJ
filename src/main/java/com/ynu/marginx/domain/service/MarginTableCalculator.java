package com.ynu.marginx.domain.service;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.MarginTable;

/**
 * Measures every element's margin. The optimisers re-measure after each step the way the C++ tool
 * calls Margin() and Margin_low() from inside its optimisation loops; how that measurement is
 * scheduled - sequentially here, in parallel from the application layer - is not their concern.
 */
@FunctionalInterface
public interface MarginTableCalculator {

    MarginTable calculate(Netlist netlist, JudgementSpec spec);
}
