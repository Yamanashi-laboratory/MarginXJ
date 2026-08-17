package com.ynu.marginx.presentation.gui.editor;

import com.ynu.marginx.domain.model.circuit.CircuitElement;
import com.ynu.marginx.domain.model.circuit.Netlist;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.IntConsumer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * What the parser found in the netlist: every element a margin will be measured on.
 *
 * <p>The list is the parse result and nothing else, so it doubles as an answer to the question the
 * file format makes hardest - which of these lines actually get swept. An element that the user
 * expected to see and cannot find here is one whose designator starts with a capital.
 */
public final class ElementListView extends TableView<CircuitElement> {

    private final ObservableList<CircuitElement> elements = FXCollections.observableArrayList();

    public ElementListView(IntConsumer onLineChosen) {
        setItems(elements);
        setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        setPlaceholder(new Label("No margin targets."));

        getColumns().add(text("Line", element -> String.valueOf(element.lineNumber() + 1)));
        getColumns().add(text("Element", CircuitElement::displayName));
        getColumns().add(text("Type", element -> element.type().name().toLowerCase(Locale.ROOT)));
        getColumns().add(text("Value", element ->
                String.format(Locale.ROOT, "%.3f%s", element.value(), element.unit())));
        getColumns().add(text("Range", element -> String.format(Locale.ROOT, "%.3f .. %.3f",
                element.range().min(), element.range().max())));
        getColumns().add(text("Flags", ElementListView::flagsOf));

        // A click is how the user gets from the list back to the line that produced it.
        getSelectionModel().selectedItemProperty().addListener((observable, previous, element) -> {
            if (element != null) {
                onLineChosen.accept(element.lineNumber());
            }
        });
    }

    public void show(Netlist netlist) {
        elements.setAll(netlist.elements());
    }

    public void clear() {
        elements.clear();
    }

    public List<CircuitElement> rows() {
        return List.copyOf(elements);
    }

    private static String flagsOf(CircuitElement element) {
        StringBuilder flags = new StringBuilder();
        if (element.fixed()) {
            flags.append("FIX ");
        }
        if (element.synchronizationGroup() != 0) {
            flags.append("SYN").append(element.synchronizationGroup()).append(' ');
        }
        if (element.hasShuntLine()) {
            flags.append("shunt");
        }
        return flags.toString().trim();
    }

    private TableColumn<CircuitElement, String> text(String title, Function<CircuitElement, String> value) {
        TableColumn<CircuitElement, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        return column;
    }
}
