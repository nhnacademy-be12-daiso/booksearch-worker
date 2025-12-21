package com.nhnacademy.bookssearchworker.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.nhnacademy.bookssearchworker.message.BookUpsertMessage;
import org.springframework.stereotype.Component;

/**
 * ES 문서 / 메시지 -> 임베딩 입력 텍스트 생성(SRP)
 */
@Component
public class EmbeddingTextBuilder {

    public String build(JsonNode src) {
        if (src == null || src.isNull()) return "";

        String title = text(src, "title");
        String author = text(src, "author");
        String publisher = text(src, "publisher");
        String description = text(src, "description");
        String reviewSummary = text(src, "reviewSummary");
        String reviewContent = text(src, "review_content");

        return """
                [TITLE]
                %s

                [AUTHOR]
                %s

                [PUBLISHER]
                %s

                [DESCRIPTION]
                %s

                [REVIEW_SUMMARY]
                %s

                [REVIEW_CONTENT]
                %s
                """.formatted(title, author, publisher, description, reviewSummary, reviewContent);
    }

    public String build(BookUpsertMessage.BookPayload book) {
        if (book == null) return "";

        String title = safe(book.title());
        String author = safe(book.author());
        String publisher = safe(book.publisher());
        String description = safe(book.description());

        return """
                [TITLE]
                %s

                [AUTHOR]
                %s

                [PUBLISHER]
                %s

                [DESCRIPTION]
                %s
                """.formatted(title, author, publisher, description);
    }

    // null 안전 처리
    private String text(JsonNode src, String field) {
        JsonNode n = src.get(field);
        if (n == null || n.isNull()) return "";
        return n.asText("");
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
