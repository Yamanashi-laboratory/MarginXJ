package com.ynu.marginx.presentation;

import com.ynu.marginx.shared.exception.MarginXException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;

public final class InteractiveMenu {

    private final Scanner scanner;
    private final PrintStream out;

    public InteractiveMenu(InputStream in, PrintStream out) {
        this.scanner = new Scanner(in);
        this.out = out;
    }

    public OperationMode select() {
        out.println(" Please select an operation mode.");
        out.println();
        for (OperationMode mode : OperationMode.values()) {
            out.printf(" %d. %s%n", mode.code(), mode.label());
        }
        out.print("  Selected: ");
        out.flush();

        if (!scanner.hasNextInt()) {
            throw new MarginXException("Please input a correct number.");
        }
        OperationMode selected = OperationMode.fromCode(scanner.nextInt());
        out.println();
        return selected;
    }
}
