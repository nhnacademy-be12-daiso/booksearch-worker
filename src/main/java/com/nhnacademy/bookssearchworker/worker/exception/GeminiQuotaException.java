package com.nhnacademy.bookssearchworker.worker.exception;

public class GeminiQuotaException extends RuntimeException {
    public GeminiQuotaException(String message) {
        super(message);
    }
}
