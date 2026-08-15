package com.ynu.marginx.testsupport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Fixtures {

    private Fixtures() {
    }

    public static List<String> circuitLines() {
        return read("/circuits/test_JTL.cir");
    }

    public static List<String> judgementLines() {
        return read("/circuits/test_JTL.txt");
    }

    private static List<String> read(String resource) {
        try (InputStream stream = Fixtures.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource " + resource);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
