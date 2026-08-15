package com.ynu.marginx.presentation.view;

import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.service.CriticalElementFinder;
import java.io.PrintStream;
import java.util.Locale;
import java.util.Optional;

public final class MarginChartView {

    private static final double SCALE_LIMIT = 50;
    private static final int BAR_WIDTH = 25;

    private final PrintStream out;
    private final CriticalElementFinder finder;

    public MarginChartView(PrintStream out, CriticalElementFinder finder) {
        this.out = out;
        this.finder = finder;
    }

    public void print(MarginTable table) {
        out.println();
        out.println("                  -50%                                                 50%");
        out.println("                    +---------------------------------------------------+");
        for (ElementMargin entry : table.entries()) {
            double lower = Math.min(Math.abs(entry.margin().lowerPercent()), SCALE_LIMIT);
            double upper = Math.min(entry.margin().upperPercent(), SCALE_LIMIT);
            out.printf(Locale.ROOT, "%6s  :  %6.2f %% [%25s|%-25s]%6.2f %%%n",
                    entry.displayName(), -lower, bar(lower), bar(upper), upper);
        }
        out.println("                    +---------------------------------------------------+");
        out.println("                  -50%                                                 50%");
        printCritical("    Critical Margin : ", finder.findCritical(table));
        printCritical("    Bias Margin     : ", finder.findCriticalBias(table));
        out.println();
    }

    private String bar(double percent) {
        return "#".repeat(Math.max(0, (int) Math.ceil(percent / 2)));
    }

    private void printCritical(String label, Optional<ElementMargin> entry) {
        entry.ifPresentOrElse(
                margin -> out.printf(Locale.ROOT, "%s%.2f %% ('%s')%n",
                        label, margin.margin().criticalPercent(), margin.displayName()),
                () -> out.println(label + "n/a"));
    }
}
