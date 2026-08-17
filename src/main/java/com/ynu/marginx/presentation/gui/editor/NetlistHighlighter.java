package com.ynu.marginx.presentation.gui.editor;

import com.ynu.marginx.domain.model.circuit.ElementType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Works out what colour every part of a netlist should be.
 *
 * <p>This is only about appearance: nothing here decides what a line means. The element symbols it
 * recognises come from {@link ElementType} and the directive words are the ones NetlistParser acts
 * on, so the editor cannot drift into colouring a vocabulary the parser does not share.
 *
 * <p>Kept apart from the editor widget because it needs no toolkit, and because the rules are worth
 * testing on their own.
 */
public final class NetlistHighlighter {

    /** The CSS classes; netlist-editor.css gives them their colours. */
    public static final String ELEMENT = "netlist-element";
    public static final String DOT_COMMAND = "netlist-dot-command";
    public static final String DIRECTIVE = "netlist-directive";
    public static final String COMMENT = "netlist-comment";
    public static final String PLAIN = "netlist-plain";

    /** *MIN, *MAX, *FIX and *SYN, which apply to the element on the line above. */
    private static final List<String> ELEMENT_DIRECTIVES = List.of("MIN", "MAX", "FIX", "SYN");

    /** Named on a shunt resistor line to say how its value should be read. */
    private static final List<String> SHUNT_DIRECTIVES = List.of("SHUNT", "BC", "CALC");

    private final Pattern elementLine;
    private final Pattern directive;

    public NetlistHighlighter() {
        this.elementLine = Pattern.compile("^\\s*(" + designators() + ")\\w*\\b",
                Pattern.CASE_INSENSITIVE);
        this.directive = Pattern.compile("^\\*(" + directiveWords() + ")\\b", Pattern.CASE_INSENSITIVE);
    }

    /**
     * Longest first, so BI is matched before B - otherwise every junction inductance would be
     * recognised as a plain junction.
     */
    private static String designators() {
        return Arrays.stream(ElementType.values())
                .map(ElementType::directiveKey)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .reduce((left, right) -> left + "|" + right)
                .orElseThrow();
    }

    /** *MIN and friends, plus the per-type range words such as *LMIN and *BMAX. */
    private static String directiveWords() {
        List<String> words = new ArrayList<>(ELEMENT_DIRECTIVES);
        for (ElementType type : ElementType.values()) {
            words.add(type.directiveKey() + "MIN");
            words.add(type.directiveKey() + "MAX");
        }
        words.sort(Comparator.comparingInt(String::length).reversed());
        return String.join("|", words);
    }

    public StyleSpans<java.util.Collection<String>> computeHighlighting(String text) {
        StyleSpansBuilder<java.util.Collection<String>> spans = new StyleSpansBuilder<>();
        int position = 0;
        for (String line : text.split("\n", -1)) {
            int length = line.length();
            if (position > 0) {
                spans.add(List.of(PLAIN), 1);
            }
            spans.add(List.of(styleOf(line)), length);
            position += length + 1;
        }
        return spans.create();
    }

    /**
     * A line gets exactly one style. Netlist lines are short and each one is a single thing - an
     * element, a command, a directive or a comment - so colouring the whole line reads better than
     * picking out tokens inside it.
     */
    public String styleOf(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return PLAIN;
        }
        if (trimmed.startsWith(".")) {
            return DOT_COMMAND;
        }
        if (trimmed.startsWith("*")) {
            // Everything starting with * is a comment as far as SPICE is concerned; the few words
            // MarginX reads out of them are what make a line a directive rather than a remark.
            return directive.matcher(trimmed).find() ? DIRECTIVE : COMMENT;
        }
        if (containsShuntDirective(trimmed)) {
            return DIRECTIVE;
        }
        return elementLine.matcher(trimmed).find() ? ELEMENT : PLAIN;
    }

    /** A shunt directive rides on the end of a resistor line rather than having one of its own. */
    private boolean containsShuntDirective(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        for (String word : SHUNT_DIRECTIVES) {
            if (upper.contains("*" + word)) {
                return true;
            }
        }
        return false;
    }

    /** The element symbol a line starts with, or null when it is not an element line at all. */
    String designatorOf(String line) {
        Matcher matcher = elementLine.matcher(line.trim());
        return matcher.find() ? matcher.group(1) : null;
    }
}
