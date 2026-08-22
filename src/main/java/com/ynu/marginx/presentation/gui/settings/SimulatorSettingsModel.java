package com.ynu.marginx.presentation.gui.settings;

import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.infrastructure.config.SimulatorKind;
import com.ynu.marginx.infrastructure.config.SimulatorLocation;
import com.ynu.marginx.infrastructure.config.UserSimulatorSettings;
import com.ynu.marginx.infrastructure.simulator.SimulatorRegistry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What the settings window shows and does, without any JavaFX in the way.
 *
 * <p>The interesting question for a user whose run went wrong is not only "which simulator" but
 * "why that one" - a stale environment variable and a copy on PATH look identical once the run has
 * started. Every row therefore carries the step of {@link SimulatorLocation} that produced it.
 */
public final class SimulatorSettingsModel {

    /** One simulator, as the window presents it. */
    public record Status(SimulatorKind kind, boolean available, String executable,
                         String source, String detail, boolean savedHere) {

        public String displayName() {
            return kind.displayName();
        }
    }

    private final SimulatorRegistry registry;
    private final UserSimulatorSettings settings;

    public SimulatorSettingsModel(SimulatorRegistry registry, UserSimulatorSettings settings) {
        this.registry = registry;
        this.settings = settings;
    }

    public List<Status> statuses() {
        List<Status> statuses = new ArrayList<>(SimulatorKind.values().length);
        for (SimulatorKind kind : SimulatorKind.values()) {
            statuses.add(statusOf(kind));
        }
        return statuses;
    }

    public Status statusOf(SimulatorKind kind) {
        CircuitSimulator simulator = registry.simulator(kind);
        SimulatorLocation location = registry.locationOf(simulator).orElseThrow();
        boolean available = location.found();
        return new Status(kind, available,
                available ? location.command() : "",
                location.source().description(),
                available ? "" : location.reason(),
                settings.path(kind).isPresent());
    }

    /** Remembers an executable for future runs. The next resolution will prefer it over PATH. */
    public void save(SimulatorKind kind, Path executable) {
        settings.save(kind, executable.toAbsolutePath().toString());
    }

    public void clear(SimulatorKind kind) {
        settings.clear(kind);
    }

    /** Shown in the window, because a saved setting is otherwise invisible on disk. */
    public Path settingsFile() {
        return settings.file();
    }
}
