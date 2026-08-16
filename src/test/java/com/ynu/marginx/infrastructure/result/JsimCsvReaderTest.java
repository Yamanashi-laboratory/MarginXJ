package com.ynu.marginx.infrastructure.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.ynu.marginx.domain.model.judge.SimulationResult;
import com.ynu.marginx.shared.exception.SimulationFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsimCsvReaderTest {

    @TempDir
    Path directory;

    private final JsimCsvReader reader = new JsimCsvReader();

    @Test
    void keepsTheFirstRowBecauseThereIsNoHeader() throws IOException {
        // Shape taken from a real JSIM run: no header, space separated, three-digit exponents.
        Path csv = write("0.000e+000 0.000e+000 0.000e+000 ",
                "1.000e-012 2.848e-002 3.876e-002 ",
                "2.000e-012 1.054e-001 1.459e-001 ");

        SimulationResult result = reader.read(csv);

        assertThat(result.rowCount()).isEqualTo(3);
        assertThat(result.columnCount()).isEqualTo(3);
        assertThat(result.at(0, 1)).isEqualTo(0.0);
        assertThat(result.at(1, 1)).isCloseTo(2.848e-2, within(1e-12));
    }

    @Test
    void scalesTheTimeColumnToPicoseconds() throws IOException {
        Path csv = write("0.000e+000 0.000e+000", "1.000e-012 1.000e+000");

        assertThat(reader.read(csv).at(1, 0)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void reportsAMissingFile() {
        assertThatThrownBy(() -> reader.read(directory.resolve("absent.csv")))
                .isInstanceOf(SimulationFailedException.class)
                .hasMessageContaining("JSIM produced no output file");
    }

    @Test
    void reportsAnEmptyFile() throws IOException {
        assertThatThrownBy(() -> reader.read(write()))
                .isInstanceOf(SimulationFailedException.class)
                .hasMessageContaining("JSIM output is empty");
    }

    @Test
    void readsMsvcDivergenceMarkersAsNonFiniteValues() throws IOException {
        // What a real JSIM writes when the circuit diverges; it still exits 0.
        Path csv = write("0.000e+000 0.000e+000 0.000e+000",
                "1.000e-012 -1.#IOe+000 -1.#INDe+000",
                "2.000e-012 1.#INFe+000 0.000e+000");

        SimulationResult result = reader.read(csv);

        assertThat(result.at(1, 1)).isEqualTo(Double.NEGATIVE_INFINITY);
        assertThat(result.at(1, 2)).isNaN();
        assertThat(result.at(2, 1)).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    void namesTheLineWhenTheOutputIsNotNumericAtAll() throws IOException {
        Path csv = write("0.000e+000 0.000e+000", "## Error -- no transient analysis specified");

        assertThatThrownBy(() -> reader.read(csv))
                .isInstanceOf(SimulationFailedException.class)
                .hasMessageContaining("not a number")
                .hasMessageContaining("no transient analysis");
    }

    private Path write(String... lines) throws IOException {
        Path csv = directory.resolve("CIRCUIT.CSV");
        Files.write(csv, List.of(lines));
        return csv;
    }
}
