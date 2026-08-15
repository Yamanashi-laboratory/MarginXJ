package com.ynu.marginx.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Writes a one-line launcher script for {@link StubJosim}, so the adapter under test can invoke it
 * exactly the way it would invoke {@code josim}: one executable name plus the netlist file.
 */
public final class StubJosimLauncher {

    private StubJosimLauncher() {
    }

    public static String write(Path directory, double lower, double upper) throws IOException {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        String java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString();
        String classpath = System.getProperty("java.class.path");
        String stub = StubJosim.class.getName();

        Path script = directory.resolve(windows ? "stub-josim.bat" : "stub-josim.sh");
        List<String> lines = windows
                ? List.of("@echo off",
                          String.format(Locale.ROOT, "\"%s\" -cp \"%s\" %s %s %s %%1",
                                  java, classpath, stub, lower, upper))
                : List.of("#!/bin/sh",
                          String.format(Locale.ROOT, "exec \"%s\" -cp \"%s\" %s %s %s \"$1\"",
                                  java, classpath, stub, lower, upper));
        Files.write(script, lines);
        if (!windows) {
            script.toFile().setExecutable(true);
        }
        return script.toString();
    }
}
