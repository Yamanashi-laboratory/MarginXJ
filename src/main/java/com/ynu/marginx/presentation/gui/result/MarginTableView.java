package com.ynu.marginx.presentation.gui.result;

import com.ynu.marginx.domain.model.margin.ElementMargin;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * The per-element numbers behind the chart, the same figures detail_out.cpp printed.
 *
 * <p>Rows appear as each element finishes rather than all at once, so a long run fills the table
 * in front of the user instead of showing nothing until the end. They appear in the circuit's own
 * order all the same - see {@link #put}.
 */
public final class MarginTableView extends TableView<ElementMargin> {

    private final ObservableList<ElementMargin> rows = FXCollections.observableArrayList();

    /** Where each row's element sits in the netlist, kept in step with {@link #rows}. */
    private final List<Integer> indices = new ArrayList<>();

    public MarginTableView() {
        setItems(rows);
        setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        setPlaceholder(new javafx.scene.control.Label("No results yet."));

        getColumns().add(text("Element", entry -> entry.displayName()));
        getColumns().add(number("Value", entry -> entry.element().value(), "%.3f"));
        getColumns().add(text("Unit", entry -> entry.element().unit()));
        getColumns().add(number("Lower [%]", entry -> entry.margin().lowerPercent(), "%.2f"));
        getColumns().add(number("Upper [%]", entry -> entry.margin().upperPercent(), "%.2f"));
        getColumns().add(number("Lower value", ElementMargin::lowerValue, "%.3f"));
        getColumns().add(number("Upper value", ElementMargin::upperValue, "%.3f"));
        getColumns().add(number("Median", ElementMargin::medianValue, "%.3f"));
    }

    /**
     * Puts one finished element in its place. Call on the JavaFX thread.
     *
     * <p>The results arrive from whichever worker finished first, so appending them would order
     * the table by how long each element took to measure - different on every run, and not the
     * order the chart draws, which is the circuit's own. The index is where the element sits in
     * the netlist, and that is where its row goes.
     */
    public void put(int index, ElementMargin result) {
        int position = Collections.binarySearch(indices, index);
        // A negative result is the insertion point, which is what an unfinished element gives.
        int insertAt = position >= 0 ? position : -(position + 1);
        indices.add(insertAt, index);
        rows.add(insertAt, result);
    }

    public void clear() {
        rows.clear();
        indices.clear();
    }

    public ObservableList<ElementMargin> rows() {
        return rows;
    }

    /** Selects by name, so a click on the chart can move the table. */
    public void select(String elementName) {
        for (ElementMargin row : rows) {
            if (row.displayName().equals(elementName)) {
                getSelectionModel().select(row);
                scrollTo(row);
                return;
            }
        }
    }

    private TableColumn<ElementMargin, String> text(String title,
                                                    java.util.function.Function<ElementMargin, String> value) {
        TableColumn<ElementMargin, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        return column;
    }

    private TableColumn<ElementMargin, String> number(String title, ToDoubleFunction<ElementMargin> value,
                                                      String format) {
        TableColumn<ElementMargin, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                String.format(Locale.ROOT, format, value.applyAsDouble(cell.getValue()))));
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
        return column;
    }
}
