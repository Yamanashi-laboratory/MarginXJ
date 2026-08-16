package com.ynu.marginx.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stand-in for the JoSIM executable, launched as a real external process by
 * {@link com.ynu.marginx.infrastructure.simulator.JosimSimulatorProcessTest}.
 *
 * <p>It behaves like the real thing in the ways the adapter depends on: it reads the netlist from
 * its working directory, honours the {@code .FILE} directive, and writes a JoSIM-shaped CSV whose
 * waveform depends on the swept resistor value.
 *
 * <p>Usage: {@code StubJosim <lower> <upper> <delay millis> <netlist file>}. The delay is there so
 * a test can cancel a run while a simulator is genuinely mid-flight.
 */
public final class StubJosim {

    private static final Pattern RESISTANCE = Pattern.compile("(-?\\d+\\.\\d+)ohm");
    private static final Pattern OUTPUT_FILE = Pattern.compile("(?i)^\\.FILE\\s+(\\S+)");

    private static final int ROWS = 12;
    private static final int CROSSING_ROW = 5;
    private static final double ABOVE_THRESHOLD = 4.0;
    private static final double SECONDS_PER_ROW = 1e-12;

    private StubJosim() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        double lower = Double.parseDouble(args[0]);
        double upper = Double.parseDouble(args[1]);
        long delayMillis = Long.parseLong(args[2]);
        Path netlist = Path.of(args[3]);

        if (delayMillis > 0) {
            Thread.sleep(delayMillis);
        }

        List<String> lines = Files.readAllLines(netlist);
        double resistance = extract(lines, RESISTANCE);
        boolean operating = resistance >= lower - 1e-9 && resistance <= upper + 1e-9;

        List<String> csv = new ArrayList<>(ROWS + 1);
        csv.add("time,v1");
        for (int row = 0; row < ROWS; row++) {
            double value = operating && row >= CROSSING_ROW ? ABOVE_THRESHOLD : 0.0;
            csv.add(String.format(Locale.ROOT, "%.6e,%.6f", row * SECONDS_PER_ROW, value));
        }
        Files.write(netlist.toAbsolutePath().getParent().resolve(outputFileName(lines)), csv);
    }

    private static double extract(List<String> lines, Pattern pattern) {
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        }
        throw new IllegalStateException("Stub could not find a resistor value in the netlist: " + lines);
    }

    private static String outputFileName(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = OUTPUT_FILE.matcher(line.trim());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new IllegalStateException("Stub found no .FILE directive in the netlist: " + lines);
    }
}
