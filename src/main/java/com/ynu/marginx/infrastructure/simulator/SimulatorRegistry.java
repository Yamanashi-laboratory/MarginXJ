package com.ynu.marginx.infrastructure.simulator;

import com.ynu.marginx.domain.port.CircuitSimulator;
import com.ynu.marginx.infrastructure.config.SimulatorKind;
import com.ynu.marginx.infrastructure.config.SimulatorLocation;
import com.ynu.marginx.infrastructure.config.SimulatorProperties;
import com.ynu.marginx.infrastructure.config.UserSimulatorSettings;
import com.ynu.marginx.infrastructure.netlist.JsimPrintDirectiveConverter;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.infrastructure.result.JosimCsvReader;
import com.ynu.marginx.infrastructure.result.JsimCsvReader;
import com.ynu.marginx.shared.exception.SimulationFailedException;
import java.util.Optional;
import java.util.function.Function;

/**
 * Decides which simulator a run uses.
 *
 * <p>JoSIM is the default and JSIM is the fallback, but the two do not share a numerical engine, so
 * falling back is never silent: {@link Selection} carries the fact and the reason for whoever is
 * going to tell the user. Simulator choice is a top-level setting, independent of the operation
 * mode - the C++ tool put JSIM behind its own submenu and that structure is deliberately dropped.
 */
public final class SimulatorRegistry {

    private static final String JOSIM_RELEASES = "https://github.com/JoeyDelp/JoSIM/releases";

    /** What the caller asked for. */
    public enum Choice {
        AUTO, JOSIM, JSIM
    }

    /**
     * The chosen simulator, plus what it means. {@code fallback} is true only when JoSIM was
     * wanted, was unavailable, and JSIM stood in for it.
     */
    public record Selection(CircuitSimulator simulator, boolean fallback, String reason) {

        public String warning() {
            return fallback ? reason : "";
        }
    }

    private final SimulatorProperties properties;
    private final NetlistRenderer renderer;
    private final ProcessExecutor executor;
    private final Function<SimulatorKind, SimulatorLocation> resolver;

    public SimulatorRegistry(SimulatorProperties properties, UserSimulatorSettings settings,
                             NetlistRenderer renderer, ProcessExecutor executor) {
        this(properties, renderer, executor, kind -> SimulatorLocation.resolve(kind, settings));
    }

    /** Test seam: lets the selection rules be exercised without a real PATH underneath them. */
    SimulatorRegistry(SimulatorProperties properties, NetlistRenderer renderer, ProcessExecutor executor,
                      Function<SimulatorKind, SimulatorLocation> resolver) {
        this.properties = properties;
        this.renderer = renderer;
        this.executor = executor;
        this.resolver = resolver;
    }

    public Selection resolve() {
        return resolve(Choice.AUTO);
    }

    public Selection resolve(Choice choice) {
        CircuitSimulator josim = simulator(SimulatorKind.JOSIM);
        CircuitSimulator jsim = simulator(SimulatorKind.JSIM);

        // An explicit choice is never overridden: being told to use JSIM and quietly getting JoSIM
        // would be its own kind of surprise.
        if (choice == Choice.JOSIM) {
            return required(josim);
        }
        if (choice == Choice.JSIM) {
            return required(jsim);
        }
        if (josim.isAvailable()) {
            return new Selection(josim, false, "");
        }
        if (jsim.isAvailable()) {
            return new Selection(jsim, true, josim.unavailableReason()
                    + ", so JSIM is being used instead. JSIM has a different numerical engine and"
                    + " its results may not agree with JoSIM.");
        }
        throw new SimulationFailedException("Found no simulator to run. " + josim.unavailableReason()
                + ", and the same is true of JSIM. MarginXJ does not ship a simulator: install JoSIM"
                + " from " + JOSIM_RELEASES + ", or point MARGINX_JOSIM_COMMAND at the executable.");
    }

    /** Where the chosen simulator was found, for the provenance line on a result file. */
    public Optional<SimulatorLocation> locationOf(CircuitSimulator simulator) {
        return simulator instanceof ExternalProcessSimulator external
                ? Optional.of(external.location())
                : Optional.empty();
    }

    public CircuitSimulator simulator(SimulatorKind kind) {
        return build(kind, resolver.apply(kind));
    }

    /** Builds an adapter around a location that has already been resolved. */
    public CircuitSimulator simulator(SimulatorLocation location) {
        return build(location.kind(), location);
    }

    private CircuitSimulator build(SimulatorKind kind, SimulatorLocation location) {
        return kind == SimulatorKind.JOSIM
                ? new JosimSimulator(location, properties.timeout(), renderer, new JosimCsvReader(), executor)
                : new JsimSimulator(location, properties.timeout(), renderer,
                        new JsimPrintDirectiveConverter(), new JsimCsvReader(), executor);
    }

    private Selection required(CircuitSimulator simulator) {
        if (simulator.isAvailable()) {
            return new Selection(simulator, false, "");
        }
        throw new SimulationFailedException(simulator.unavailableReason()
                + ". It was asked for explicitly, so nothing else was tried.");
    }
}
