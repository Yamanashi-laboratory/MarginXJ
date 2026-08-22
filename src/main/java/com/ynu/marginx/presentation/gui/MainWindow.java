package com.ynu.marginx.presentation.gui;

import com.ynu.marginx.application.CalculateMarginUseCase;
import com.ynu.marginx.application.OptimizationReport;
import com.ynu.marginx.application.OptimizeCircuitUseCase;
import com.ynu.marginx.application.ScoreChoice;
import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.domain.model.optimize.OptimizationOutcome;
import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.domain.port.MarginResultRepository;
import com.ynu.marginx.domain.port.NetlistRepository;
import com.ynu.marginx.domain.service.BinarySearchMarginSearcher;
import com.ynu.marginx.domain.service.CenterOfGravityOptimizer;
import com.ynu.marginx.domain.service.CriticalElementFinder;
import com.ynu.marginx.domain.service.CriticalMarginCalculator;
import com.ynu.marginx.domain.service.CriticalMarginMethod;
import com.ynu.marginx.domain.service.ExhaustiveMarginSearcher;
import com.ynu.marginx.domain.service.MarginSearcher;
import com.ynu.marginx.domain.service.OperationEvaluator;
import com.ynu.marginx.domain.service.OperationJudge;
import com.ynu.marginx.domain.service.RandomSource;
import com.ynu.marginx.domain.service.ScoreCalculator;
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
import com.ynu.marginx.presentation.gui.editor.EditorPane;
import com.ynu.marginx.presentation.gui.export.ResultExporter;
import com.ynu.marginx.presentation.gui.result.MarginChartView;
import com.ynu.marginx.presentation.gui.result.MarginTableView;
import com.ynu.marginx.presentation.gui.settings.SimulatorSettingsDialog;
import com.ynu.marginx.presentation.gui.settings.SimulatorSettingsModel;
import com.ynu.marginx.presentation.gui.task.MarginCalculationTask;
import com.ynu.marginx.presentation.gui.task.OptimizationTask;
import com.ynu.marginx.shared.exception.MarginXException;
import java.io.File;
import java.nio.file.Path;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.concurrent.Task;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

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
    private final ChoiceBox<Mode> modeChoice = new ChoiceBox<>();
    private final ChoiceBox<ScoreChoice> scoreChoice = new ChoiceBox<>();
    private final Label scoreLabel = new Label("Maximise:");
    private final Button runButton = new Button("Run");
    private final Button cancelButton = new Button("Cancel");
    private final Button exportPngButton = new Button("Export PNG");
    private final Button exportCsvButton = new Button("Export CSV");
    private final MarginChartView chart = new MarginChartView();
    private final MarginTableView table = new MarginTableView();
    private final EditorPane editor = new EditorPane();

    private final SimulatorRegistry registry;
    private final UserSimulatorSettings userSettings;
    private SimulatorRegistry.Selection selection;
    private Path circuitFile;
    private Task<?> task;
    /** How to wait for the finished task's workers, whichever kind of task it was. */
    private Runnable awaitCleanup;

    /**
     * Everything this window can run, in the order the C++ menu lists it: measure the margins as
     * they are, or move the circuit and measure again.
     */
    private enum Mode {
        EXHAUSTIVE("Margin: accurate (decade refinement)", false, false),
        BINARY("Margin: binary search", false, false),
        SYNCHRONISED("Margin: accurate, synchronised groups", false, false),
        CRITICAL_MARGIN("Optimise: Critical Margin Method", true, false),
        CENTER_OF_GRAVITY("Optimise: Center of Gravity (CGM)", true, true),
        SEQUENTIAL_CGM("Optimise: sequential CGM", true, true);

        private final String label;
        private final boolean optimises;
        private final boolean usesScore;

        Mode(String label, boolean optimises, boolean usesScore) {
            this.label = label;
            this.optimises = optimises;
            this.usesScore = usesScore;
        }

        /** Whether the run moves the circuit, which is what decides where its result is written. */
        boolean optimises() {
            return optimises;
        }

        /** Only the CGM variants maximise a score; the Critical Margin Method has nothing to pick. */
        boolean usesScore() {
            return usesScore;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public MainWindow() {
        this(UserSimulatorSettings.inDefaultLocation());
    }

    private MainWindow(UserSimulatorSettings userSettings) {
        this(new SimulatorRegistry(SimulatorProperties.load(), userSettings,
                new NetlistRenderer(), new ProcessExecutor()), userSettings);
    }

    MainWindow(SimulatorRegistry registry) {
        this(registry, UserSimulatorSettings.inDefaultLocation());
    }

    MainWindow(SimulatorRegistry registry, UserSimulatorSettings userSettings) {
        this.registry = registry;
        this.userSettings = userSettings;
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

        modeChoice.getItems().addAll(Mode.values());
        modeChoice.setValue(Mode.BINARY);
        modeChoice.valueProperty().addListener((observable, previous, mode) -> showScoreChoice(mode));

        scoreChoice.getItems().addAll(ScoreChoice.values());
        scoreChoice.setValue(ScoreChoice.CRITICAL);
        scoreChoice.setConverter(new ScoreChoiceLabels());
        showScoreChoice(modeChoice.getValue());

        runButton.setOnAction(event -> run());
        runButton.setDefaultButton(true);
        cancelButton.setOnAction(event -> cancel());
        exportPngButton.setOnAction(event -> exportPng());
        exportCsvButton.setOnAction(event -> exportCsv());

        Button simulatorButton = new Button("Simulators...");
        simulatorButton.setOnAction(event -> openSimulatorSettings());

        HBox first = new HBox(8, chooseCircuit, circuitLabel);
        first.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox second = new HBox(8, new Label("Mode:"), modeChoice, scoreLabel, scoreChoice,
                runButton, cancelButton, spacer, exportPngButton, exportCsvButton);
        second.setAlignment(Pos.CENTER_LEFT);

        HBox simulatorRow = new HBox(8, simulatorLabel, simulatorButton);
        simulatorRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, first, second, simulatorRow);
        box.setPadding(new Insets(0, 0, 10, 0));
        return box;
    }

    private TabPane results() {
        SplitPane split = new SplitPane(chart, table);
        split.setDividerPositions(0.55);

        Tab resultsTab = new Tab("Results", split);
        resultsTab.setClosable(false);
        // The editor gets a tab of its own: the judgement file will join it here as a second pane.
        Tab netlistTab = new Tab("Netlist", editor);
        // A star on the tab is the reminder that the file on disk is not what is on screen.
        editor.modifiedProperty().addListener((observable, previous, dirty) ->
                netlistTab.setText(dirty ? "Netlist *" : "Netlist"));
        netlistTab.setClosable(false);

        TabPane tabs = new TabPane(resultsTab, netlistTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return tabs;
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

    /**
     * Neither simulator ships with MarginXJ, so somebody who only ever opens the window needs a
     * way to say where theirs is. Re-resolving on close means the label reflects the change at
     * once rather than at the next start.
     */
    private void openSimulatorSettings() {
        new SimulatorSettingsDialog(new SimulatorSettingsModel(registry, userSettings), window())
                .showAndWait();
        resolveSimulator();
        updateButtons(false);
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
            loadIntoEditor(circuitFile);
            updateButtons(false);
        }
    }

    private void loadIntoEditor(Path file) {
        try {
            editor.open(file);
        } catch (MarginXException e) {
            report(e);
        }
    }

    /**
     * The calculation reads the files from disk, so edits that have not been written out would be
     * measured as they were before. Asking beats silently measuring the wrong thing.
     */
    private boolean readyToRun() {
        if (!editor.modifiedProperty().get()) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "The netlist has been edited. Save it before running?",
                ButtonType.CANCEL, ButtonType.NO, ButtonType.YES);
        alert.setHeaderText("Unsaved changes");
        alert.initOwner(window());
        ButtonType answer = alert.showAndWait().orElse(ButtonType.CANCEL);
        if (answer == ButtonType.YES) {
            editor.save();
            return true;
        }
        return answer == ButtonType.NO;
    }

    private void run() {
        if (circuitFile == null || selection == null || !readyToRun()) {
            return;
        }
        Path directory = circuitFile.getParent();
        String baseName = stripExtension(circuitFile.getFileName().toString());
        try {
            NetlistRepository netlists = new FileNetlistRepository(directory, new NetlistParser());
            Netlist netlist = netlists.load(baseName);
            JudgementSpec spec =
                    new FileJudgementSpecRepository(directory, new JudgementSpecParser()).load(baseName);

            table.clear();
            chart.clear();
            progress.setProgress(0);

            CircuitSimulator simulator = selection.simulator();
            OperationEvaluator evaluator = new OperationEvaluator(simulator, new OperationJudge());
            MarginResultRepository results = new FileMarginResultRepository(directory,
                    new FileMarginResultRepository.Provenance(simulator.displayName(), simulator.name()));

            Mode mode = modeChoice.getValue();
            start(mode.optimises()
                    ? optimisation(mode, evaluator, netlists, results, netlist, spec, baseName)
                    : measurement(evaluator, results, netlist, spec));
        } catch (MarginXException e) {
            report(e);
        }
    }

    /** Binds the progress reporting and runs it. What to do when it ends is already on the task. */
    private void start(Task<?> started) {
        task = started;
        progress.progressProperty().bind(started.progressProperty());
        statusLabel.textProperty().bind(started.messageProperty());
        started.setOnFailed(event -> {
            stopped("The run stopped with an error.");
            report(started.getException());
        });
        updateButtons(true);
        Thread worker = new Thread(started, "marginx-calculation");
        worker.setDaemon(true);
        worker.start();
    }

    private MarginCalculationTask measurement(OperationEvaluator evaluator, MarginResultRepository results,
                                              Netlist netlist, JudgementSpec spec) {
        MarginCalculationTask started = new MarginCalculationTask(
                new CalculateMarginUseCase(searcher(evaluator), results), netlist, spec, table::add);
        started.setOnSucceeded(event -> finished(started.getValue()));
        started.setOnCancelled(event ->
                stopped("Stopped after " + table.rows().size() + " elements."));
        awaitCleanup = started::awaitCleanup;
        return started;
    }

    /**
     * An optimisation moves the circuit and measures it again, over and over. The window follows it
     * measurement by measurement, so the chart shows where the circuit has got to rather than
     * nothing at all until the run ends.
     */
    private OptimizationTask optimisation(Mode mode, OperationEvaluator evaluator,
                                          NetlistRepository netlists, MarginResultRepository results,
                                          Netlist netlist, JudgementSpec spec, String baseName) {
        CenterOfGravityOptimizer.Settings settings = CenterOfGravityOptimizer.Settings.defaults();
        boolean centreOfGravity = mode.usesScore();
        ScoreChoice score = scoreChoice.getValue();
        OptimizationTask started = new OptimizationTask(netlists, results,
                // Cycles for the CGM; the Critical Margin Method has only its trial limit to go on.
                centreOfGravity ? settings.cycles() : 0,
                centreOfGravity ? 0 : CriticalMarginMethod.MAX_TRIALS + 1,
                this::showMeasurement,
                useCase -> centreOfGravity
                        ? useCase.withCenterOfGravity(
                                centreOfGravity(mode, useCase, evaluator, settings), netlist, spec,
                                score.weights())
                        : useCase.withCriticalMarginMethod(
                                criticalMargin(useCase, evaluator), netlist, spec));
        started.setOnSucceeded(event -> finished(started.getValue(), baseName));
        started.setOnCancelled(event -> stopped("Cancelled. The chart shows the last measurement."));
        awaitCleanup = started::awaitCleanup;
        return started;
    }

    private CriticalMarginMethod criticalMargin(OptimizeCircuitUseCase useCase, OperationEvaluator evaluator) {
        // The C++ tool measures once with the exhaustive search and re-measures with the binary one.
        return new CriticalMarginMethod(
                useCase.measurements(new ExhaustiveMarginSearcher(evaluator)),
                useCase.measurements(new BinarySearchMarginSearcher(evaluator)),
                new CriticalElementFinder());
    }

    private CenterOfGravityOptimizer centreOfGravity(Mode mode, OptimizeCircuitUseCase useCase,
                                                     OperationEvaluator evaluator,
                                                     CenterOfGravityOptimizer.Settings settings) {
        CriticalMarginCalculator criticalMargins = new CriticalMarginCalculator();
        return new CenterOfGravityOptimizer(
                useCase.sampling(evaluator, settings.cycles()),
                useCase.measurements(new BinarySearchMarginSearcher(evaluator)), criticalMargins,
                new ScoreCalculator(criticalMargins), RandomSource.unseeded(), settings,
                mode == Mode.SEQUENTIAL_CGM
                        ? CenterOfGravityOptimizer.Variant.SEQUENTIAL
                        : CenterOfGravityOptimizer.Variant.YIELD_UP);
    }

    private MarginSearcher searcher(OperationEvaluator evaluator) {
        return switch (modeChoice.getValue()) {
            case BINARY -> new BinarySearchMarginSearcher(evaluator);
            case SYNCHRONISED -> ExhaustiveMarginSearcher.synchronizingGroups(evaluator);
            default -> new ExhaustiveMarginSearcher(evaluator);
        };
    }

    /** Shows a whole table at once, which is how a re-measurement arrives. */
    private void showMeasurement(MarginTable measured) {
        table.clear();
        for (int index = 0; index < measured.size(); index++) {
            table.add(measured.get(index));
        }
        chart.show(measured);
    }

    private void showScoreChoice(Mode mode) {
        boolean shown = mode != null && mode.usesScore();
        scoreLabel.setVisible(shown);
        scoreLabel.setManaged(shown);
        scoreChoice.setVisible(shown);
        scoreChoice.setManaged(shown);
    }

    private void cancel() {
        if (task != null) {
            task.cancel();
        }
    }

    private void finished(MarginTable result) {
        chart.show(result);
        settle();
        statusLabel.setText("Done: " + result.size() + " elements.");
    }

    private void finished(OptimizationOutcome outcome, String baseName) {
        showMeasurement(outcome.margins());
        settle();
        statusLabel.setText("Stopped after " + outcome.trials() + " trial(s): "
                + OptimizationReport.explain(outcome.reason())
                + ". Optimised circuit written to " + baseName + "_out.cir");
    }

    /** Cancelled or failed: whatever arrived before that is still worth plotting. */
    private void stopped(String message) {
        chart.show(new MarginTable(table.rows()));
        settle();
        progress.setProgress(0);
        statusLabel.setText(message);
    }

    /** Hands the window back to the user: unbind, re-enable, and let the workers finish unwinding. */
    private void settle() {
        progress.progressProperty().unbind();
        statusLabel.textProperty().unbind();
        updateButtons(false);
        if (awaitCleanup != null) {
            awaitCleanup.run();
        }
    }

    private void updateButtons(boolean running) {
        runButton.setDisable(running || circuitFile == null || selection == null);
        cancelButton.setDisable(!running);
        modeChoice.setDisable(running);
        scoreChoice.setDisable(running);
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

    /** The menu wording from the original, which is what the score is known by in the paper. */
    private static final class ScoreChoiceLabels extends StringConverter<ScoreChoice> {

        @Override
        public String toString(ScoreChoice choice) {
            return choice == null ? "" : choice.label();
        }

        @Override
        public ScoreChoice fromString(String label) {
            throw new UnsupportedOperationException("The list is not editable");
        }
    }
}
