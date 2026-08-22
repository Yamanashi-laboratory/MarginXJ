package com.ynu.marginx.presentation.gui.settings;

import com.ynu.marginx.infrastructure.config.SimulatorKind;
import java.io.File;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Where the user points MarginXJ at their JoSIM and JSIM.
 *
 * <p>Neither simulator ships with MarginXJ, so on a fresh machine this is the first thing a user
 * needs. Until now it could only be done from the command line, which is not much use to someone
 * who opened the window by double-clicking it.
 */
public final class SimulatorSettingsDialog extends Dialog<Void> {

    private static final String OK_STYLE = "-fx-text-fill: #2c7a2c;";
    private static final String WARNING_STYLE = "-fx-text-fill: #b00020; -fx-font-weight: bold;";

    private final SimulatorSettingsModel model;
    private final Map<SimulatorKind, Label> states = new EnumMap<>(SimulatorKind.class);
    private final Map<SimulatorKind, Label> paths = new EnumMap<>(SimulatorKind.class);
    private final Map<SimulatorKind, Button> clearButtons = new EnumMap<>(SimulatorKind.class);

    public SimulatorSettingsDialog(SimulatorSettingsModel model, Window owner) {
        this.model = model;
        setTitle("Simulators");
        setHeaderText("MarginXJ runs JoSIM, or JSIM when JoSIM is not installed.\n"
                + "Neither is bundled; point MarginXJ at the copy you have.");
        initOwner(owner);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(12);
        content.setPadding(new Insets(10, 4, 4, 4));
        for (SimulatorKind kind : SimulatorKind.values()) {
            content.getChildren().add(row(kind));
            content.getChildren().add(new Separator());
        }
        Label file = new Label("Saved in " + model.settingsFile());
        file.setStyle("-fx-font-size: 10px; -fx-text-fill: #606060;");
        content.getChildren().add(file);

        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(620);
        refresh();
    }

    private VBox row(SimulatorKind kind) {
        Label name = new Label(kind.displayName());
        name.setStyle("-fx-font-weight: bold;");
        Label state = new Label();
        states.put(kind, state);

        Label path = new Label();
        path.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        path.setWrapText(true);
        paths.put(kind, path);

        Button browse = new Button("Choose...");
        browse.setOnAction(event -> choose(kind));
        Button clear = new Button("Use PATH again");
        clear.setOnAction(event -> {
            model.clear(kind);
            refresh();
        });
        clearButtons.put(kind, clear);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, name, state, spacer, browse, clear);
        header.setAlignment(Pos.CENTER_LEFT);

        return new VBox(4, header, path);
    }

    private void choose(SimulatorKind kind) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose the " + kind.displayName() + " executable");
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Executable", "*.exe", "*.bat", "*.cmd"));
        }
        File chosen = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
        if (chosen != null) {
            model.save(kind, chosen.toPath());
            refresh();
        }
    }

    /** Re-resolves after every change, so the window always shows what a run would actually use. */
    private void refresh() {
        for (SimulatorSettingsModel.Status status : model.statuses()) {
            Label state = states.get(status.kind());
            state.setText(status.available() ? "found via " + status.source() : "not found");
            state.setStyle(status.available() ? OK_STYLE : WARNING_STYLE);

            Label path = paths.get(status.kind());
            path.setText(status.available() ? status.executable() : status.detail());

            // Nothing to clear unless this simulator is pinned by a saved path.
            clearButtons.get(status.kind()).setDisable(!status.savedHere());
        }
    }

    /** The settings file, so a caller can mention it after the window closes. */
    public Path settingsFile() {
        return model.settingsFile();
    }
}
