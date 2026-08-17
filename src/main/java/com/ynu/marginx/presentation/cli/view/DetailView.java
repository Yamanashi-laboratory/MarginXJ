package com.ynu.marginx.presentation.cli.view;

import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import java.io.PrintStream;
import java.util.Locale;

public final class DetailView {

    private final PrintStream out;

    public DetailView(PrintStream out) {
        this.out = out;
    }

    public void print(MarginTable table) {
        for (ElementMargin entry : table.entries()) {
            out.printf(Locale.ROOT,
                    "%6s : %6.3f %-5s Lower: %6.3f   Upper: %6.3f   Median: %6.3f  %7.2f %% ~ %6.2f %%%n",
                    entry.displayName(), entry.element().value(), entry.element().unit(),
                    entry.lowerValue(), entry.upperValue(), entry.medianValue(),
                    entry.margin().lowerPercent(), entry.margin().upperPercent());
        }
    }
}
