package com.ynu.marginx.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

/**
 * Simulator commands, overridable per run with -Dmarginx.josim.command=... so the executable name
 * is no longer a compile-time #define.
 */
public record SimulatorProperties(String josimCommand, String jsimCommand, Duration timeout) {

    private static final String RESOURCE = "/application.properties";

    public static SimulatorProperties load() {
        Properties properties = new Properties();
        try (InputStream stream = SimulatorProperties.class.getResourceAsStream(RESOURCE)) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
        return new SimulatorProperties(
                resolve(properties, "marginx.josim.command", "josim"),
                resolve(properties, "marginx.jsim.command", "jsim"),
                Duration.ofSeconds(Long.parseLong(resolve(properties, "marginx.simulation.timeout-seconds", "120"))));
    }

    private static String resolve(Properties properties, String key, String fallback) {
        String override = System.getProperty(key);
        return override != null ? override : properties.getProperty(key, fallback);
    }
}
