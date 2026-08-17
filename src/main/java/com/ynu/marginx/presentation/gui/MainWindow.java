package com.ynu.marginx.presentation.gui;

import com.ynu.marginx.application.CalculateMarginUseCase;
import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.domain.service.BinarySearchMarginSearcher;
import com.ynu.marginx.domain.service.ExhaustiveMarginSearcher;
import com.ynu.marginx.domain.service.MarginSearcher;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationJudge;
import com.ynu.marginx.infrastructure.config.SimulatorProperties;
import com.ynu.marginx.infrastructure.config.UserSimulatorSettings;
import com.ynu.marginx.infrastructure.judgement.FileJudgementSpecRepository;
import com.ynu.marginx.infrastructure.judgement.JudgementSpecParser;
import com.ynu.marginx.infrastructure.netlist.FileNetlistRepository;
import com.ynu.marginx.infrastructure.netlist.NetlistParser;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.result.FileMarginResultRepository;
import com.ynu.marginx.infrastructure.simulator.ProcessExecutor;
import com.ynu.marginx.infrastructure.simulator.SimulatorRegistry;
import com.ynu.marginx.presentation.gui.export.ResultExporter;
import com.ynu.marginx.presentation.gui.result.MarginChartView;
import com.ynu.marginx.presentation.gui.result.MarginTableView;
import com.ynu.marginx.presentation.gui.task.MarginCalculationTask;
import com.ynu.marginx.shared.exception.MarginXException;
import java.io.File;
import java.nio.file.Path;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * The margin window: pick a circuit, run it, watch the results arrive.
 *
 * <p>Everything long-running happens in a {@link MarginCalculationTask}; this class only starts it,
 * shows what it reports and can stop it. The simulator in use is on screen the whole time, marked
 * in a warning colour whenever JSIM has stood in for JoSIM, because the two do not have to agree.
 */
public final class MainWindow extends BorderPane {

    private static final String WARNING_STYLE = "-fx-text-fill: #b35c00; -fx-font-weight: bold;";
    private static final String ORDINARY_STYLE = "-fx-text-fill: -fx-text-base-color;";

    private final Label circuitLabel = new Label("No circuit chosen.");
    private final Label simulatorLabel = new Label();
    private final Label statusLabel = new Label();
    private final ProgressBar progress = new ProgressBar(0);
    private final ChoiceBox<SearchMode> modeChoice = new ChoiceBox<>();
    private final Button runButton = new Button("Run");
    private final Button cancelButton = new Button("Cancel");
    private final Button exportPngButton = new Button("Export PNG");
    private final Button exportCsvButton = new Button("Export CSV");
    private final MarginChartView chart = new MarginChartView();
    private final MarginTableView table = new MarginTableView();

    private final SimulatorRegistry registry;
    private SimulatorRegistry.Selection selection;
    private Path circuitFile;
    private MarginCalculationTask task;

    /** The margin searches this window offers; the optimisers are a later step. */
    private enum SearchMode {
        EXHAUSTIVE("Accurate (decade refinement)"),
        BINARY("Binary search"),
        SYNCHRONISED("Accurate, synchronised groups");

        private final String label;

        SearchMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public MainWindow() {
        this(new SimulatorRegistry(SimulatorProperties.load(), UserSimulatorSettings.inDefaultLocation(),
                new NetlistRenderer(), new ProcessExecutor()));
    }

    MainWindow(SimulatorRegistry registry) {
        this.registry = registry;
        setTop(controls());
        setCenter(results());
        setBottom(statusBar());
        setPadding(new Insets(10));
        resolveSimulator();
        updateButtons(false);
        wireSelection();
    }

    private VBox controls() {
        Button chooseCircuit = new Button("Choose circuit...");
        chooseCircuit.setOnAction(event -> chooseCircuit());

        modeChoice.getItems().addAll(SearchMode.values());
        modeChoice.setValue(SearchMode.BINARY);

        runButton.setOnAction(event -> run());
        runButton.setDefaultButton(true);
        cancelButton.setOnAction(event -> cancel());
        exportPngButton.setOnAction(event -> exportPng());
        exportCsvButton.setOnAction(event -> exportCsv());

        HBox first = new HBox(8, chooseCircuit, circuitLabel);
        first.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox second = new HBox(8, new Label("Mode:"), modeChoice, runButton, cancelButton,
                spacer, exportPngButton, exportCsvButton);
        second.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, first, second, simulatorLabel);
        box.setPadding(new Insets(0, 0, 10, 0));
        return box;
    }

    private SplitPane results() {
        SplitPane split = new SplitPane(chart, table);
        split.setDividerPositions(0.55);
        return split;
    }

    private HBox statusBar() {
        progress.setPrefWidth(240);
        HBox bar = new HBox(10, progress, statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 0, 0, 0));
        return bar;
    }

    /**
     * Resolving can fail outright when no simulator is installed. That must not stop the window
     * from opening - the user still needs to see why, and where to get one.
     */
    private void resolveSimulator() {
        try {
            selection = registry.resolve();
            String executable = selection.simulator().name();
            simulatorLabel.setText("Simulator: " + selection.simulator().displayName() + "  (" + executable + ")");
            if (selection.fallback()) {
                simulatorLabel.setStyle(WARNING_STYLE);
                simulatorLabel.setText("Simulator: " + selection.simulator().displayName()
                        + " - substituted for JoSIM  (" + executable + ")");
                Tooltip.install(simulatorLabel, new Tooltip(selection.warning()));
            } else {
                simulatorLabel.setStyle(ORDINARY_STYLE);
                Tooltip.install(simulatorLabel, new Tooltip("Resolved from " + executable));
            }
        } catch (MarginXException e) {
            selection = null;
            simulatorLabel.setStyle(WARNING_STYLE);
            simulatorLabel.setText("No simulator found. Install JoSIM to run a calculation.");
            Tooltip.install(simulatorLabel, new Tooltip(e.getMessage()));
        }
    }

    private void wireSelection() {
        table.getSelectionModel().selectedItemProperty().addListener((observable, previous, row) -> {
            if (row != null) {
                chart.select(row.displayName());
            }
        });
        chart.selectedElementProperty().addListener((observable, previous, elementName) -> {
            if (elementName != null) {
                table.select(elementName);
            }
        });
    }

    private void chooseCircuit() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a circuit netlist");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Circuit netlist", "*.cir", "*.inp"));
        File chosen = chooser.showOpenDialog(window());
        if (chosen != null) {
            circuitFile = chosen.toPath().toAbsolutePath();
            circuitLabel.setText(circuitFile.toString());
            updateButtons(false);
        }
    }

    private void run() {
        if (circuitFile == null || selection == null) {
            return;
        }
        Path directory = circuitFile.getParent();
        String baseName = stripExtension(circuitFile.getFileName().toString());
        try {
            Netlist netlist = new FileNetlistRepository(directory, new NetlistParser()).load(baseName);
            JudgementSpec spec =
                    new FileJudgementSpecRepository(directory, new JudgementSpecParser()).load(baseName);

            table.clear();
            chart.clear();
            progress.setProgress(0);

            CircuitSimulator simulator = selection.simulator();
            OperationEvaluator evaluator = new OperationEvaluator(simulator, new OperationJudge());
            MarginResultRepository results = new FileMarginResultRepository(directory,
                    new FileMarginResultRepository.Provenance(simulator.displayName(), simulator.name()));

            task = new MarginCalculationTask(
                    new CalculateMarginUseCase(searcher(evaluator), results), netlist, spec, table::add);
            progress.progressProperty().bind(task.progressProperty());
            statusLabel.textProperty().bind(task.messageProperty());
            task.setOnSucceeded(event -> finish(task.getValue()));
            task.setOnCancelled(event -> finish(null));
            task.setOnFailed(event -> {
                finish(null);
                report(task.getException());
            });

            updateButtons(true);
            Thread worker = new Thread(task, "marginx-calculation");
            worker.setDaemon(true);
            worker.start();
        } catch (MarginXException e) {
            report(e);
        }
    }

    private MarginSearcher searcher(OperationEvaluator evaluator) {
        return switch (modeChoice.getValue()) {
            case BINARY -> new BinarySearchMarginSearcher(evaluator);
            case SYNCHRONISED -> ExhaustiveMarginSearcher.synchronizingGroups(evaluator);
            case EXHAUSTIVE -> new ExhaustiveMarginSearcher(evaluator);
        };
    }

    private void cancel() {
        if (task != null) {
            task.cancel();
        }
    }

    private void finish(MarginTable result) {
        progress.progressProperty().unbind();
        statusLabel.textProperty().unbind();
        if (result != null) {
            chart.show(result);
            statusLabel.setText("Done: " + result.size() + " elements.");
        } else {
            // Cancelled or failed: whatever arrived before that is still worth plotting.
            chart.show(new MarginTable(table.rows()));
            progress.setProgress(0);
            statusLabel.setText("Stopped after " + table.rows().size() + " elements.");
        }
        updateButtons(false);
        if (task != null) {
            task.awaitCleanup();
        }
    }

    private void updateButtons(boolean running) {
        runButton.setDisable(running || circuitFile == null || selection == null);
        cancelButton.setDisable(!running);
        modeChoice.setDisable(running);
        boolean hasRows = !table.rows().isEmpty();
        exportPngButton.setDisable(running || !hasRows);
        exportCsvButton.setDisable(running || !hasRows);
    }

    private void exportPng() {
        Path target = chooseSaveTarget("Export the chart", "PNG image", "*.png", ".png");
        if (target != null) {
            ResultExporter.writePng(target, chart.plot());
            statusLabel.setText("Wrote " + target);
        }
    }

    private void exportCsv() {
        Path target = chooseSaveTarget("Export the results", "CSV", "*.csv", ".csv");
        if (target != null) {
            ResultExporter.writeCsv(target, table.rows(),
                    selection.simulator().displayName(), selection.simulator().name());
            statusLabel.setText("Wrote " + target);
        }
    }

    private Path chooseSaveTarget(String title, String description, String pattern, String extension) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, pattern));
        if (circuitFile != null) {
            chooser.setInitialDirectory(circuitFile.getParent().toFile());
            chooser.setInitialFileName(stripExtension(circuitFile.getFileName().toString()) + extension);
        }
        File chosen = chooser.showSaveDialog(window());
        return chosen == null ? null : chosen.toPath();
    }

    private void report(Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR, error == null ? "Unknown error" : error.getMessage());
        alert.setHeaderText("The calculation could not be completed");
        alert.initOwner(window());
        alert.showAndWait();
    }

    private Window window() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }
}
