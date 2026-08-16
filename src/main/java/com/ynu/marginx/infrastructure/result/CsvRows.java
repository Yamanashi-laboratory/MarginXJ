package com.ynu.marginx.infrastructure.result;

import com.ynu.marginx.domain.model.judge.SimulationResult;
import com.ynu.marginx.shared.exception.SimulationFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared parsing for both simulators' output. They differ in one detail only: readJOSIMData.cpp
 * drops a header line that readJSIMdata.cpp has none of.
 */
final class CsvRows {

    /** Both simulators report seconds; the judgement windows are written in picoseconds. */
    private static final double TIME_SCALE = 1e12;

    private CsvRows() {
    }

    static SimulationResult read(Path csv, int headerLines, String simulator) {
        if (!Files.isRegularFile(csv)) {
            throw new SimulationFailedException(simulator + " produced no output file: " + csv);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(csv);
        } catch (IOException e) {
            throw new SimulationFailedException("Cannot read " + simulator + " output " + csv, e);
        }
        if (lines.size() <= headerLines) {
            throw new SimulationFailedException(simulator + " output is empty: " + csv);
        }

        List<double[]> rows = new ArrayList<>(lines.size() - headerLines);
        for (String line : lines.subList(headerLines, lines.size())) {
            double[] row = parseRow(line, csv, simulator);
            if (row.length > 0) {
                rows.add(row);
            }
        }
        return new SimulationResult(rows);
    }

    /**
     * JSIM is built with MSVC, whose printf spells a diverged sample {@code -1.#IO}, {@code 1.#INF}
     * or {@code -1.#IND} rather than an infinity or a NaN. Reading those back as non-finite doubles
     * makes the judgement fail for that run, which is what a diverged circuit means. The C++ reader
     * arrives at the same outcome by accident: its stream extraction stops at the '#'.
     *
     * @return the value, or null when the token is not a divergence marker at all
     */
    private static Double nonFinite(String token) {
        int hash = token.indexOf('#');
        if (hash < 0) {
            return null;
        }
        String kind = token.substring(hash + 1).toUpperCase(Locale.ROOT);
        if (kind.startsWith("IND") || kind.startsWith("QNAN") || kind.startsWith("SNAN")) {
            return Double.NaN;
        }
        if (kind.startsWith("INF") || kind.startsWith("IO")) {
            return token.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        return null;
    }

    private static double[] parseRow(String line, Path csv, String simulator) {
        String[] tokens = line.trim().split("[,\\s]+");
        List<Double> values = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            try {
                values.add(Double.parseDouble(token));
            } catch (NumberFormatException e) {
                Double diverged = nonFinite(token);
                if (diverged == null) {
                    // Neither a number nor a value we recognise as diverged: name the line rather
                    // than let a raw parse error out. A simulator that cannot solve the circuit
                    // often says so in the output file instead of through its exit code.
                    throw new SimulationFailedException(
                            simulator + " wrote a value that is not a number into " + csv + ": "
                                    + line.trim(), e);
                }
                values.add(diverged);
            }
        }
        double[] row = new double[values.size()];
        for (int i = 0; i < row.length; i++) {
            row[i] = i == 0 ? values.get(i) * TIME_SCALE : values.get(i);
        }
        return row;
    }
}
