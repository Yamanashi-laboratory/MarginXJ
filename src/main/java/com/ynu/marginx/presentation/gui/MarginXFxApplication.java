package com.ynu.marginx.presentation.gui;

import com.ynu.marginx.infrastructure.simulator.SimulatorWorkspaces;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The JavaFX entry point. {@link com.ynu.marginx.presentation.MarginX} calls {@link #start(String[])}
 * reflectively, which keeps JavaFX off the path of a plain command-line run.
 */
public final class MarginXFxApplication extends Application {

    private static final int WIDTH = 1100;
    private static final int HEIGHT = 720;

    /** Called by the router. Named so it does not clash with Application.start(Stage). */
    public static void start(String[] args) {
        launch(MarginXFxApplication.class, args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("MarginXJ");
        Scene scene = new Scene(new MainWindow(), WIDTH, HEIGHT);
        scene.getStylesheets().add(
                MarginXFxApplication.class.getResource("/netlist-editor.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        // Closing the window mid-calculation would otherwise leave the simulators running and
        // their working directories behind, the same way Ctrl+C does at the terminal.
        SimulatorWorkspaces.deleteRemaining();
    }
}
