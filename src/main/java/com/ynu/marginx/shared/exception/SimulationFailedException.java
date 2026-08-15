package com.ynu.marginx.shared.exception;

public class SimulationFailedException extends MarginXException {

    public SimulationFailedException(String message) {
        super(message);
    }

    public SimulationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
