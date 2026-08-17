package com.ynu.marginx.shared.exception;

import java.util.OptionalInt;

public class CircuitFileException extends MarginXException {

    /** Zero-based, or absent when the problem is with the file as a whole. */
    private final int line;

    public CircuitFileException(String message) {
        this(message, -1);
    }

    public CircuitFileException(String message, Throwable cause) {
        super(message, cause);
        this.line = -1;
    }

    /**
     * A problem with one particular line. The editor needs the number to underline it: searching
     * the text for the line again would pick the wrong one whenever a netlist repeats itself.
     */
    public CircuitFileException(String message, int line) {
        super(message);
        this.line = line;
    }

    public OptionalInt line() {
        return line < 0 ? OptionalInt.empty() : OptionalInt.of(line);
    }
}
