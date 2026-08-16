package com.ynu.marginx.infrastructure.netlist;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * JSIM names a node inside a subcircuit the other way round from JoSIM: what JoSIM prints as
 * {@code X1.b01} has to be asked for as {@code b01_X1}. convert_jsim.cpp rewrites the .print
 * directives for that and leaves a directive without a subcircuit reference untouched.
 */
public final class JsimPrintDirectiveConverter {

    private static final String PRINT_DIRECTIVE = ".print";
    private static final int REFERENCE_TOKEN = 2;

    public List<String> convert(List<String> lines) {
        List<String> converted = new ArrayList<>(lines);
        for (int i = 0; i < converted.size(); i++) {
            String rewritten = rewrite(converted.get(i));
            if (rewritten != null) {
                converted.set(i, rewritten);
            }
        }
        return converted;
    }

    /** Returns the rewritten directive, or null when the line needs no conversion. */
    private String rewrite(String line) {
        String trimmed = line.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(PRINT_DIRECTIVE)) {
            return null;
        }
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length <= REFERENCE_TOKEN) {
            return null;
        }
        String reference = tokens[REFERENCE_TOKEN];
        int dot = reference.lastIndexOf('.');
        if (dot <= 0 || dot == reference.length() - 1) {
            // No subcircuit part: JoSIM and JSIM spell this one the same way.
            return null;
        }
        String instance = reference.substring(0, dot);
        String element = reference.substring(dot + 1);
        return PRINT_DIRECTIVE + " " + tokens[1] + " " + element + "_" + instance;
    }
}
