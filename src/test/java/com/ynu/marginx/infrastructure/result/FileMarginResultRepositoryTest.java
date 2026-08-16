package com.ynu.marginx.infrastructure.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.ynu.marginx.domain.model.margin.ElementMargin;
import com.ynu.marginx.domain.model.margin.Margin;
import com.ynu.marginx.domain.model.margin.MarginTable;
import com.ynu.marginx.testsupport.Circuits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileMarginResultRepositoryTest {

    @TempDir
    Path outputDirectory;

    private final MarginTable table = new MarginTable(List.of(
            new ElementMargin(Circuits.singleResistor(1.0).element(0), new Margin(-25, 30))));

    @Test
    void recordsWhichSimulatorMeasuredTheMargins() throws IOException {
        repository(new FileMarginResultRepository.Provenance("JSIM", "C:\\tools\\jsim.exe"))
                .save("test_JTL", table);

        // A result file has to say which engine produced it: JoSIM and JSIM need not agree.
        assertThat(lines("result_test_JTL.csv")).startsWith(
                "# simulator: JSIM", "# executable: C:\\tools\\jsim.exe");
        assertThat(lines("result_test_JTL.txt")).startsWith(
                "# simulator: JSIM", "# executable: C:\\tools\\jsim.exe");
        assertThat(lines("result.csv")).startsWith("# simulator: JSIM");
    }

    @Test
    void keepsTheDataRowsExactlyAsTheyWere() throws IOException {
        repository(new FileMarginResultRepository.Provenance("JoSIM", "/usr/local/bin/josim"))
                .save("test_JTL", table);

        // Everything after the comments is the format that was there before.
        assertThat(lines("result_test_JTL.csv"))
                .filteredOn(line -> !line.startsWith("#"))
                .containsExactly("R01,-25.0000,30.0000");
    }

    @Test
    void writesNoCommentsWhenThereIsNoProvenance() throws IOException {
        repository(null).save("test_JTL", table);

        assertThat(lines("result_test_JTL.csv")).noneMatch(line -> line.startsWith("#"));
    }

    private FileMarginResultRepository repository(FileMarginResultRepository.Provenance provenance) {
        return new FileMarginResultRepository(outputDirectory, provenance);
    }

    private List<String> lines(String name) throws IOException {
        return Files.readAllLines(outputDirectory.resolve(name));
    }
}
