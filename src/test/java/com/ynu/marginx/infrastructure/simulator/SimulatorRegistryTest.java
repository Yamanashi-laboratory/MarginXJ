package com.ynu.marginx.infrastructure.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ynu.marginx.infrastructure.config.SimulatorKind;
import com.ynu.marginx.infrastructure.config.SimulatorLocation;
import com.ynu.marginx.infrastructure.config.SimulatorProperties;
import com.ynu.marginx.infrastructure.netlist.NetlistRenderer;
import com.ynu.marginx.shared.exception.SimulationFailedException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The selection rules: JoSIM by default, JSIM only as an announced fallback, and an explicit choice
 * that is never quietly overridden. Locations are supplied directly so the rules are tested without
 * a real PATH underneath them.
 */
class SimulatorRegistryTest {

    @Test
    void prefersJosimWhenBothAreInstalled() {
        SimulatorRegistry.Selection selection = registry(SimulatorKind.JOSIM, SimulatorKind.JSIM).resolve();

        assertThat(selection.simulator().displayName()).isEqualTo("JoSIM");
        assertThat(selection.fallback()).isFalse();
        assertThat(selection.warning()).isEmpty();
    }

    @Test
    void usesJosimAloneWithoutAnyWarning() {
        SimulatorRegistry.Selection selection = registry(SimulatorKind.JOSIM).resolve();

        assertThat(selection.simulator().displayName()).isEqualTo("JoSIM");
        assertThat(selection.fallback()).isFalse();
        assertThat(selection.simulator().isAvailable()).isTrue();
    }

    @Test
    void fallsBackToJsimAndSaysWhyAndWhatItMeans() {
        SimulatorRegistry.Selection selection = registry(SimulatorKind.JSIM).resolve();

        assertThat(selection.simulator().displayName()).isEqualTo("JSIM");
        assertThat(selection.fallback()).isTrue();
        // The whole point of the warning is that the two engines need not agree.
        assertThat(selection.warning())
                .contains("JoSIM")
                .contains("JSIM is being used instead")
                .contains("may not agree");
    }

    @Test
    void failsWithSomewhereToGetJosimWhenNeitherIsInstalled() {
        assertThatThrownBy(() -> registry().resolve())
                .isInstanceOf(SimulationFailedException.class)
                .hasMessageContaining("Found no simulator to run")
                .hasMessageContaining("github.com/JoeyDelp/JoSIM/releases")
                .hasMessageContaining("MARGINX_JOSIM_COMMAND");
    }

    @Test
    void anExplicitChoiceOfJsimIsHonouredEvenWhenJosimIsThere() {
        SimulatorRegistry.Selection selection = registry(SimulatorKind.JOSIM, SimulatorKind.JSIM)
                .resolve(SimulatorRegistry.Choice.JSIM);

        assertThat(selection.simulator().displayName()).isEqualTo("JSIM");
        // Asked for, so not a fallback: there is nothing to warn about.
        assertThat(selection.fallback()).isFalse();
    }

    @Test
    void anExplicitChoiceThatIsNotInstalledFailsRatherThanSubstituting() {
        assertThatThrownBy(() -> registry(SimulatorKind.JSIM).resolve(SimulatorRegistry.Choice.JOSIM))
                .isInstanceOf(SimulationFailedException.class)
                .hasMessageContaining("asked for explicitly");
    }

    @Test
    void anAbsentSimulatorCarriesTheReasonItIsUnavailable() {
        var josim = registry().simulator(SimulatorKind.JOSIM);

        assertThat(josim.isAvailable()).isFalse();
        assertThat(josim.unavailableReason()).contains("JoSIM").contains("not found");
    }

    @Test
    void theResolvedPathIsWhatTheAdapterWillRun() {
        var josim = registry(SimulatorKind.JOSIM).simulator(SimulatorKind.JOSIM);

        assertThat(josim.name()).isEqualTo(Path.of("/opt/josim/bin/josim").toString());
    }

    private SimulatorRegistry registry(SimulatorKind... installed) {
        Set<SimulatorKind> present = installed.length == 0
                ? EnumSet.noneOf(SimulatorKind.class)
                : EnumSet.copyOf(Set.of(installed));
        return new SimulatorRegistry(
                new SimulatorProperties("josim", "jsim", Duration.ofSeconds(1)),
                new NetlistRenderer(), new ProcessExecutor(),
                kind -> present.contains(kind)
                        ? SimulatorLocation.found(kind, Path.of("/opt/" + kind.command() + "/bin/" + kind.command()),
                                SimulatorLocation.Source.INSTALL_DIRECTORY)
                        : SimulatorLocation.missing(kind, kind.displayName() + " was not found on PATH"));
    }
}
