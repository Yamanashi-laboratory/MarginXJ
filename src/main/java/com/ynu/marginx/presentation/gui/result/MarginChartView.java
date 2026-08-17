package com.ynu.marginx.presentation.gui.result;

import com.ynu.marginx.domain.model.margin.MarginTable;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.BorderPane;

/**
 * The margin figure: one horizontal bar per element, the lower margin left of zero and the upper
 * margin right of it.
 *
 * <p>This replaces fig_out.cpp's ASCII drawing and the matplotlib script that came with the C++
 * tool. Nothing here shells out to Python; the shape of the plot is the only thing carried over.
 */
public final class MarginChartView extends BorderPane {

    private static final String BAR_STYLE = "-fx-bar-fill: darkorange;";
    private static final String SELECTED_STYLE = "-fx-bar-fill: derive(darkorange, -30%);";
    private static final String BASE_STYLE = "-fx-bar-fill: transparent;";

    private final NumberAxis marginAxis = new NumberAxis(-50, 50, 10);
    private final CategoryAxis elementAxis = new CategoryAxis();
    private final StackedBarChart<Number, String> chart = new StackedBarChart<>(marginAxis, elementAxis);
    /**
     * Two series make one floating bar. JavaFX stacks sequentially rather than piling negatives
     * left and positives right, so a series holding the lower margin does not draw a bar from zero
     * leftwards - it just moves where the next series starts. That is used here on purpose: the
     * base is invisible and only shifts the start, and the span drawn on top of it runs the whole
     * width of the operating window, which is the bar scripts/margin.py produced.
     */
    private final XYChart.Series<Number, String> base = new XYChart.Series<>();
    private final XYChart.Series<Number, String> span = new XYChart.Series<>();
    private final ReadOnlyStringWrapper selectedElement = new ReadOnlyStringWrapper();

    public MarginChartView() {
        marginAxis.setLabel("Margin [%]");
        marginAxis.setAutoRanging(false);
        elementAxis.setLabel("");
        base.setName("Base");
        span.setName("Margin");
        chart.getData().add(base);
        chart.getData().add(span);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setTitle("");
        // The line at zero that margin.py drew by hand.
        chart.setVerticalZeroLineVisible(true);
        setCenter(chart);
    }

    /** The element the user last clicked on, so the table can follow along. */
    public ReadOnlyStringProperty selectedElementProperty() {
        return selectedElement.getReadOnlyProperty();
    }

    public void show(MarginTable table) {
        MarginChartData data = MarginChartData.from(table);
        marginAxis.setLowerBound(data.lowerBound());
        marginAxis.setUpperBound(data.upperBound());
        marginAxis.setTickUnit(data.tickUnit());

        base.getData().clear();
        span.getData().clear();
        for (MarginChartData.Bar bar : data.bars()) {
            base.getData().add(bar(bar.elementName(), bar.lowerPercent(), BASE_STYLE));
            span.getData().add(bar(bar.elementName(), bar.widthPercent(), BAR_STYLE));
        }
        highlight(selectedElement.get());
    }

    public void clear() {
        base.getData().clear();
        span.getData().clear();
        selectedElement.set(null);
    }

    /** Called when the selection came from the table rather than from a click on the chart. */
    public void select(String elementName) {
        selectedElement.set(elementName);
        highlight(elementName);
    }

    private XYChart.Data<Number, String> bar(String elementName, double percent, String style) {
        XYChart.Data<Number, String> point = new XYChart.Data<>(percent, elementName);
        point.nodeProperty().addListener((observable, previous, node) -> {
            if (node != null) {
                node.setStyle(elementName.equals(selectedElement.get()) ? SELECTED_STYLE : style);
                node.setOnMouseClicked(event -> select(elementName));
            }
        });
        return point;
    }

    private void highlight(String elementName) {
        restyle(span, elementName, BAR_STYLE);
    }

    private void restyle(XYChart.Series<Number, String> series, String selected, String ordinary) {
        for (XYChart.Data<Number, String> point : series.getData()) {
            Node node = point.getNode();
            if (node != null) {
                node.setStyle(point.getYValue().equals(selected) ? SELECTED_STYLE : ordinary);
            }
        }
    }

    /**
     * The chart itself, for taking a snapshot of exactly the plot and nothing else - and for a
     * test to read back the series it drew.
     */
    public StackedBarChart<Number, String> plot() {
        return chart;
    }
}
