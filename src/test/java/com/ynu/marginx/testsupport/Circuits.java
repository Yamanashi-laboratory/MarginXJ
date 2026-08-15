package com.ynu.marginx.testsupport;

import com.ynu.marginx.domain.model.circuit.CircuitElement;
import com.ynu.marginx.domain.model.circuit.ElementType;
import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.circuit.ShuntSpec;
import com.ynu.marginx.domain.model.judge.JudgementRule;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import java.util.List;

public final class Circuits {

    private Circuits() {
    }

    public static Netlist singleResistor(double value) {
        CircuitElement element = new CircuitElement(ElementType.RESISTOR, 0, "R01", "1", "2",
                value, "ohm", ElementType.RESISTOR.defaultRange(), false, 0, ShuntSpec.unshunted(), "");
        return new Netlist("single", List.of("r01   1   2   " + value + "ohm", ".FILE out.csv"),
                List.of(element));
    }

    public static JudgementSpec singleWindow() {
        return new JudgementSpec(List.of(List.of(new JudgementRule(0, 10, 1, false))));
    }
}
