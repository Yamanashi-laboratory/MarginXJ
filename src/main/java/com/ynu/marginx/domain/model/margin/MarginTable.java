package com.ynu.marginx.domain.model.margin;

import java.util.List;

public record MarginTable(List<ElementMargin> entries) {

    public MarginTable {
        entries = List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }

    public ElementMargin get(int index) {
        return entries.get(index);
    }
}
