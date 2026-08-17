package com.ynu.marginx.presentation.gui.editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ynu.marginx.domain.model.circuit.CircuitElement;
import com.ynu.marginx.testsupport.FxToolkit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The editor pane against a real scene graph: the two files, the target marking and saving. */
class EditorPaneTest {

    private static final Path REAL_CIRCUIT = Path.of("test_circuits", "MUX_clked.cir");
    private static final Path REAL_JUDGEMENT = Path.of("test_circuits", "MUX_clked.txt");

    @TempDir
    Path workingDirectory;

    @BeforeAll
    static void toolkit() {
        FxToolkit.startOrSkip();
    }

    @Test
    void pairsACircuitWithTheJudgementFileOfTheSameName() {
        assertThat(EditorPane.judgementFor(Path.of("/work/adder.cir")))
                .isEqualTo(Path.of("/work/adder.txt"));
        assertThat(EditorPane.judgementFor(Path.of("/work/adder.inp")))
                .isEqualTo(Path.of("/work/adder.txt"));
    }

    @Test
    void opensBothFilesAndListsTheMarginTargets() throws IOException {
        assumeTrue(Files.isRegularFile(REAL_CIRCUIT), "test_circuits/MUX_clked.cir is not present");
        Path circuit = copyRealCircuit();
        EditorPane pane = FxToolkit.call(EditorPane::new);

        FxToolkit.run(() -> pane.open(circuit));

        assertThat(FxToolkit.call(() -> pane.netlistEditor().getText())).contains(".subckt");
        assertThat(FxToolkit.call(() -> pane.elementList().rows())).hasSize(33);
        assertThat(FxToolkit.call(pane::netlistStatusText)).contains("33 margin targets");
    }

    @Test
    void marksExactlyTheLinesThatWillBeMeasured() throws IOException {
        assumeTrue(Files.isRegularFile(REAL_CIRCUIT), "test_circuits/MUX_clked.cir is not present");
        Path circuit = copyRealCircuit();
        EditorPane pane = FxToolkit.call(EditorPane::new);

        FxToolkit.run(() -> pane.open(circuit));

        List<CircuitElement> listed = FxToolkit.call(() -> pane.elementList().rows());
        assertThat(FxToolkit.call(() -> pane.netlistEditor().markedTargetLines()))
                .containsExactlyInAnyOrderElementsOf(
                        listed.stream().map(CircuitElement::lineNumber).toList());
    }

    @Test
    void opensAJudgementFileThatDoesNotExistYetAsAnEmptyOne() throws IOException {
        Path circuit = workingDirectory.resolve("fresh.cir");
        Files.writeString(circuit, String.join("\n", "* fresh", "l01  1  2  2p", ".FILE out.csv"));
        EditorPane pane = FxToolkit.call(EditorPane::new);

        FxToolkit.run(() -> pane.open(circuit));

        assertThat(FxToolkit.call(() -> pane.netlistEditor().getText())).contains("l01");
        assertThat(FxToolkit.call(pane::netlistStatusText)).contains("1 margin targets");
    }

    @Test
    void writesBothFilesBackUnderTheNamesTheCalculationLooksFor() throws IOException {
        assumeTrue(Files.isRegularFile(REAL_CIRCUIT), "test_circuits/MUX_clked.cir is not present");
        Path circuit = copyRealCircuit();
        EditorPane pane = FxToolkit.call(EditorPane::new);
        FxToolkit.run(() -> pane.open(circuit));

        FxToolkit.run(() -> {
            pane.netlistEditor().area().insertText(0, "* edited by the test\n");
            pane.save();
        });

        assertThat(Files.readString(circuit)).startsWith("* edited by the test");
        // The judgement keeps its name, which is how the run finds it.
        assertThat(workingDirectory.resolve("MUX_clked.txt")).exists();
        assertThat(FxToolkit.call(() -> pane.modifiedProperty().get())).isFalse();
    }

    @Test
    void reportsTheLineAnErrorIsOnWhileEditing() throws IOException {
        Path circuit = workingDirectory.resolve("broken.cir");
        Files.writeString(circuit, String.join("\n", "* header", "l01  1  2  2p", ".FILE out.csv"));
        EditorPane pane = FxToolkit.call(EditorPane::new);
        FxToolkit.run(() -> pane.open(circuit));

        FxToolkit.run(() -> {
            pane.netlistEditor().setText(String.join("\n",
                    "* header", "l01  1  2  2p", "r01  broken", ".FILE out.csv"));
            pane.save();
        });

        assertThat(FxToolkit.call(pane::netlistStatusText))
                .contains("line 3")
                .contains("malformed element line");
        assertThat(FxToolkit.call(() -> pane.elementList().rows())).isEmpty();
    }

    private Path copyRealCircuit() throws IOException {
        Path circuit = workingDirectory.resolve("MUX_clked.cir");
        Files.copy(REAL_CIRCUIT, circuit);
        Files.copy(REAL_JUDGEMENT, workingDirectory.resolve("MUX_clked.txt"));
        return circuit;
    }
}
