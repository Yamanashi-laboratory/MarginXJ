package com.ynu.marginx.infrastructure.netlist;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UnitPrefix {

    /**
     * Search order copied from sub_unit.cpp. "meg" sits behind the bare "m" and is therefore
     * unreachable there too - kept as-is so parsed units stay identical to the C++ tool.
     */
    private static final List<String> PREFIXES = List.of("f", "p", "n", "u", "m", "k", "meg", "x", "g", "t");

    private static final Pattern LEADING_NUMBER =
            Pattern.compile("^[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");

    private UnitPrefix() {
    }

    static String detect(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        for (String prefix : PREFIXES) {
            if (lower.contains(prefix)) {
                return prefix;
            }
        }
        return "";
    }

    static double magnitude(String token) {
        Matcher matcher = LEADING_NUMBER.matcher(token);
        return matcher.find() ? Double.parseDouble(matcher.group()) : 0;
    }

    /** The C++ tool stores every parsed value rounded to three decimals (triple_digits). */
    static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
