package com.ynu.marginx.infrastructure.netlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.testsupport.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileNetlistRepositorySaveTest {

    @TempDir
    Path workingDirectory;

    private FileNetlistRepository repository;
    private Netlist netlist;

    @BeforeEach
    void setUp() throws IOException {
        Files.write(workingDirectory.resolve("test_JTL.cir"), Fixtures.circuitLines());
        repository = new FileNetlistRepository(workingDirectory, new NetlistParser());
        netlist = repository.load("test_JTL");
    }

    @Test
    void writesTheOptimisedCircuitNextToTheOriginal() {
        repository.save(netlist.baseName(), netlist);

        // make_cir_last.cpp names it <circuit>_out.cir - not <circuit>.cir_out.cir.
        assertThat(workingDirectory.resolve("test_JTL_out.cir")).exists();
    }

    @Test
    void writesTheCurrentValuesAndKeepsTheDeckOtherwiseIntact() throws IOException {
        repository.save(netlist.baseName(), netlist.withElementValue(0, 3.5));

        List<String> saved = Files.readAllLines(workingDirectory.resolve("test_JTL_out.cir"));
        assertThat(saved).anyMatch(line -> line.contains("area=3.500"));
        assertThat(saved).anyMatch(line -> line.startsWith(".FILE CIRCUIT.CSV"));
        assertThat(saved).contains("* Example JTL Basic");
    }
}
