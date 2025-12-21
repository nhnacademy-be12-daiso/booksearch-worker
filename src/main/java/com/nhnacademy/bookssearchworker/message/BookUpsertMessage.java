package com.nhnacademy.bookssearchworker.message;

import java.time.LocalDate;

public record BookUpsertMessage(
        String requestId,
        BookPayload book,
        long ts,
        String reason
) {
    public record BookPayload(
            String isbn,
            String title,
            String author,
            String publisher,
            String description,
            LocalDate pubDate,
            Integer price,
            String imageUrl
    ) {}
}
