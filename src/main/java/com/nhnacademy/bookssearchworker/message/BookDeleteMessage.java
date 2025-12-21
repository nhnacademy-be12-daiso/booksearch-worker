package com.nhnacademy.bookssearchworker.message;

public record BookDeleteMessage(
        String requestId,
        String isbn,
        long ts,
        String reason
) {}
