package com.ynu.marginx.infrastructure.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ynu.marginx.infrastructure.config.SimulatorProperties;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.shared.exception.SimulationFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimulatorSelectorTest {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    @TempDir
    Path binDirectory;

    @Test
    void prefersJosimWhenBothAreInstalled() throws IOException {
        install("josim");
        install("jsim");

        assertThat(selector().select().name()).isEqualTo("josim");
    }

    @Test
    void fallsBackToJsimOnlyWhenJosimIsMissing() throws IOException {
        install("jsim");

        assertThat(selector().select().name()).isEqualTo("jsim");
    }

    @Test
    void explainsHowToInstallWhenNeitherIsFound() {
        assertThatThrownBy(() -> selector().select())
                .isInstanceOf(SimulationFailedException.class)
                .hasMessageContaining("MarginXJ does not ship a simulator")
                .hasMessageContaining("MARGINX_JOSIM_COMMAND");
    }

    @Test
    void acceptsACommandGivenAsAPath() throws IOException {
        Path josim = install("josim");

        SimulatorSelector selector = new SimulatorSelector(
                new SimulatorProperties(josim.toString(), "jsim", Duration.ofSeconds(1)),
                new NetlistRenderer(), new ProcessExecutor(), key -> null);

        assertThat(selector.locate(josim.toString())).contains(josim);
    }

    private SimulatorSelector selector() {
        Map<String, String> environment = Map.of(
                "PATH", binDirectory.toString(),
                "PATHEXT", ".COM;.EXE;.BAT");
        return new SimulatorSelector(
                new SimulatorProperties("josim", "jsim", Duration.ofSeconds(1)),
                new NetlistRenderer(), new ProcessExecutor(), environment::get);
    }

    private Path install(String command) throws IOException {
        Path executable = binDirectory.resolve(WINDOWS ? command + ".exe" : command);
        Files.writeString(executable, "stub");
        executable.toFile().setExecutable(true);
        return executable;
    }
}
