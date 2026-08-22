package com.ynu.marginx.presentation.gui.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.ynu.marginx.infrastructure.config.SimulatorKind;
import com.ynu.marginx.infrastructure.config.SimulatorProperties;
import com.ynu.marginx.infrastructure.config.UserSimulatorSettings;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.simulator.ProcessExecutor;
import com.ynu.marginx.infrastructure.simulator.SimulatorRegistry;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the settings window is built on, tested without a display: what it reports about each
 * simulator, and that saving and clearing a path actually change what a run would use.
 */
class SimulatorSettingsModelTest {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    @TempDir
    Path settingsDirectory;

    @TempDir
    Path binDirectory;

    private UserSimulatorSettings settings;
    private SimulatorSettingsModel model;

    @BeforeEach
    void setUp() throws Exception {
        settings = newSettings(settingsDirectory.resolve("settings.properties"));
        model = new SimulatorSettingsModel(
                new SimulatorRegistry(new SimulatorProperties("josim", "jsim", Duration.ofSeconds(1)),
                        settings, new NetlistRenderer(), new ProcessExecutor()),
                settings);
    }

    @Test
    void reportsOneRowPerSimulator() {
        assertThat(model.statuses())
                .extracting(SimulatorSettingsModel.Status::displayName)
                .containsExactly("JoSIM", "JSIM");
    }

    @Test
    void aSavedPathIsWhatARunWouldUse() throws IOException {
        Path josim = install("josim");

        model.save(SimulatorKind.JOSIM, josim);

        SimulatorSettingsModel.Status status = model.statusOf(SimulatorKind.JOSIM);
        assertThat(status.available()).isTrue();
        assertThat(status.executable()).isEqualTo(josim.toAbsolutePath().toString());
        // The step that produced it, which is what tells a puzzled user why this copy and not that.
        assertThat(status.source()).isEqualTo("the saved setting");
        assertThat(status.savedHere()).isTrue();
    }

    @Test
    void clearingASavedPathGivesTheSearchBackToPath() throws IOException {
        model.save(SimulatorKind.JOSIM, install("josim"));
        assertThat(model.statusOf(SimulatorKind.JOSIM).savedHere()).isTrue();

        model.clear(SimulatorKind.JOSIM);

        assertThat(model.statusOf(SimulatorKind.JOSIM).savedHere()).isFalse();
        assertThat(settings.path(SimulatorKind.JOSIM)).isEmpty();
    }

    @Test
    void explainsASimulatorItCannotFind() {
        // Nothing saved and nothing installed under the names being searched for.
        SimulatorSettingsModel.Status status = model.statusOf(SimulatorKind.JSIM);

        if (!status.available()) {
            assertThat(status.detail()).contains("JSIM").isNotBlank();
            assertThat(status.executable()).isEmpty();
        }
    }

    @Test
    void reportsASavedPathThatIsNoLongerThere() throws IOException {
        Path josim = install("josim");
        model.save(SimulatorKind.JOSIM, josim);
        Files.delete(josim);

        SimulatorSettingsModel.Status status = model.statusOf(SimulatorKind.JOSIM);

        // Silently going back to PATH would hide the fact that the setting is now wrong.
        assertThat(status.available()).isFalse();
        assertThat(status.detail()).contains("nothing runnable is there");
        assertThat(status.savedHere()).isTrue();
    }

    @Test
    void namesTheFileTheSettingsLiveIn() {
        assertThat(model.settingsFile()).isEqualTo(settingsDirectory.resolve("settings.properties"));
    }

    private Path install(String command) throws IOException {
        Path executable = binDirectory.resolve(WINDOWS ? command + ".exe" : command);
        Files.writeString(executable, "stub");
        executable.toFile().setExecutable(true);
        return executable;
    }

    /** The package-private constructor keeps the real settings file out of the tests. */
    private UserSimulatorSettings newSettings(Path file) throws Exception {
        Constructor<UserSimulatorSettings> constructor =
                UserSimulatorSettings.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(file);
    }
}
