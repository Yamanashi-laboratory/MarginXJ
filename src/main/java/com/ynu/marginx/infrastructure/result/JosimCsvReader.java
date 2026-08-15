package com.ynu.marginx.infrastructure.result;

import com.ynu.marginx.domain.model.judge.SimulationResult;
import com.ynu.marginx.shared.exception.SimulationFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JosimCsvReader {

    /** JoSIM reports seconds; the judgement windows are written in picoseconds. */
    private static final double TIME_SCALE = 1e12;

    public SimulationResult read(Path csv) {
        if (!Files.isRegularFile(csv)) {
            throw new SimulationFailedException("JoSIM produced no output file: " + csv);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(csv);
        } catch (IOException e) {
            throw new SimulationFailedException("Cannot read JoSIM output " + csv, e);
        }
        if (lines.size() < 2) {
            throw new SimulationFailedException("JoSIM output is empty: " + csv);
        }

        List<double[]> rows = new ArrayList<>(lines.size() - 1);
        for (String line : lines.subList(1, lines.size())) {
            double[] row = parseRow(line);
            if (row.length > 0) {
                rows.add(row);
            }
        }
        return new SimulationResult(rows);
    }

    private double[] parseRow(String line) {
        String[] tokens = line.trim().split("[,\\s]+");
        List<Double> values = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (!token.isEmpty()) {
                values.add(Double.parseDouble(token));
            }
        }
        double[] row = new double[values.size()];
        for (int i = 0; i < row.length; i++) {
            row[i] = i == 0 ? values.get(i) * TIME_SCALE : values.get(i);
        }
        return row;
    }
}
