package com.ynu.marginx.presentation.cli.view;

import com.ynu.marginx.application.OptimizationProgressListener;
import com.ynu.marginx.domain.model.margin.MarginTable;
import java.io.PrintStream;

/**
 * Says where an optimisation has got to.
 *
 * <p>A run at the default settings is fifty thousand simulations, and until now it printed nothing
 * between starting and finishing - long enough that a working run and a hung one look the same.
 * The cycle count is the honest measure of progress: the limit is the most cycles the run could
 * take, and it stops early whenever the yield stalls.
 */
public final class OptimizationProgressBarView implements OptimizationProgressListener {

    private static final int WIDTH = 50;

    private final PrintStream out;

    public OptimizationProgressBarView(PrintStream out) {
        this.out = out;
    }

    @Override
    public synchronized void cycleStarted(int cycle, int totalCycles) {
        int percent = totalCycles == 0 ? 100 : cycle * 100 / totalCycles;
        out.printf("\r [%-50s] cycle %d/%d ", "#".repeat(percent * WIDTH / 100), cycle + 1, totalCycles);
        out.flush();
    }

    @Override
    public synchronized void measurementStarted(int measurement) {
        out.printf("\r%-72s\r Measuring every element (%d)... ", "", measurement + 1);
        out.flush();
    }

    @Override
    public synchronized void measurementCompleted(int measurement, MarginTable table) {
        out.printf("\r%-72s\r", "");
        out.flush();
    }
}
