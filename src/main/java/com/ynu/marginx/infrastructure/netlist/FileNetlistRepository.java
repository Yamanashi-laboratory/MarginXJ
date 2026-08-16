package com.ynu.marginx.infrastructure.netlist;

import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.port.NetlistRepository;
import com.ynu.marginx.shared.exception.CircuitFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class FileNetlistRepository implements NetlistRepository {

    private static final List<String> EXTENSIONS = List.of(".cir", ".inp");

    /** make_cir_last.cpp points the saved deck at the intermediate output, and so do we. */
    private static final String OUTPUT_DIRECTIVE_TARGET = "CIRCUIT.CSV";

    private final Path workingDirectory;
    private final NetlistParser parser;
    private final NetlistRenderer renderer;

    public FileNetlistRepository(Path workingDirectory, NetlistParser parser) {
        this(workingDirectory, parser, new NetlistRenderer());
    }

    public FileNetlistRepository(Path workingDirectory, NetlistParser parser, NetlistRenderer renderer) {
        this.workingDirectory = workingDirectory;
        this.parser = parser;
        this.renderer = renderer;
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

    @Override
    public void save(String baseName, Netlist netlist) {
        // The name carried on the netlist keeps its extension; make_cir_last.cpp appends _out.cir
        // to the bare name it was given on the command line.
        Path target = workingDirectory.resolve(stripExtension(baseName) + "_out.cir");
        try {
            Files.write(target, renderer.render(netlist, OUTPUT_DIRECTIVE_TARGET));
        } catch (IOException e) {
            throw new CircuitFileException("Cannot write circuit file " + target, e);
        }
    }

    private String stripExtension(String baseName) {
        for (String extension : EXTENSIONS) {
            if (baseName.toLowerCase(Locale.ROOT).endsWith(extension)) {
                return baseName.substring(0, baseName.length() - extension.length());
            }
        }
        return baseName;
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
