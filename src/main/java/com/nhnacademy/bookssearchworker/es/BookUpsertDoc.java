package com.nhnacademy.bookssearchworker.es;

import java.time.LocalDate;
import java.util.List;

/**
 * ES upsert용 도서 문서
 */
public record BookUpsertDoc(
        String isbn,
        String title,
        String author,
        String publisher,
        String description,
        LocalDate pubDate,
        Integer price,
        String image_url,
        List<Float> embedding
) {}
