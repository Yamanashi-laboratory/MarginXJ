package com.ynu.marginx.presentation.gui.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.ynu.marginx.domain.model.circuit.CircuitElement;
import com.ynu.marginx.domain.model.circuit.ElementType;
import com.ynu.marginx.domain.model.circuit.ShuntSpec;
import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.Margin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.testsupport.FxToolkit;
import javafx.scene.chart.XYChart;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The two result views against a real scene graph, including the selection that links them. */
class MarginResultViewsTest {

    @BeforeAll
    static void toolkit() {
        FxToolkit.startOrSkip();
    }

    @Test
    void everyBarRunsFromTheLowerMarginToTheUpperOne() {
        MarginChartView chart = FxToolkit.call(MarginChartView::new);

        FxToolkit.run(() -> chart.show(table()));

        List<String> categories = FxToolkit.call(() -> chart.plot().getData().get(0).getData().stream()
                .map(point -> point.getYValue().toString())
                .toList());
        List<Number> base = FxToolkit.call(() -> chart.plot().getData().get(0).getData().stream()
                .map(XYChart.Data::getXValue)
                .toList());
        List<Number> span = FxToolkit.call(() -> chart.plot().getData().get(1).getData().stream()
                .map(XYChart.Data::getXValue)
                .toList());

        assertThat(categories).containsExactly("R02", "R01");
        // JavaFX stacks the second series onto the end of the first rather than mirroring around
        // zero, so the first series only moves the start and the second draws the whole window.
        assertThat(base).containsExactly(-30.0, -10.0);
        assertThat(span).containsExactly(70.0, 30.0);
    }

    @Test
    void clickingTheChartMovesTheTableAndBack() {
        MarginChartView chart = FxToolkit.call(MarginChartView::new);
        MarginTableView table = FxToolkit.call(MarginTableView::new);
        FxToolkit.run(() -> {
            chart.show(table());
            MarginTable measured = table();
            for (int index = 0; index < measured.size(); index++) {
                table.put(index, measured.get(index));
            }
            // What MainWindow wires up between the two.
            table.getSelectionModel().selectedItemProperty().addListener(
                    (observable, previous, row) -> chart.select(row.displayName()));
            chart.selectedElementProperty().addListener(
                    (observable, previous, name) -> table.select(name));
        });

        FxToolkit.run(() -> chart.select("R02"));
        assertThat(FxToolkit.call(() -> table.getSelectionModel().getSelectedItem().displayName()))
                .isEqualTo("R02");

        FxToolkit.run(() -> table.select("R01"));
        assertThat(FxToolkit.call(() -> chart.selectedElementProperty().get())).isEqualTo("R01");
    }

    @Test
    void theTableFillsOneRowAtATime() {
        MarginTableView table = FxToolkit.call(MarginTableView::new);

        FxToolkit.run(() -> table.put(0, entry("R01", -10, 20)));
        assertThat(FxToolkit.call(() -> table.rows().size())).isEqualTo(1);

        FxToolkit.run(() -> table.put(1, entry("R02", -30, 40)));
        assertThat(FxToolkit.call(() -> table.rows().size())).isEqualTo(2);

        FxToolkit.run(table::clear);
        assertThat(FxToolkit.call(() -> table.rows().size())).isZero();
    }

    @Test
    void theRowsFollowTheCircuitAndNotTheOrderTheyFinishedIn() {
        MarginTableView table = FxToolkit.call(MarginTableView::new);

        // The searches run in parallel, so a slow first element arrives after a quick third one.
        FxToolkit.run(() -> {
            table.put(2, entry("R03", -30, 40));
            table.put(0, entry("R01", -10, 20));
            table.put(3, entry("R04", -40, 50));
            table.put(1, entry("R02", -20, 30));
        });

        assertThat(FxToolkit.call(() -> table.rows().stream().map(ElementMargin::displayName).toList()))
                .containsExactly("R01", "R02", "R03", "R04");
    }

    @Test
    void clearingLetsTheNextRunStartFromTheTop() {
        MarginTableView table = FxToolkit.call(MarginTableView::new);

        FxToolkit.run(() -> {
            table.put(1, entry("R02", -20, 30));
            table.clear();
            // Without forgetting the indices too, this row would be placed after the cleared one.
            table.put(0, entry("R01", -10, 20));
        });

        assertThat(FxToolkit.call(() -> table.rows().stream().map(ElementMargin::displayName).toList()))
                .containsExactly("R01");
    }

    private MarginTable table() {
        return new MarginTable(List.of(entry("R01", -10, 20), entry("R02", -30, 40)));
    }

    private ElementMargin entry(String name, double lower, double upper) {
        CircuitElement element = new CircuitElement(ElementType.RESISTOR, 0, name, "1", "2",
                1.0, "ohm", ElementType.RESISTOR.defaultRange(), false, 0, ShuntSpec.unshunted(), "");
        return new ElementMargin(element, new Margin(lower, upper));
    }
}
