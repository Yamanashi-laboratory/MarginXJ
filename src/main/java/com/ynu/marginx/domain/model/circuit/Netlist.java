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

    /**
     * Applies the value to every element sharing the target's synchronisation group, the way
     * synchro() does before each make_cir() call in margin_ele_syn.cpp. Group 0 means "not
     * synchronised", so only the target moves.
     */
    public Netlist withSynchronizedValue(int index, double value) {
        int group = elements.get(index).synchronizationGroup();
        if (group == 0) {
            return withElementValue(index, value);
        }
        List<CircuitElement> updated = new ArrayList<>(elements);
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).synchronizationGroup() == group) {
                updated.set(i, updated.get(i).withValue(value));
            }
        }
        // The swept element itself is rendered from make_cir()'s argument regardless of its group.
        updated.set(index, updated.get(index).withValue(value));
        return new Netlist(baseName, lines, updated);
    }

    public Netlist withElements(List<CircuitElement> newElements) {
        return new Netlist(baseName, lines, newElements);
    }
}
