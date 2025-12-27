package com.nhnacademy.bookssearchworker.worker.es;

import java.time.LocalDate;
import java.util.List;

/**
 * ES upsert용 도서 문서
 */
public record BookUpsertDoc(
        String isbn,
        Long id,
        String title,
        String author,
        String publisher,
        String description,
        LocalDate pubDate,
        Integer price,
        List<String> categories,
        String image_url,
        Long publisherId,
        Long categoryId,
        List<Float> embedding
) {}
