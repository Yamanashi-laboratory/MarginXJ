package com.ynu.marginx.shared.exception;

public class MarginXException extends RuntimeException {

    public MarginXException(String message) {
        super(message);
    }

    public MarginXException(String message, Throwable cause) {
        super(message, cause);
    }
}
