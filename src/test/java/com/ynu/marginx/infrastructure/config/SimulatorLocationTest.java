package com.ynu.marginx.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The five steps, in order. Each test installs the executable in more than one place and checks
 * which one wins, because the order is the whole point of the class.
 */
class SimulatorLocationTest {

    private static final String OS = System.getProperty("os.name", "");
    private static final boolean WINDOWS = OS.toLowerCase(Locale.ROOT).startsWith("windows");

    @TempDir
    Path settingsDirectory;

    @TempDir
    Path preferred;

    @TempDir
    Path onPath;

    @TempDir
    Path installRoot;

    private UserSimulatorSettings settings;
    private Map<String, String> environment;

    @BeforeEach
    void setUp() {
        settings = new UserSimulatorSettings(settingsDirectory.resolve("settings.properties"));
        environment = new HashMap<>();
        // Point the standard install directories at an empty temp tree. Without this the last step
        // would find whatever the developer happens to have installed, and the "not found" cases
        // would pass or fail depending on the machine.
        environment.put("USERPROFILE", installRoot.toString());
        environment.put("HOME", installRoot.toString());
        environment.put("ProgramFiles", installRoot.resolve("Program Files").toString());
        environment.put("ProgramFiles(x86)", installRoot.resolve("Program Files x86").toString());
        environment.put("LOCALAPPDATA", installRoot.resolve("Local").toString());
    }

    @Test
    void theSavedSettingBeatsEverythingElse() throws IOException {
        Path saved = install(preferred, "josim");
        Path other = install(onPath, "josim");
        settings.save(SimulatorKind.JOSIM, saved.toString());
        environment.put(SimulatorKind.JOSIM.environmentVariable(), other.toString());
        environment.put("PATH", onPath.toString());

        assertThat(resolve().executable()).contains(saved);
        assertThat(resolve().source()).isEqualTo(SimulatorLocation.Source.USER_SETTING);
    }

    @Test
    void theEnvironmentVariableBeatsTheSystemProperty() throws IOException {
        Path fromEnvironment = install(preferred, "josim");
        Path fromProperty = install(onPath, "josim");
        environment.put(SimulatorKind.JOSIM.environmentVariable(), fromEnvironment.toString());

        SimulatorLocation location = resolve(SimulatorKind.JOSIM, key -> fromProperty.toString());

        assertThat(location.executable()).contains(fromEnvironment);
        assertThat(location.source()).isEqualTo(SimulatorLocation.Source.ENVIRONMENT);
    }

    @Test
    void theSystemPropertyBeatsPath() throws IOException {
        Path fromProperty = install(preferred, "josim");
        install(onPath, "josim");
        environment.put("PATH", onPath.toString());

        SimulatorLocation location = resolve(SimulatorKind.JOSIM,
                key -> SimulatorKind.JOSIM.systemProperty().equals(key) ? fromProperty.toString() : null);

        assertThat(location.executable()).contains(fromProperty);
        assertThat(location.source()).isEqualTo(SimulatorLocation.Source.SYSTEM_PROPERTY);
    }

    @Test
    void pathIsSearchedWhenNothingIsConfigured() throws IOException {
        Path executable = install(onPath, "josim");
        environment.put("PATH", onPath.toString());

        assertThat(resolve().executable()).contains(executable);
        assertThat(resolve().source()).isEqualTo(SimulatorLocation.Source.PATH);
    }

    @Test
    void aBareCommandNameInTheEnvironmentIsLookedUpOnPath() throws IOException {
        Path executable = install(onPath, "josim");
        environment.put("PATH", onPath.toString());
        environment.put(SimulatorKind.JOSIM.environmentVariable(), "josim");

        assertThat(resolve().executable()).contains(executable);
        assertThat(resolve().source()).isEqualTo(SimulatorLocation.Source.ENVIRONMENT);
    }

    @Test
    void aConfiguredPathThatIsNotThereIsReportedRatherThanIgnored() {
        settings.save(SimulatorKind.JOSIM, preferred.resolve("absent").toString());

        SimulatorLocation location = resolve();

        assertThat(location.found()).isFalse();
        assertThat(location.reason())
                .contains("absent")
                .contains("nothing runnable is there");
    }

    @Test
    void anEmptyEnvironmentLeavesTheSimulatorUnavailableWithAReason() {
        SimulatorLocation location = resolve();

        assertThat(location.found()).isFalse();
        assertThat(location.source()).isEqualTo(SimulatorLocation.Source.NOT_FOUND);
        assertThat(location.reason()).contains("JoSIM").contains(SimulatorKind.JOSIM.environmentVariable());
        // A missing simulator still answers with the plain command, so messages read sensibly.
        assertThat(location.command()).isEqualTo("josim");
    }

    @Test
    void aBrokenEnvironmentVariableIsReportedRatherThanFallingThroughToPath() throws IOException {
        // josim is on PATH, but the environment names one that is not there. Using the PATH copy
        // would silently ignore what the user asked for.
        install(onPath, "josim");
        environment.put("PATH", onPath.toString());
        environment.put(SimulatorKind.JOSIM.environmentVariable(), preferred.resolve("absent").toString());

        SimulatorLocation location = resolve();

        assertThat(location.found()).isFalse();
        assertThat(location.reason())
                .contains(SimulatorKind.JOSIM.environmentVariable())
                .contains("nothing runnable is there");
    }

    @Test
    void aStandardInstallDirectoryIsTheLastResort() throws IOException {
        // Nothing configured and nothing on PATH, but the scanned location has it.
        Path directory = installRoot.resolve("opt");
        Files.createDirectories(directory);
        Path executable = install(directory, "josim");

        SimulatorLocation location = resolve();

        assertThat(location.executable()).contains(executable);
        assertThat(location.source()).isEqualTo(SimulatorLocation.Source.INSTALL_DIRECTORY);
    }

    @Test
    void looksInThePlacesEachPlatformInstallsInto() {
        // The list itself, without touching the filesystem.
        List<String> windows = SimulatorKind.JOSIM.installDirectories("Windows 11", environment::get);
        assertThat(windows).anyMatch(directory -> directory.contains("Program Files"));

        List<String> linux = SimulatorKind.JOSIM.installDirectories("Linux", environment::get);
        assertThat(linux).contains("/usr/local/bin", "/usr/bin");

        List<String> mac = SimulatorKind.JSIM.installDirectories("Mac OS X", environment::get);
        assertThat(mac).contains("/usr/local/bin", "/opt/homebrew/bin");
    }

    @Test
    void theSeparatorForPathEntriesIsThePlatformOne() throws IOException {
        Path executable = install(onPath, "jsim");
        environment.put("PATH", preferred + File.pathSeparator + onPath);

        SimulatorLocation location = resolve(SimulatorKind.JSIM, key -> null);

        assertThat(location.executable()).contains(executable);
    }

    private SimulatorLocation resolve() {
        return resolve(SimulatorKind.JOSIM, key -> null);
    }

    /**
     * Always scans a temp directory rather than the machine. On Linux the real list starts at
     * /usr/local/bin and /usr/bin, which a test must neither write to nor be at the mercy of.
     */
    private SimulatorLocation resolve(SimulatorKind kind, java.util.function.UnaryOperator<String> properties) {
        return SimulatorLocation.resolve(kind, settings, environment::get, properties, OS,
                List.of(installRoot.resolve("opt").toString()));
    }

    private Path install(Path directory, String command) throws IOException {
        Path executable = directory.resolve(WINDOWS ? command + ".exe" : command);
        Files.writeString(executable, "stub");
        executable.toFile().setExecutable(true);
        return executable;
    }
}
