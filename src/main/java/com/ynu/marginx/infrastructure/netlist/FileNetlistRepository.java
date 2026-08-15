package com.ynu.marginx.infrastructure.netlist;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.port.NetlistRepository;
import com.ynu.marginx.shared.exception.CircuitFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class FileNetlistRepository implements NetlistRepository {

    private static final List<String> EXTENSIONS = List.of(".cir", ".inp");

    private final Path workingDirectory;
    private final NetlistParser parser;

    public FileNetlistRepository(Path workingDirectory, NetlistParser parser) {
        this.workingDirectory = workingDirectory;
        this.parser = parser;
    }

    @Override
    public Netlist load(String baseName) {
        Path source = resolve(baseName);
        try {
            return parser.parse(source.getFileName().toString(), Files.readAllLines(source));
        } catch (IOException e) {
            throw new CircuitFileException("Cannot read circuit file " + source, e);
        }
    }

    private Path resolve(String baseName) {
        for (String extension : EXTENSIONS) {
            Path candidate = workingDirectory.resolve(baseName + extension);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new CircuitFileException("No circuit file named " + baseName + ".cir or " + baseName + ".inp"
                + " under " + workingDirectory);
    }
}
