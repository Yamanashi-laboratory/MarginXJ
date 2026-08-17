package com.ynu.marginx.infrastructure.config;

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
 * Where a simulator executable actually is, and how it was found.
 *
 * <p>Resolution runs in a fixed order, most specific first: the path the user saved in the
 * settings, the environment variable, the system property, PATH, and finally the directories the
 * thing is usually installed into. Nothing here starts a process - JSIM reads its netlist from
 * standard input when it is given no usable argument, so probing by execution risks hanging - and
 * nothing here decides availability from the operating system alone.
 */
public record SimulatorLocation(SimulatorKind kind, Optional<Path> executable, Source source, String reason) {

    /** Which of the five steps produced the answer. */
    public enum Source {
        USER_SETTING("the saved setting"),
        ENVIRONMENT("the environment"),
        SYSTEM_PROPERTY("a system property"),
        PATH("PATH"),
        INSTALL_DIRECTORY("a standard install directory"),
        NOT_FOUND("nowhere");

        private final String description;

        Source(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    private static final List<String> WINDOWS_DEFAULT_EXTENSIONS = List.of(".exe", ".bat", ".cmd", ".com");

    public boolean found() {
        return executable.isPresent();
    }

    /** The command to run, which is the resolved path once we have one. */
    public String command() {
        return executable.map(Path::toString).orElseGet(kind::command);
    }

    public static SimulatorLocation found(SimulatorKind kind, Path executable, Source source) {
        return new SimulatorLocation(kind, Optional.of(executable), source, "");
    }

    public static SimulatorLocation missing(SimulatorKind kind, String reason) {
        return new SimulatorLocation(kind, Optional.empty(), Source.NOT_FOUND, reason);
    }

    public static SimulatorLocation resolve(SimulatorKind kind, UserSimulatorSettings settings) {
        return resolve(kind, settings, System::getenv, System::getProperty,
                System.getProperty("os.name", ""));
    }

    static SimulatorLocation resolve(SimulatorKind kind, UserSimulatorSettings settings,
                                     UnaryOperator<String> environment, UnaryOperator<String> systemProperties,
                                     String operatingSystem) {
        return resolve(kind, settings, environment, systemProperties, operatingSystem,
                kind.installDirectories(operatingSystem, environment));
    }

    /**
     * The directories to scan are a parameter rather than a lookup so a test can point them
     * somewhere harmless. On Linux the real ones are /usr/local/bin and /usr/bin: a test that used
     * them would be writing to system directories, and would find whatever the machine happens to
     * have installed instead of what the test set up.
     */
    static SimulatorLocation resolve(SimulatorKind kind, UserSimulatorSettings settings,
                                     UnaryOperator<String> environment, UnaryOperator<String> systemProperties,
                                     String operatingSystem, List<String> installDirectories) {
        // Steps 1 to 3 are the user saying which executable to use. If one of them is set but does
        // not resolve, that is an error rather than a reason to carry on looking: quietly running
        // some other copy found on PATH is exactly the kind of surprise this class exists to avoid.
        Optional<String> configured = settings.path(kind);
        if (configured.isPresent()) {
            return fromConfiguredValue(kind, configured.get(), Source.USER_SETTING, environment, operatingSystem)
                    .orElseGet(() -> missing(kind, kind.displayName() + " is set to " + configured.get()
                            + " in " + settings.file() + ", but nothing runnable is there"));
        }
        String fromEnvironment = environment.apply(kind.environmentVariable());
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromConfiguredValue(kind, fromEnvironment, Source.ENVIRONMENT, environment, operatingSystem)
                    .orElseGet(() -> missing(kind, kind.displayName() + " is set to " + fromEnvironment.trim()
                            + " by " + kind.environmentVariable() + ", but nothing runnable is there"));
        }
        String fromProperty = systemProperties.apply(kind.systemProperty());
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromConfiguredValue(kind, fromProperty, Source.SYSTEM_PROPERTY, environment, operatingSystem)
                    .orElseGet(() -> missing(kind, kind.displayName() + " is set to " + fromProperty.trim()
                            + " by -D" + kind.systemProperty() + ", but nothing runnable is there"));
        }
        Optional<Path> onPath = searchPath(kind.command(), environment, operatingSystem);
        if (onPath.isPresent()) {
            return found(kind, onPath.get(), Source.PATH);
        }
        Optional<Path> installed =
                searchInstallDirectories(kind, installDirectories, environment, operatingSystem);
        if (installed.isPresent()) {
            return found(kind, installed.get(), Source.INSTALL_DIRECTORY);
        }
        return missing(kind, kind.displayName() + " was not found on PATH, in "
                + kind.environmentVariable() + ", or in the usual install directories");
    }

    /** A configured value may be a full path or a bare command name; both are accepted. */
    private static Optional<SimulatorLocation> fromConfiguredValue(SimulatorKind kind, String value, Source source,
                                                                   UnaryOperator<String> environment,
                                                                   String operatingSystem) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        try {
            Path direct = Path.of(trimmed);
            if (direct.getParent() != null) {
                return runnable(direct)
                        ? Optional.of(found(kind, direct, source))
                        : Optional.empty();
            }
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
        return searchPath(trimmed, environment, operatingSystem).map(path -> found(kind, path, source));
    }

    private static Optional<Path> searchPath(String command, UnaryOperator<String> environment,
                                             String operatingSystem) {
        String searchPath = environment.apply("PATH");
        if (searchPath == null || searchPath.isBlank()) {
            return Optional.empty();
        }
        for (String entry : searchPath.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            Optional<Path> candidate = in(entry, command, environment, operatingSystem);
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> searchInstallDirectories(SimulatorKind kind, List<String> directories,
                                                           UnaryOperator<String> environment,
                                                           String operatingSystem) {
        for (String directory : directories) {
            Optional<Path> candidate = in(directory, kind.command(), environment, operatingSystem);
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> in(String directory, String command, UnaryOperator<String> environment,
                                     String operatingSystem) {
        try {
            for (String extension : extensions(environment, operatingSystem)) {
                Path candidate = Path.of(directory).resolve(command + extension);
                if (runnable(candidate)) {
                    return Optional.of(candidate);
                }
            }
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static boolean runnable(Path candidate) {
        return Files.isRegularFile(candidate) && Files.isExecutable(candidate);
    }

    /** On Windows the name on PATH carries no suffix; PATHEXT says which ones to try. */
    private static List<String> extensions(UnaryOperator<String> environment, String operatingSystem) {
        if (!operatingSystem.toLowerCase(Locale.ROOT).startsWith("windows")) {
            return List.of("");
        }
        String pathExtensions = environment.apply("PATHEXT");
        List<String> configured = pathExtensions == null || pathExtensions.isBlank()
                ? WINDOWS_DEFAULT_EXTENSIONS
                : Arrays.stream(pathExtensions.split(File.pathSeparator))
                        .map(extension -> extension.toLowerCase(Locale.ROOT))
                        .toList();
        return Stream.concat(Stream.of(""), configured.stream()).toList();
    }
}
