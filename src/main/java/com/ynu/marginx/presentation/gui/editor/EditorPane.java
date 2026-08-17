package com.ynu.marginx.presentation.gui.editor;

import com.ynu.marginx.shared.exception.MarginXException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Editing a circuit means editing two files, and MarginXJ finds the second one by name: the
 * judgement for {@code adder.cir} is {@code adder.txt} beside it. The pair is therefore opened,
 * shown and saved together, and the judgement name is displayed rather than asked for - there is
 * no way to save one of them under a name the calculation would not look for.
 */
public final class EditorPane extends BorderPane {

    private static final String OK_STYLE = "-fx-text-fill: #2c7a2c;";
    private static final String ERROR_STYLE = "-fx-text-fill: #b00020; -fx-font-weight: bold;";

    private final NetlistEditor netlistEditor = new NetlistEditor();
    private final NetlistEditor judgementEditor = new NetlistEditor();
    private final ElementListView elements = new ElementListView(this::goToNetlistLine);
    private final NetlistValidator validator = new NetlistValidator();

    private final Label netlistName = new Label("No circuit open.");
    private final Label judgementName = new Label();
    private final Label netlistStatus = new Label();
    private final Label judgementStatus = new Label();
    private final Button saveButton = new Button("Save");
    private final ReadOnlyBooleanWrapper modified = new ReadOnlyBooleanWrapper();

    private Path circuitFile;
    private Path judgementFile;

    public EditorPane() {
        netlistEditor.setOnSettled(text -> {
            markModified();
            validateNetlist(text);
        });
        judgementEditor.setOnSettled(text -> {
            markModified();
            validateJudgement(text);
        });
        saveButton.setOnAction(event -> save());
        saveButton.setDisable(true);

        setTop(toolbar());
        setCenter(panes());
        setPadding(new Insets(6, 0, 0, 0));
    }

    private HBox toolbar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(10, saveButton, netlistName, netlistStatus, spacer, judgementName, judgementStatus);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 6, 0));
        return bar;
    }

    private SplitPane panes() {
        SplitPane split = new SplitPane(titled("Netlist", netlistEditor),
                titled("Judgement", judgementEditor),
                titled("Margin targets", elements));
        split.setDividerPositions(0.42, 0.68);
        return split;
    }

    private VBox titled(String title, javafx.scene.Node content) {
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold;");
        VBox box = new VBox(4, label, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        box.setPadding(new Insets(0, 6, 0, 6));
        return box;
    }

    /** True while either file has been edited since it was last written out. */
    public ReadOnlyBooleanProperty modifiedProperty() {
        return modified.getReadOnlyProperty();
    }

    /**
     * Opens a circuit and the judgement file that belongs to it. A judgement file that is not there
     * yet opens empty rather than failing: writing one is a perfectly ordinary thing to be doing.
     */
    public void open(Path circuit) {
        this.circuitFile = circuit.toAbsolutePath();
        this.judgementFile = judgementFor(circuitFile);
        netlistName.setText(circuitFile.getFileName().toString());
        judgementName.setText(judgementFile.getFileName().toString());
        Tooltip.install(judgementName, new Tooltip(
                "The judgement file is found by name: " + judgementFile));

        netlistEditor.setText(read(circuitFile));
        judgementEditor.setText(Files.isRegularFile(judgementFile) ? read(judgementFile) : "");
        modified.set(false);
        saveButton.setDisable(true);
        validateNetlist(netlistEditor.getText());
        validateJudgement(judgementEditor.getText());
    }

    /** {@code adder.cir} is judged by {@code adder.txt}; the pairing is by name, not by choice. */
    static Path judgementFor(Path circuit) {
        String fileName = circuit.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot <= 0 ? fileName : fileName.substring(0, dot);
        return circuit.resolveSibling(baseName + ".txt");
    }

    public void save() {
        if (circuitFile == null) {
            return;
        }
        write(circuitFile, netlistEditor.getText());
        write(judgementFile, judgementEditor.getText());
        modified.set(false);
        saveButton.setDisable(true);
        validateNetlist(netlistEditor.getText());
        validateJudgement(judgementEditor.getText());
    }

    /**
     * Parsing happens on this thread on purpose. It is a few hundred lines of text and takes well
     * under a millisecond; the wait between keystroke and parse comes from the editor holding off
     * until typing stops, not from doing the work somewhere else.
     */
    private void validateNetlist(String text) {
        if (circuitFile == null) {
            return;
        }
        NetlistValidator.NetlistResult result =
                validator.validateNetlist(circuitFile.getFileName().toString(), text);
        netlistEditor.showParseResult(result);
        if (result.valid()) {
            elements.show(result.netlist());
            netlistStatus.setStyle(OK_STYLE);
            netlistStatus.setText(result.message());
        } else {
            elements.clear();
            netlistStatus.setStyle(ERROR_STYLE);
            netlistStatus.setText(result.line().isPresent()
                    ? "line " + (result.line().getAsInt() + 1) + ": " + result.message()
                    : result.message());
        }
    }

    private void validateJudgement(String text) {
        if (text.isBlank()) {
            judgementStatus.setStyle(ERROR_STYLE);
            judgementStatus.setText("empty");
            return;
        }
        NetlistValidator.JudgementResult result = validator.validateJudgement(text);
        judgementStatus.setStyle(result.valid() ? OK_STYLE : ERROR_STYLE);
        judgementStatus.setText(result.message());
    }

    private void goToNetlistLine(int lineNumber) {
        netlistEditor.goToLine(lineNumber);
        netlistEditor.area().requestFocus();
    }

    private void markModified() {
        if (circuitFile != null) {
            modified.set(true);
            saveButton.setDisable(false);
        }
    }

    private String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new MarginXException("Cannot read " + file, e);
        }
    }

    private void write(Path file, String text) {
        try {
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new MarginXException("Cannot write " + file, e);
        }
    }

    NetlistEditor netlistEditor() {
        return netlistEditor;
    }

    ElementListView elementList() {
        return elements;
    }

    String netlistStatusText() {
        return netlistStatus.getText().toLowerCase(Locale.ROOT);
    }
}
