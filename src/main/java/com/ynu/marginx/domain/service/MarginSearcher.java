package com.ynu.marginx.domain.service;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.Margin;

public interface MarginSearcher {

    Margin search(Netlist netlist, int elementIndex, JudgementSpec spec);

    String description();
}
