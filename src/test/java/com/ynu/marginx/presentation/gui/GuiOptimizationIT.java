package com.ynu.marginx.presentation.gui;

import static org.assertj.core.api.Assertions.assertThat;

import com.ynu.marginx.infrastructure.config.SimulatorProperties;
import com.ynu.marginx.infrastructure.config.UserSimulatorSettings;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.simulator.ProcessExecutor;
import com.ynu.marginx.infrastructure.simulator.SimulatorRegistry;
import com.ynu.marginx.presentation.gui.result.MarginTableView;
import com.ynu.marginx.testsupport.FxToolkit;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a whole optimisation from the window, against the real simulator and a real circuit.
 *
 * <pre>./gradlew test -Dmarginx.it.josim=josim</pre>
 *
 * <p>The unit tests drive the task with a stub that answers instantly, which says nothing about
 * what happens over the minutes a real run takes: a hundred simulations per cycle, a re-measurement
 * every so often, and every one of those results crossing onto the JavaFX thread. This is the test
 * that runs long enough for that to matter.
 *
 * <p>It drives the window itself rather than the screen. Clicking at coordinates would mean
 * pressing whatever happened to be on top, and would make the check impossible to run unattended.
 */
@EnabledIfSystemProperty(named = "marginx.it.josim", matches = ".+")
class GuiOptimizationIT {

    /** A CGM run at the default settings takes minutes; the limit is only there to end a hang. */
    private static final Duration LIMIT = Duration.ofMinutes(20);

    private static final String CIRCUIT = "MUX_clked";
    private static final int TARGETS = 33;

    @TempDir
    Path workingDirectory;

    @BeforeAll
    static void toolkit() {
        FxToolkit.startOrSkip();
    }

    @Test
    void runsACentreOfGravityOptimisationToTheEnd() throws Exception {
        Path circuit = copyIn(CIRCUIT + ".cir");
        copyIn(CIRCUIT + ".txt");

        MainWindow window = FxToolkit.call(this::window);
        FxToolkit.run(() -> {
            choose(window, circuit);
            mode(window).setValue(modeNamed(window, "Optimise: Center of Gravity (CGM)"));
        });

        // Through the button, so that what runs is what a user's click would have run.
        Button run = FxToolkit.call(() -> button(window, "Run"));
        assertThat(FxToolkit.call(() -> !run.isDisabled()))
                .as("the run button must be live once a circuit is chosen")
                .isTrue();
        FxToolkit.run(run::fire);

        String status = awaitCompletion(window);

        assertThat(status).contains("Stopped after").contains(CIRCUIT + "_out.cir");
        // The optimised circuit is the thing the run exists to produce.
        assertThat(workingDirectory.resolve(CIRCUIT + "_out.cir")).exists();
        // Every target measured, and the table showing the circuit it settled on rather than an
        // empty one or the half-filled state of some intermediate measurement.
        assertThat(FxToolkit.call(() -> table(window).rows().size())).isEqualTo(TARGETS);
    }

    /** Polls the status line, which is where the window says how the run ended. */
    private String awaitCompletion(MainWindow window) throws Exception {
        Instant deadline = Instant.now().plus(LIMIT);
        while (Instant.now().isBefore(deadline)) {
            String status = FxToolkit.call(() -> label(window).getText());
            if (status != null && (status.startsWith("Stopped after") || status.startsWith("The run stopped"))) {
                return status;
            }
            Thread.sleep(2000);
        }
        throw new AssertionError("The optimisation had not finished after " + LIMIT);
    }

    private MainWindow window() {
        // The command under test is the one the run was given, not whatever is on PATH.
        SimulatorProperties properties = new SimulatorProperties(
                System.getProperty("marginx.it.josim"), "jsim", Duration.ofSeconds(120));
        return new MainWindow(new SimulatorRegistry(properties, UserSimulatorSettings.inDefaultLocation(),
                new NetlistRenderer(), new ProcessExecutor()));
    }

    /**
     * Puts the window in the state the file chooser would leave it in. The chooser itself is a
     * native dialog with nothing of ours in it, so there is nothing here worth driving.
     */
    private void choose(MainWindow window, Path circuit) {
        try {
            Field file = MainWindow.class.getDeclaredField("circuitFile");
            file.setAccessible(true);
            file.set(window, circuit);
            Method update = MainWindow.class.getDeclaredMethod("updateButtons", boolean.class);
            update.setAccessible(true);
            update.invoke(window, false);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("The window no longer looks the way this test drives it", e);
        }
    }

    private Path copyIn(String fileName) throws Exception {
        Path source = Path.of("test_circuits", fileName);
        Assumptions.assumeTrue(Files.exists(source), "the reference circuit is not in this checkout");
        Path target = workingDirectory.resolve(fileName);
        Files.copy(source, target);
        return target;
    }

    private Object modeNamed(MainWindow window, String label) {
        for (Object item : mode(window).getItems()) {
            if (item.toString().equals(label)) {
                return item;
            }
        }
        throw new AssertionError("No such mode: " + label);
    }

    @SuppressWarnings("unchecked")
    private ChoiceBox<Object> mode(MainWindow window) {
        return (ChoiceBox<Object>) find(window, ChoiceBox.class).get(0);
    }

    /** The status line, by name: picking it out of the scene graph by position would be a guess. */
    private Label label(MainWindow window) {
        return field(window, "statusLabel", Label.class);
    }

    private <T> T field(MainWindow window, String name, Class<T> type) {
        try {
            Field field = MainWindow.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(window));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("The window no longer looks the way this test drives it", e);
        }
    }

    private Button button(MainWindow window, String text) {
        return find(window, Button.class).stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No such button: " + text));
    }

    /**
     * By name again, and for a sharper reason than the status line: the table lives inside a
     * TabPane, whose contents are not children of anything until the window is on a stage. Walking
     * the scene graph finds nothing at all.
     */
    private MarginTableView table(MainWindow window) {
        return field(window, "table", MarginTableView.class);
    }

    private <T> List<T> find(Node node, Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(node, type, found);
        return found;
    }

    private <T> void collect(Node node, Class<T> type, List<T> found) {
        if (type.isInstance(node)) {
            found.add(type.cast(node));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, type, found);
            }
        }
    }
}
