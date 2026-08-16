package com.ynu.marginx.infrastructure.result;

import com.ynu.marginx.domain.model.judge.SimulationResult;
import java.nio.file.Path;

/**
 * Reads JSIM output: space separated samples with no header at all, so the very first line is
 * already data. Dropping it - the way the JoSIM reader has to - would silently lose t=0.
 */
public final class JsimCsvReader {

    private static final int HEADER_LINES = 0;

    public SimulationResult read(Path csv) {
        return CsvRows.read(csv, HEADER_LINES, "JSIM");
    }
}
