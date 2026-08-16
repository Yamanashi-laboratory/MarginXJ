package com.ynu.marginx.shared.exception;

/**
 * Thrown when a run was asked to stop rather than because anything went wrong: the user pressed
 * cancel in the GUI, or Ctrl+C at the terminal. Separate from the other failures so a caller can
 * tell "you stopped this" from "this broke".
 */
public class CalculationCancelledException extends MarginXException {

    public CalculationCancelledException(String message) {
        super(message);
    }

    public CalculationCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
