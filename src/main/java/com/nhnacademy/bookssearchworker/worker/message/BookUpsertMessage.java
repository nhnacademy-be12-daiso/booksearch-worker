package com.nhnacademy.bookssearchworker.worker.message;

import java.time.LocalDate;
import java.util.List;

public record BookUpsertMessage(
        String requestId,
        BookPayload book,
        long ts,
        String reason
) {
    public record BookPayload(
            Long id,
            String isbn,
            String title,
            String author,
            String publisher,
            String description,
            LocalDate pubDate,
            Integer price,
            List<String> categories,
            String imageUrl
    ) {}
}
