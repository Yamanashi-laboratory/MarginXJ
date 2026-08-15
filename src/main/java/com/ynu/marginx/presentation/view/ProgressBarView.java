package com.ynu.marginx.presentation.view;

import com.ynu.marginx.application.ProgressListener;
import java.io.PrintStream;

public final class ProgressBarView implements ProgressListener {

    private static final int WIDTH = 50;

    private final PrintStream out;

    public ProgressBarView(PrintStream out) {
        this.out = out;
    }

    @Override
    public void started(int total) {
        render(0, total);
    }

    @Override
    public synchronized void advanced(int completed, int total) {
        render(completed, total);
    }

    @Override
    public void finished() {
        out.println();
    }

    private void render(int completed, int total) {
        int percent = total == 0 ? 100 : completed * 100 / total;
        out.printf("\r [%-50s] %3d %% ", "#".repeat(percent * WIDTH / 100), percent);
        out.flush();
    }
}
