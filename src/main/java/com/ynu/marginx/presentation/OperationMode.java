package com.ynu.marginx.presentation;

import com.ynu.marginx.shared.exception.MarginXException;
import java.util.Arrays;

public enum OperationMode {

    JUDGE(1, "Judge operation"),
    MARGIN_EXHAUSTIVE(2, "Calculate Margin (accurately)"),
    MARGIN_BINARY(3, "Calculate Margin (binary search)"),
    MARGIN_SYNCHRONIZED(4, "Calculate Margin (synchronised groups)");

    private final int code;
    private final String label;

    OperationMode(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static OperationMode fromCode(int code) {
        return Arrays.stream(values())
                .filter(mode -> mode.code == code)
                .findFirst()
                .orElseThrow(() -> new MarginXException("Unknown operation mode: " + code));
    }
}
