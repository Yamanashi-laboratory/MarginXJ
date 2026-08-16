package com.ynu.marginx.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class SimulatorPropertiesTest {

    private static final UnaryOperator<String> NO_ENVIRONMENT = key -> null;

    @Test
    void fallsBackToTheBundledResource() {
        // Not josimCommand: the build forwards -Dmarginx.josim.command into the test JVM.
        SimulatorProperties properties = SimulatorProperties.load(NO_ENVIRONMENT);

        assertThat(properties.jsimCommand()).isEqualTo("jsim");
        assertThat(properties.timeout().toSeconds()).isEqualTo(120);
    }

    @Test
    void readsTheCommandFromTheEnvironment() {
        UnaryOperator<String> environment =
                Map.of("MARGINX_JOSIM_COMMAND", "/usr/local/bin/josim")::get;

        assertThat(SimulatorProperties.load(environment).josimCommand()).isEqualTo("/usr/local/bin/josim");
    }

    @Test
    void letsASystemPropertyWinOverTheEnvironment() {
        UnaryOperator<String> environment = Map.of("MARGINX_JSIM_COMMAND", "from-environment")::get;
        System.setProperty("marginx.jsim.command", "from-property");
        try {
            assertThat(SimulatorProperties.load(environment).jsimCommand()).isEqualTo("from-property");
        } finally {
            System.clearProperty("marginx.jsim.command");
        }
    }
}
