package com.ynu.marginx.presentation.gui.result;

import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import java.util.ArrayList;
import java.util.List;

/**
 * What the margin chart draws, worked out without touching JavaFX.
 *
 * <p>This is the shape scripts/margin.py produced with matplotlib: one horizontal bar per element,
 * the lower margin running left of zero and the upper margin right of it, on a fixed -50..50 axis.
 * Keeping the arithmetic here means the axis bounds and the ordering can be tested without a
 * display, and leaves the view with nothing to do but draw.
 */
public record MarginChartData(List<Bar> bars, double lowerBound, double upperBound, double tickUnit) {

    /** One element: the two halves of its operating window, as percentages. */
    public record Bar(String elementName, double lowerPercent, double upperPercent) {

        /** The whole operating window, which is what the visible bar spans. */
        public double widthPercent() {
            return upperPercent - lowerPercent;
        }

        /** The narrower side, which is what decides whether an element is the critical one. */
        public double criticalPercent() {
            return Math.min(-lowerPercent, upperPercent);
        }
    }

    /** The range margin.py fixed with plt.xlim, and the ticks it set every ten percent. */
    private static final double DEFAULT_BOUND = 50;
    private static final double TICK_UNIT = 10;

    public static MarginChartData from(MarginTable table) {
        List<Bar> bars = new ArrayList<>(table.size());
        // margin.py inverts the y axis so the first element of the netlist ends up at the top.
        // JavaFX draws the first category at the bottom, so the order is reversed here instead.
        for (int index = table.size() - 1; index >= 0; index--) {
            ElementMargin entry = table.get(index);
            bars.add(new Bar(entry.displayName(),
                    entry.margin().lowerPercent(), entry.margin().upperPercent()));
        }
        double bound = boundFor(bars);
        return new MarginChartData(List.copyOf(bars), -bound, bound, TICK_UNIT);
    }

    /**
     * Fifty percent either way, unless something reaches further. matplotlib simply clipped at 50
     * and hid the rest; growing the axis in whole ticks keeps every bar visible instead.
     */
    private static double boundFor(List<Bar> bars) {
        double widest = DEFAULT_BOUND;
        for (Bar bar : bars) {
            widest = Math.max(widest, Math.max(Math.abs(bar.lowerPercent()), Math.abs(bar.upperPercent())));
        }
        return Math.ceil(widest / TICK_UNIT) * TICK_UNIT;
    }

    /** The element with the narrowest window, the one the chart calls out. */
    public String criticalElement() {
        String critical = null;
        double narrowest = Double.MAX_VALUE;
        for (Bar bar : bars) {
            if (bar.criticalPercent() < narrowest) {
                narrowest = bar.criticalPercent();
                critical = bar.elementName();
            }
        }
        return critical;
    }
}
