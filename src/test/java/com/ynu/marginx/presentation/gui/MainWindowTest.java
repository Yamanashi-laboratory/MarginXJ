package com.ynu.marginx.presentation.gui;

import static org.assertj.core.api.Assertions.assertThat;

import com.ynu.marginx.application.ScoreChoice;
import com.ynu.marginx.domain.service.CenterOfGravityOptimizer;
import com.ynu.marginx.infrastructure.config.SimulatorProperties;
import com.ynu.marginx.infrastructure.config.UserSimulatorSettings;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.simulator.ProcessExecutor;
import com.ynu.marginx.infrastructure.simulator.SimulatorRegistry;
import com.ynu.marginx.testsupport.FxToolkit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Spinner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What the window offers, checked on the built scene graph rather than through a robot.
 *
 * <p>Nothing here runs a calculation: the point is that every mode the command line has is reachable
 * from the window, and that the score - which only two of them use - is asked for exactly when it
 * is going to be used.
 */
class MainWindowTest {

    @BeforeAll
    static void toolkit() {
        FxToolkit.startOrSkip();
    }

    @Test
    void offersEverySearchAndEveryOptimiser() {
        List<String> modes = FxToolkit.call(() -> modeChoice(window()).getItems().stream()
                .map(Object::toString)
                .toList());

        assertThat(modes).containsExactly(
                "Margin: accurate (decade refinement)",
                "Margin: binary search",
                "Margin: accurate, synchronised groups",
                "Optimise: Critical Margin Method",
                "Optimise: Center of Gravity (CGM)",
                "Optimise: sequential CGM");
    }

    @Test
    void asksForTheScoreOnlyWhenAnOptimiserWillUseIt() {
        FxToolkit.run(() -> {
            MainWindow window = window();
            ChoiceBox<?> mode = modeChoice(window);
            ChoiceBox<ScoreChoice> score = scoreChoice(window);

            // A margin measurement maximises nothing, so the choice is not merely disabled but gone.
            assertThat(score.isManaged()).isFalse();

            select(mode, "Optimise: Center of Gravity (CGM)");
            assertThat(score.isManaged()).isTrue();
            assertThat(score.getItems()).containsExactly(ScoreChoice.values());

            select(mode, "Optimise: sequential CGM");
            assertThat(score.isManaged()).isTrue();

            // The Critical Margin Method centres whatever is tightest; there is nothing to pick.
            select(mode, "Optimise: Critical Margin Method");
            assertThat(score.isManaged()).isFalse();
        });
    }

    @Test
    void offersACycleLimitOnlyForTheRunsThatHaveCycles() {
        FxToolkit.run(() -> {
            MainWindow window = window();
            ChoiceBox<?> mode = modeChoice(window);
            Spinner<?> cycles = spinner(window);

            // A margin measurement has no cycles, and neither has the Critical Margin Method.
            assertThat(cycles.isManaged()).isFalse();

            select(mode, "Optimise: Center of Gravity (CGM)");
            assertThat(cycles.isManaged()).isTrue();
            // The default has to be the value the original uses, or a plain run would not match it.
            assertThat(cycles.getValue()).isEqualTo(CenterOfGravityOptimizer.Settings.defaults().cycles());

            select(mode, "Optimise: Critical Margin Method");
            assertThat(cycles.isManaged()).isFalse();
        });
    }

    @Test
    void namesTheScoresTheWayTheOriginalMenuDoes() {
        String shown = FxToolkit.call(() -> scoreChoice(window()).getConverter()
                .toString(ScoreChoice.CRITICAL_AND_DOUBLE_BIAS));

        assertThat(shown).isEqualTo(ScoreChoice.CRITICAL_AND_DOUBLE_BIAS.label());
    }

    private MainWindow window() {
        return new MainWindow(new SimulatorRegistry(
                new SimulatorProperties("josim", "jsim", Duration.ofSeconds(1)),
                UserSimulatorSettings.inDefaultLocation(), new NetlistRenderer(), new ProcessExecutor()));
    }

    /** The mode list is the first choice box in the controls; the score list follows it. */
    private ChoiceBox<?> modeChoice(MainWindow window) {
        return choiceBoxes(window).get(0);
    }

    @SuppressWarnings("unchecked")
    private ChoiceBox<ScoreChoice> scoreChoice(MainWindow window) {
        return (ChoiceBox<ScoreChoice>) choiceBoxes(window).get(1);
    }

    private Spinner<?> spinner(MainWindow window) {
        List<Spinner<?>> found = new ArrayList<>();
        collectSpinners(window, found);
        return found.get(0);
    }

    private void collectSpinners(Node node, List<Spinner<?>> found) {
        if (node instanceof Spinner<?> spinner) {
            found.add(spinner);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectSpinners(child, found);
            }
        }
    }

    private List<ChoiceBox<?>> choiceBoxes(MainWindow window) {
        List<ChoiceBox<?>> found = new ArrayList<>();
        collect(window, found);
        return found;
    }

    private void collect(Node node, List<ChoiceBox<?>> found) {
        if (node instanceof ChoiceBox<?> choice) {
            found.add(choice);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, found);
            }
        }
    }

    private void select(ChoiceBox<?> choice, String label) {
        for (Object item : choice.getItems()) {
            if (item.toString().equals(label)) {
                selectItem(choice, item);
                return;
            }
        }
        throw new AssertionError("No such mode: " + label);
    }

    @SuppressWarnings("unchecked")
    private void selectItem(ChoiceBox<?> choice, Object item) {
        ((ChoiceBox<Object>) choice).setValue(item);
    }
}
