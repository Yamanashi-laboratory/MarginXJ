package com.ynu.marginx.presentation.gui.editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ynu.marginx.domain.model.circuit.CircuitElement;
import com.ynu.marginx.testsupport.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The editor validates by calling the parsers the calculation uses, so what these check is that the
 * answer comes back in a form the editor can show: the elements, or a message and a line number.
 *
 * <p>The larger circuit under test_circuits is a real one, with subcircuits, synchronisation groups
 * and range directives - the shapes a hand-written fixture tends not to have.
 */
class NetlistValidatorTest {

    private static final Path REAL_CIRCUIT = Path.of("test_circuits", "MUX_clked.cir");
    private static final Path REAL_JUDGEMENT = Path.of("test_circuits", "MUX_clked.txt");

    private final NetlistValidator validator = new NetlistValidator();

    @Test
    void acceptsTheReferenceNetlist() {
        NetlistValidator.NetlistResult result =
                validator.validateNetlist("test_JTL.cir", String.join("\n", Fixtures.circuitLines()));

        assertThat(result.valid()).isTrue();
        assertThat(result.netlist().elementCount()).isEqualTo(5);
        assertThat(result.message()).contains("5 margin targets");
    }

    @Test
    void acceptsARealCircuitWithSubcircuitsAndSynchronisationGroups() throws IOException {
        assumeTrue(Files.isRegularFile(REAL_CIRCUIT), "test_circuits/MUX_clked.cir is not present");

        NetlistValidator.NetlistResult result =
                validator.validateNetlist("MUX_clked.cir", Files.readString(REAL_CIRCUIT));

        assertThat(result.valid()).isTrue();
        assertThat(result.netlist().elementCount()).isEqualTo(33);
        // Groups come from *SYN, which this circuit uses heavily.
        assertThat(result.netlist().elements())
                .anySatisfy(element -> assertThat(element.synchronizationGroup()).isNotZero());
    }

    @Test
    void everyReportedElementPointsAtTheLineItCameFrom() throws IOException {
        assumeTrue(Files.isRegularFile(REAL_CIRCUIT), "test_circuits/MUX_clked.cir is not present");
        List<String> lines = Files.readAllLines(REAL_CIRCUIT);

        NetlistValidator.NetlistResult result =
                validator.validateNetlist("MUX_clked.cir", String.join("\n", lines));

        // The editor marks target lines from these numbers, so a wrong one marks the wrong line.
        for (CircuitElement element : result.netlist().elements()) {
            String source = lines.get(element.lineNumber()).trim();
            assertThat(source)
                    .as("line %d for %s", element.lineNumber() + 1, element.displayName())
                    .startsWith(element.name().substring(0, 1).toLowerCase());
        }
    }

    @Test
    void marginTargetsAreExactlyTheLowerCaseDesignators() throws IOException {
        assumeTrue(Files.isRegularFile(REAL_CIRCUIT), "test_circuits/MUX_clked.cir is not present");
        List<String> lines = Files.readAllLines(REAL_CIRCUIT);

        NetlistValidator.NetlistResult result =
                validator.validateNetlist("MUX_clked.cir", String.join("\n", lines));

        long lowerCaseLines = lines.stream().filter(line -> !line.isEmpty())
                .filter(line -> Character.isLowerCase(line.charAt(0)))
                .count();
        assertThat(result.netlist().elementCount()).isEqualTo((int) lowerCaseLines);
    }

    @Test
    void reportsTheLineAMalformedElementIsOn() {
        String text = String.join("\n",
                "* header",
                "l01   1   2   2p",
                "r01   broken",
                ".FILE out.csv");

        NetlistValidator.NetlistResult result = validator.validateNetlist("broken.cir", text);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("Malformed element line");
        assertThat(result.line()).hasValue(2);
    }

    @Test
    void reportsAMissingFileDirectiveWithoutPointingAtALine() {
        String text = String.join("\n", "* header", "l01   1   2   2p");

        NetlistValidator.NetlistResult result = validator.validateNetlist("broken.cir", text);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains(".FILE");
        assertThat(result.line()).isEmpty();
    }

    @Test
    void explainsWhenNothingWouldBeMeasured() {
        // Every designator is upper case, so the parser finds no targets at all.
        String text = String.join("\n", "* header", "L01   1   2   2p", ".FILE out.csv");

        NetlistValidator.NetlistResult result = validator.validateNetlist("upper.cir", text);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("lower-case");
    }

    @Test
    void readsTheJudgementFileThroughItsOwnParser() throws IOException {
        assumeTrue(Files.isRegularFile(REAL_JUDGEMENT), "test_circuits/MUX_clked.txt is not present");

        NetlistValidator.JudgementResult result =
                validator.validateJudgement(Files.readString(REAL_JUDGEMENT));

        assertThat(result.valid()).isTrue();
        assertThat(result.message()).contains("51 judgement rules");
    }

    @Test
    void reportsAJudgementFileItCannotRead() {
        NetlistValidator.JudgementResult result = validator.validateJudgement("300 400 1");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isNotBlank();
    }
}
