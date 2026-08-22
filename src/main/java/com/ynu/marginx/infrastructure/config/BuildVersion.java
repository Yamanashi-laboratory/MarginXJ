package com.ynu.marginx.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The version this copy was built as.
 *
 * <p>It comes from the build rather than from a constant in the source, because the two drift the
 * moment a release is built with -Pversion: a user reporting a problem would name a version that
 * says nothing about which build they are running.
 */
public final class BuildVersion {

    private static final String RESOURCE = "/marginx-build.properties";

    /** What to say when the resource is missing, which means this is not a built copy. */
    private static final String UNKNOWN = "unknown";

    private static final String VERSION = read();

    private BuildVersion() {
    }

    public static String version() {
        return VERSION;
    }

    private static String read() {
        Properties properties = new Properties();
        try (InputStream stream = BuildVersion.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                return UNKNOWN;
            }
            properties.load(stream);
        } catch (IOException e) {
            return UNKNOWN;
        }
        String version = properties.getProperty("version", "").trim();
        // An unsubstituted placeholder means the resource was copied without being filtered, which
        // is worth saying plainly rather than showing the user a literal dollar sign.
        return version.isEmpty() || version.startsWith("$") ? UNKNOWN : version;
    }
}
