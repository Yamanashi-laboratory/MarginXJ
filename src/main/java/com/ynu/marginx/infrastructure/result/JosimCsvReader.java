package com.ynu.marginx.infrastructure.result;

import com.ynu.marginx.domain.model.judge.SimulationResult;
import java.nio.file.Path;

/** Reads JoSIM output: one header row, then comma separated samples. */
public final class JosimCsvReader {

    private static final int HEADER_LINES = 1;

    public SimulationResult read(Path csv) {
        return CsvRows.read(csv, HEADER_LINES, "JoSIM");
    }
}
