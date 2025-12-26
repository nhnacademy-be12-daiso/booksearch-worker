package com.nhnacademy.bookssearchworker.worker.message;

public record BookDeleteMessage(
        String requestId,
        String isbn,
        long ts,
        String reason
) {}
