package com.ynu.marginx.infrastructure.netlist;

import com.ynu.marginx.domain.model.circuit.CircuitElement;
import com.ynu.marginx.domain.model.circuit.ElementType;
import com.ynu.marginx.domain.model.circuit.Netlist;
import com.ynu.marginx.domain.model.circuit.ParameterRange;
import com.ynu.marginx.domain.model.circuit.ShuntMode;
import com.ynu.marginx.domain.model.circuit.ShuntSpec;
import com.ynu.marginx.shared.exception.CircuitFileException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a JoSIM netlist and the MarginX-specific {@code *} directives that mark margin targets.
 *
 * <p>An element only becomes a target when its designator starts with a lower-case letter, the
 * convention the C++ tool relies on to separate swept elements from fixed ones.
 */
public final class NetlistParser {

    private static final String FILE_DIRECTIVE = ".file";

    public Netlist parse(String baseName, List<String> lines) {
        State state = new State();
        for (String rawLine : lines) {
            state.lines.add(rawLine);
            String line = rawLine.trim();

            if (state.shuntExpected) {
                applyShuntDirective(state, line);
                state.shuntExpected = false;
            }
            applyElementDirective(state, line);
            applyRangeDirective(state, line);

            ElementType type = classify(line);
            if (type != null) {
                state.elements.add(readElement(state, type, line));
                state.shuntExpected = type == ElementType.JUNCTION;
            }
            if (line.toLowerCase(Locale.ROOT).startsWith(FILE_DIRECTIVE)) {
                state.fileDirectiveSeen = true;
            }
            state.lineNumber++;
        }

        if (state.elements.isEmpty()) {
            throw new CircuitFileException("No target element found in " + baseName
                    + ". Margin targets must be written with a lower-case designator.");
        }
        if (!state.fileDirectiveSeen) {
            throw new CircuitFileException("Missing \".FILE\" line in " + baseName + ".");
        }

        List<CircuitElement> ordered = new ArrayList<>(state.elements);
        ordered.sort(Comparator.comparingInt(element -> element.type().ordinal()));
        return new Netlist(baseName, state.lines, ordered);
    }

    static ElementType classify(String line) {
        if (line.isEmpty()) {
            return null;
        }
        char designator = line.charAt(0);
        return switch (designator) {
            case 'l' -> ElementType.INDUCTOR;
            case 'k' -> ElementType.COUPLING;
            case 'b' -> line.length() > 1 && Character.toLowerCase(line.charAt(1)) == 'i'
                    ? ElementType.JUNCTION_INDUCTANCE
                    : ElementType.JUNCTION;
            case 'c' -> ElementType.CAPACITOR;
            case 'r' -> ElementType.RESISTOR;
            case 'v' -> ElementType.VOLTAGE_SOURCE;
            case 'i' -> ElementType.CURRENT_SOURCE;
            default -> null;
        };
    }

    private CircuitElement readElement(State state, ElementType type, String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 4) {
            throw new CircuitFileException("Malformed element line: " + line, state.lineNumber);
        }
        String name = tokens[0].toUpperCase(Locale.ROOT);
        String node1 = tokens[1];
        String node2 = tokens[2];
        ParameterRange range = state.range(type);

        return switch (type) {
            case COUPLING -> element(type, state, name, node1, node2,
                    UnitPrefix.magnitude(tokens[3]), "", range, ShuntSpec.unshunted(), "");
            case JUNCTION, JUNCTION_INDUCTANCE -> {
                String model = tokens[3];
                yield element(type, state, name, node1, node2, junctionArea(state, line), "", range,
                        ShuntSpec.unshunted(), model);
            }
            case VOLTAGE_SOURCE, CURRENT_SOURCE -> {
                String amplitude = sourceAmplitude(state, tokens, line);
                yield element(type, state, name, node1, node2, UnitPrefix.magnitude(amplitude),
                        UnitPrefix.detect(amplitude) + type.baseUnit(), range, ShuntSpec.unshunted(), "");
            }
            case RESISTOR -> {
                String token = stripTrailing(tokens[3], "ohm");
                yield element(type, state, name, node1, node2, UnitPrefix.magnitude(token),
                        UnitPrefix.detect(token) + type.baseUnit(), range, ShuntSpec.unshunted(), "");
            }
            case INDUCTOR, CAPACITOR -> {
                String token = stripTrailing(tokens[3], type.baseUnit());
                yield element(type, state, name, node1, node2, UnitPrefix.magnitude(token),
                        UnitPrefix.detect(token) + type.baseUnit(), range, ShuntSpec.unshunted(), "");
            }
        };
    }

    private CircuitElement element(ElementType type, State state, String name, String node1, String node2,
                                   double value, String unit, ParameterRange range,
                                   ShuntSpec shunt, String model) {
        return new CircuitElement(type, state.lineNumber, name, node1, node2,
                UnitPrefix.round(value), unit, range, false, 0, shunt, model);
    }

    private double junctionArea(State state, String line) {
        int assignment = line.lastIndexOf('=');
        if (assignment < 0) {
            throw new CircuitFileException(
                    "Junction line is missing an \"area=\" assignment: " + line, state.lineNumber);
        }
        return UnitPrefix.magnitude(line.substring(assignment + 1).trim());
    }

    /**
     * Bias sources are written as a two-point PWL ramp; the swept quantity is its final amplitude.
     */
    private String sourceAmplitude(State state, String[] tokens, String line) {
        if (tokens.length < 7) {
            throw new CircuitFileException(
                    "Bias source line is not a two-point PWL ramp: " + line, state.lineNumber);
        }
        return tokens[6].replace(")", "");
    }

    private String stripTrailing(String token, String unit) {
        if (unit.isEmpty()) {
            return token;
        }
        int index = token.toLowerCase(Locale.ROOT).lastIndexOf(unit.toLowerCase(Locale.ROOT));
        return index < 0 ? token : token.substring(0, index);
    }

    private void applyShuntDirective(State state, String line) {
        if (!line.regionMatches(true, 0, "RS", 0, 2)) {
            return;
        }
        int assignment = line.lastIndexOf('=');
        if (assignment < 0) {
            return;
        }
        double parameter = UnitPrefix.magnitude(line.substring(assignment + 1).trim());
        String upper = line.toUpperCase(Locale.ROOT);
        ShuntMode mode = upper.contains("*SHUNT") ? ShuntMode.IC_RS
                : upper.contains("*BC") ? ShuntMode.BC
                : upper.contains("*CALC") ? ShuntMode.CALCULATED
                : ShuntMode.UNSHUNTED;

        int last = state.elements.size() - 1;
        CircuitElement junction = state.elements.get(last).withShunt(new ShuntSpec(mode, parameter));
        state.elements.set(last, junction);

        if (mode == ShuntMode.CALCULATED) {
            state.elements.add(new CircuitElement(ElementType.RESISTOR, state.lineNumber,
                    "RS" + junction.name().substring(1), junction.node1(), junction.node2(),
                    UnitPrefix.round(junction.shunt().resistance(junction.value())), "ohm",
                    state.range(ElementType.RESISTOR), false, 0, ShuntSpec.unshunted(), ""));
        }
    }

    private void applyElementDirective(State state, String line) {
        if (!line.startsWith("*") || state.elements.isEmpty()) {
            return;
        }
        int last = state.elements.size() - 1;
        CircuitElement element = state.elements.get(last);
        if (line.startsWith("*MIN")) {
            state.elements.set(last, element.withRange(element.range().withMin(directiveValue(state, line))));
        } else if (line.startsWith("*MAX")) {
            state.elements.set(last, element.withRange(element.range().withMax(directiveValue(state, line))));
        } else if (line.startsWith("*FIX")) {
            state.elements.set(last, element.fixedValue());
        } else if (line.startsWith("*SYN")) {
            state.elements.set(last, element.synchronizedWith((int) directiveValue(state, line)));
        }
    }

    private void applyRangeDirective(State state, String line) {
        if (!line.startsWith("*")) {
            return;
        }
        for (ElementType type : ElementType.values()) {
            String key = "*" + type.directiveKey();
            if (line.startsWith(key + "MIN")) {
                state.ranges.put(type, state.range(type).withMin(directiveValue(state, line)));
                return;
            }
            if (line.startsWith(key + "MAX")) {
                state.ranges.put(type, state.range(type).withMax(directiveValue(state, line)));
                return;
            }
        }
    }

    private double directiveValue(State state, String line) {
        int assignment = line.indexOf('=');
        if (assignment < 0) {
            throw new CircuitFileException("Directive is missing \"=\": " + line, state.lineNumber);
        }
        return UnitPrefix.magnitude(line.substring(assignment + 1).trim());
    }

    private static final class State {
        private final List<String> lines = new ArrayList<>();
        private final List<CircuitElement> elements = new ArrayList<>();
        private final Map<ElementType, ParameterRange> ranges = new EnumMap<>(ElementType.class);
        private int lineNumber;
        private boolean shuntExpected;
        private boolean fileDirectiveSeen;

        private ParameterRange range(ElementType type) {
            return ranges.getOrDefault(type, type.defaultRange());
        }
    }
}
