package com.ynu.marginx.domain.model.judge;

import java.util.List;

public record SimulationResult(List<double[]> rows) {

    public SimulationResult {
        rows = List.copyOf(rows);
    }

    public boolean isEmpty() {
        return rows.size() < 2;
    }

    public int rowCount() {
        return rows.size();
    }

    public int columnCount() {
        return rows.isEmpty() ? 0 : rows.get(0).length;
    }

    public double at(int row, int column) {
        return rows.get(row)[column];
    }

    public double startTime() {
        return at(0, 0);
    }

    public double timeScale() {
        return at(1, 0) - at(0, 0);
    }
}
