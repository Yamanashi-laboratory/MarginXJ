package com.ynu.marginx.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;
import java.util.function.UnaryOperator;

/**
 * Simulator commands, overridable per run with -Dmarginx.josim.command=... or the matching
 * MARGINX_JOSIM_COMMAND environment variable, so the executable name is no longer a compile-time
 * #define.
 */
public record SimulatorProperties(String josimCommand, String jsimCommand, Duration timeout) {

    private static final String RESOURCE = "/application.properties";

    public static SimulatorProperties load() {
        return load(System::getenv);
    }

    static SimulatorProperties load(UnaryOperator<String> environment) {
        Properties properties = new Properties();
        try (InputStream stream = SimulatorProperties.class.getResourceAsStream(RESOURCE)) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
        return new SimulatorProperties(
                resolve(properties, environment, "marginx.josim.command", "josim"),
                resolve(properties, environment, "marginx.jsim.command", "jsim"),
                Duration.ofSeconds(Long.parseLong(
                        resolve(properties, environment, "marginx.simulation.timeout-seconds", "120"))));
    }

    private static String resolve(Properties properties, UnaryOperator<String> environment,
                                  String key, String fallback) {
        String override = System.getProperty(key);
        if (override != null) {
            return override;
        }
        // A native image only honours -D when it precedes the application arguments, so the same
        // override is accepted as an environment variable: marginx.josim.command -> MARGINX_JOSIM_COMMAND.
        String fromEnvironment = environment.apply(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        if (fromEnvironment != null) {
            return fromEnvironment;
        }
        return properties.getProperty(key, fallback);
    }
}
