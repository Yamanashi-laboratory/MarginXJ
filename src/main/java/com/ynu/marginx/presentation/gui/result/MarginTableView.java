package com.ynu.marginx.presentation.gui.result;

import com.ynu.marginx.domain.model.margin.ElementMargin;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * The per-element numbers behind the chart, the same figures detail_out.cpp printed.
 *
 * <p>Rows are appended as each element finishes rather than all at once, so a long run fills the
 * table in front of the user instead of showing nothing until the end.
 */
public final class MarginTableView extends TableView<ElementMargin> {

    private final ObservableList<ElementMargin> rows = FXCollections.observableArrayList();

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

    /** Appends one finished element. Call on the JavaFX thread. */
    public void add(ElementMargin result) {
        rows.add(result);
    }

    public void clear() {
        rows.clear();
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
