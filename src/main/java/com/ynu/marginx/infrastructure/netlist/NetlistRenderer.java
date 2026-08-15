package com.ynu.marginx.infrastructure.netlist;

import com.ynu.marginx.domain.model.circuit.CircuitElement;
import com.ynu.marginx.domain.model.circuit.Netlist;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NetlistRenderer {

    public List<String> render(Netlist netlist, String outputFileName) {
        List<String> lines = new ArrayList<>(netlist.lines());
        for (CircuitElement element : netlist.elements()) {
            lines.set(element.lineNumber(), element.render(element.value()));
            if (element.hasShuntLine()) {
                lines.set(element.lineNumber() + 1, element.renderShuntLine());
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().toLowerCase(Locale.ROOT).startsWith(".file")) {
                lines.set(i, ".FILE " + outputFileName);
            }
        }
        return lines;
    }
}
