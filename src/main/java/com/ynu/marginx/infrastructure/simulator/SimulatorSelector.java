package com.ynu.marginx.infrastructure.simulator;

import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.infrastructure.config.SimulatorProperties;
import com.ynu.marginx.infrastructure.netlist.JsimPrintDirectiveConverter;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.result.JosimCsvReader;
import com.ynu.marginx.infrastructure.result.JsimCsvReader;
import com.ynu.marginx.shared.exception.SimulationFailedException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Picks the simulator to run with: JoSIM whenever it can be found, JSIM only as a fallback.
 *
 * <p>Neither is bundled with MarginXJ (docs/adr/0001-distribution-strategy.md), so which one is
 * available is a property of the machine, not of the build. The C++ tool asked the user instead,
 * through a separate "JSIM modes" menu; choosing automatically is a deliberate departure.
 *
 * <p>The lookup is a PATH search rather than a trial run. JSIM reads its netlist from standard
 * input when it is given no usable argument, so probing it by execution risks hanging.
 */
public final class SimulatorSelector {

    private static final List<String> WINDOWS_DEFAULT_EXTENSIONS = List.of(".exe", ".bat", ".cmd", ".com");

    private final SimulatorProperties properties;
    private final NetlistRenderer renderer;
    private final ProcessExecutor executor;
    private final UnaryOperator<String> environment;

    public SimulatorSelector(SimulatorProperties properties, NetlistRenderer renderer,
                             ProcessExecutor executor) {
        this(properties, renderer, executor, System::getenv);
    }

    SimulatorSelector(SimulatorProperties properties, NetlistRenderer renderer,
                      ProcessExecutor executor, UnaryOperator<String> environment) {
        this.properties = properties;
        this.renderer = renderer;
        this.executor = executor;
        this.environment = environment;
    }

    public CircuitSimulator select() {
        if (locate(properties.josimCommand()).isPresent()) {
            return new JosimSimulator(properties, renderer, new JosimCsvReader(), executor);
        }
        if (locate(properties.jsimCommand()).isPresent()) {
            return new JsimSimulator(properties, renderer, new JsimPrintDirectiveConverter(),
                    new JsimCsvReader(), executor);
        }
        throw new SimulationFailedException(
                "Found neither " + properties.josimCommand() + " nor " + properties.jsimCommand()
                        + " on PATH. MarginXJ does not ship a simulator: install JoSIM from"
                        + " https://github.com/JoeyDelp/JoSIM/releases, or point MARGINX_JOSIM_COMMAND"
                        + " at the executable.");
    }

    /** Resolves a command the way a shell would, so a bare name, a relative or a full path all work. */
    Optional<Path> locate(String command) {
        try {
            Path direct = Path.of(command);
            if (direct.getParent() != null) {
                return runnable(direct) ? Optional.of(direct) : Optional.empty();
            }
            String searchPath = environment.apply("PATH");
            if (searchPath == null) {
                return Optional.empty();
            }
            for (String entry : searchPath.split(File.pathSeparator)) {
                if (entry.isBlank()) {
                    continue;
                }
                for (String extension : extensions()) {
                    Path candidate = Path.of(entry).resolve(command + extension);
                    if (runnable(candidate)) {
                        return Optional.of(candidate);
                    }
                }
            }
            return Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private boolean runnable(Path candidate) {
        return Files.isRegularFile(candidate) && Files.isExecutable(candidate);
    }

    /** On Windows the name on PATH carries no suffix; PATHEXT says which ones to try. */
    private List<String> extensions() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return List.of("");
        }
        String pathExtensions = environment.apply("PATHEXT");
        if (pathExtensions == null || pathExtensions.isBlank()) {
            return prepend(WINDOWS_DEFAULT_EXTENSIONS);
        }
        return prepend(Arrays.stream(pathExtensions.split(File.pathSeparator))
                .map(extension -> extension.toLowerCase(Locale.ROOT))
                .toList());
    }

    private List<String> prepend(List<String> extensions) {
        return Stream.concat(Stream.of(""), extensions.stream()).toList();
    }
}
