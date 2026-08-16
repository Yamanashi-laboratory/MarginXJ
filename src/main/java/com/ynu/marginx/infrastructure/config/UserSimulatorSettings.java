package com.ynu.marginx.infrastructure.config;

import com.ynu.marginx.shared.exception.MarginXException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.function.UnaryOperator;

/**
 * The simulator paths the user has chosen, kept across runs in the platform config directory.
 *
 * <p>This is the highest-priority source in {@link SimulatorLocation}: whatever the user pointed at
 * from the settings wins over PATH and over anything found by scanning.
 */
public final class UserSimulatorSettings {

    private static final String DIRECTORY = "MarginXJ";
    private static final String UNIX_DIRECTORY = "marginxj";
    private static final String FILE = "settings.properties";

    private final Path file;

    UserSimulatorSettings(Path file) {
        this.file = file;
    }

    public static UserSimulatorSettings inDefaultLocation() {
        return new UserSimulatorSettings(defaultDirectory(System.getProperty("os.name", ""), System::getenv)
                .resolve(FILE));
    }

    /**
     * %APPDATA% on Windows, Application Support on macOS, XDG_CONFIG_HOME elsewhere - each
     * platform's usual home for a small settings file.
     */
    static Path defaultDirectory(String operatingSystem, UnaryOperator<String> environment) {
        String os = operatingSystem.toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home", "");
        if (os.startsWith("windows")) {
            String appData = environment.apply("APPDATA");
            Path base = appData == null || appData.isBlank() ? Path.of(home, "AppData", "Roaming") : Path.of(appData);
            return base.resolve(DIRECTORY);
        }
        if (os.startsWith("mac")) {
            return Path.of(home, "Library", "Application Support", DIRECTORY);
        }
        String xdg = environment.apply("XDG_CONFIG_HOME");
        Path base = xdg == null || xdg.isBlank() ? Path.of(home, ".config") : Path.of(xdg);
        return base.resolve(UNIX_DIRECTORY);
    }

    public Optional<String> path(SimulatorKind kind) {
        String configured = load().getProperty(kind.settingKey());
        return configured == null || configured.isBlank() ? Optional.empty() : Optional.of(configured.trim());
    }

    public void save(SimulatorKind kind, String executable) {
        Properties properties = load();
        properties.setProperty(kind.settingKey(), executable);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "MarginXJ simulator locations");
            }
        } catch (IOException e) {
            throw new MarginXException("Cannot write the settings file " + file, e);
        }
    }

    public Path file() {
        return file;
    }

    private Properties load() {
        Properties properties = new Properties();
        if (!Files.isRegularFile(file)) {
            return properties;
        }
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            throw new MarginXException("Cannot read the settings file " + file, e);
        }
        return properties;
    }
}
