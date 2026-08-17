package com.ynu.marginx.presentation.gui.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.ynu.marginx.domain.model.circuit.CircuitElement;
import com.ynu.marginx.domain.model.circuit.ElementType;
import com.ynu.marginx.domain.model.circuit.ShuntSpec;
import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.Margin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The plot rules taken from scripts/margin.py, checked without a display: a fixed -50..50 axis in
 * ten percent steps, and the first element of the netlist drawn at the top.
 */
class MarginChartDataTest {

    @Test
    void drawsTheFirstElementOfTheNetlistAtTheTop() {
        MarginChartData data = MarginChartData.from(table(
                entry("R01", -10, 20),
                entry("R02", -30, 40)));

        // JavaFX puts the first category at the bottom, so the order is reversed on the way in.
        assertThat(data.bars()).extracting(MarginChartData.Bar::elementName).containsExactly("R02", "R01");
    }

    @Test
    void keepsTheAxisAtFiftyPercentEitherWay() {
        MarginChartData data = MarginChartData.from(table(entry("R01", -10, 20)));

        assertThat(data.lowerBound()).isEqualTo(-50);
        assertThat(data.upperBound()).isEqualTo(50);
        assertThat(data.tickUnit()).isEqualTo(10);
    }

    @Test
    void widensTheAxisRatherThanClippingABarThatRunsPastFifty() {
        // matplotlib clipped at 50 and simply hid the rest of the bar.
        MarginChartData data = MarginChartData.from(table(entry("R01", -96.58, 99.9)));

        assertThat(data.upperBound()).isEqualTo(100);
        assertThat(data.lowerBound()).isEqualTo(-100);
    }

    @Test
    void carriesBothSidesOfTheWindowThroughUnchanged() {
        MarginChartData data = MarginChartData.from(table(entry("J01", -25.5, 30.25)));

        MarginChartData.Bar bar = data.bars().get(0);
        assertThat(bar.lowerPercent()).isEqualTo(-25.5);
        assertThat(bar.upperPercent()).isEqualTo(30.25);
        assertThat(bar.criticalPercent()).isEqualTo(25.5);
    }

    @Test
    void namesTheElementWithTheNarrowestWindow() {
        MarginChartData data = MarginChartData.from(table(
                entry("R01", -40, 45),
                entry("J02", -12, 60),
                entry("R03", -30, 30)));

        assertThat(data.criticalElement()).isEqualTo("J02");
    }

    private MarginTable table(ElementMargin... entries) {
        return new MarginTable(List.of(entries));
    }

    private ElementMargin entry(String name, double lower, double upper) {
        CircuitElement element = new CircuitElement(ElementType.RESISTOR, 0, name, "1", "2",
                1.0, "ohm", ElementType.RESISTOR.defaultRange(), false, 0, ShuntSpec.unshunted(), "");
        return new ElementMargin(element, new Margin(lower, upper));
    }
}
