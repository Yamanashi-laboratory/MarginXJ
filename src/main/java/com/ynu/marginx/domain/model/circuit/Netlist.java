package com.ynu.marginx.domain.model.circuit;

import java.util.ArrayList;
import java.util.List;

public record Netlist(String baseName, List<String> lines, List<CircuitElement> elements) {

    public Netlist {
        lines = List.copyOf(lines);
        elements = List.copyOf(elements);
    }

    public int elementCount() {
        return elements.size();
    }

    public CircuitElement element(int index) {
        return elements.get(index);
    }

    public Netlist withElementValue(int index, double value) {
        List<CircuitElement> updated = new ArrayList<>(elements);
        updated.set(index, updated.get(index).withValue(value));
        return new Netlist(baseName, lines, updated);
    }

    public Netlist withElements(List<CircuitElement> newElements) {
        return new Netlist(baseName, lines, newElements);
    }
}
