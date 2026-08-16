package com.ynu.marginx.infrastructure.config;

import java.util.List;
import java.util.Locale;

/**
 * The simulators MarginXJ knows how to drive, and where each one tends to be installed.
 *
 * <p>Neither is shipped with MarginXJ (docs/adr/0001-distribution-strategy.md), so everything here
 * is about finding a copy the user installed themselves.
 */
public enum SimulatorKind {

    JOSIM("JoSIM", "josim", "MARGINX_JOSIM_COMMAND", "marginx.josim.command", "josim.path"),
    JSIM("JSIM", "jsim", "MARGINX_JSIM_COMMAND", "marginx.jsim.command", "jsim.path");

    private final String displayName;
    private final String command;
    private final String environmentVariable;
    private final String systemProperty;
    private final String settingKey;

    SimulatorKind(String displayName, String command, String environmentVariable,
                  String systemProperty, String settingKey) {
        this.displayName = displayName;
        this.command = command;
        this.environmentVariable = environmentVariable;
        this.systemProperty = systemProperty;
        this.settingKey = settingKey;
    }

    public String displayName() {
        return displayName;
    }

    /** The name to look for on PATH when nothing more specific was configured. */
    public String command() {
        return command;
    }

    public String environmentVariable() {
        return environmentVariable;
    }

    public String systemProperty() {
        return systemProperty;
    }

    public String settingKey() {
        return settingKey;
    }

    /**
     * Where installers and the usual build instructions tend to leave the executable. Searched only
     * after PATH, so a user who has it on PATH never depends on this list being right.
     */
    public List<String> installDirectories(String operatingSystem, java.util.function.UnaryOperator<String> environment) {
        String os = operatingSystem.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return windowsDirectories(environment);
        }
        if (os.startsWith("mac")) {
            return List.of("/usr/local/bin", "/opt/homebrew/bin", "/opt/" + command + "/bin",
                    home(environment) + "/.local/bin");
        }
        return List.of("/usr/local/bin", "/usr/bin", "/opt/" + command + "/bin",
                home(environment) + "/.local/bin");
    }

    private List<String> windowsDirectories(java.util.function.UnaryOperator<String> environment) {
        String programFiles = orEmpty(environment.apply("ProgramFiles"), "C:\\Program Files");
        String programFilesX86 = orEmpty(environment.apply("ProgramFiles(x86)"), "C:\\Program Files (x86)");
        String localAppData = orEmpty(environment.apply("LOCALAPPDATA"), home(environment) + "\\AppData\\Local");
        String name = this == JOSIM ? "JoSIM" : "jsim";
        return List.of(
                programFiles + "\\" + name,
                programFiles + "\\" + name + "\\bin",
                programFilesX86 + "\\" + name,
                localAppData + "\\Programs\\" + name,
                // Both are commonly used straight out of their own build tree.
                home(environment) + "\\" + name + "\\build\\Release",
                home(environment) + "\\" + name + "\\build");
    }

    private String home(java.util.function.UnaryOperator<String> environment) {
        String fromEnvironment = environment.apply("USERPROFILE");
        if (fromEnvironment == null || fromEnvironment.isBlank()) {
            fromEnvironment = environment.apply("HOME");
        }
        return orEmpty(fromEnvironment, System.getProperty("user.home", ""));
    }

    private String orEmpty(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
