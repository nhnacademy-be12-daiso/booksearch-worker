/*
 * NOTE:
 * - This test suite intentionally avoids any real external calls (RabbitMQ / ES / AI).
 * - All external dependencies are replaced using @MockitoBean (Spring Boot Mockito override).
 * - If your project uses older Spring Boot (without @MockitoBean), replace @MockitoBean with @MockBean.
 */
package com.nhnacademy.bookssearchworker.worker.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.bookssearchworker.worker.message.BookUpsertMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = EmbeddingTextBuilderTest.TestConfig.class)
class EmbeddingTextBuilderTest {

    @Import(EmbeddingTextBuilder.class)
    static class TestConfig { }

    @Autowired
    EmbeddingTextBuilder builder;

    private final ObjectMapper om = new ObjectMapper();

    @Test
    @DisplayName("build(JsonNode): title/author/publisher/description/review 필드가 템플릿에 포함된다")
    void buildJsonNode_includesAllSections() throws Exception {
        JsonNode node = om.readTree("""
                {
                  "title": "T",
                  "author": "A",
                  "publisher": "P",
                  "description": "D",
                  "reviewSummary": "RS",
                  "review_content": "RC"
                }
                """);
        String text = builder.build(node);

        assertAll(
                () -> assertTrue(text.contains("[TITLE]"), "TITLE 섹션이 누락됨"),
                () -> assertTrue(text.contains("T"), "title 값이 누락됨"),
                () -> assertTrue(text.contains("[AUTHOR]") && text.contains("A"), "author 섹션/값 누락"),
                () -> assertTrue(text.contains("[PUBLISHER]") && text.contains("P"), "publisher 섹션/값 누락"),
                () -> assertTrue(text.contains("[DESCRIPTION]") && text.contains("D"), "description 섹션/값 누락"),
                () -> assertTrue(text.contains("[REVIEW_SUMMARY]") && text.contains("RS"), "reviewSummary 섹션/값 누락"),
                () -> assertTrue(text.contains("[REVIEW_CONTENT]") && text.contains("RC"), "review_content 섹션/값 누락")
        );
    }

    @Test
    @DisplayName("build(JsonNode): null 또는 필드 누락은 빈 문자열로 안전 처리된다")
    void buildJsonNode_nullSafe() throws Exception {
        assertEquals("", builder.build((JsonNode) null), "null 입력은 빈 문자열이어야 함");

        JsonNode node = om.readTree("""
                { "title": "OnlyTitle" }
                """);
        String text = builder.build(node);

        assertAll(
                () -> assertTrue(text.contains("OnlyTitle"), "title 포함 실패"),
                () -> assertTrue(text.contains("[AUTHOR]"), "AUTHOR 섹션 누락"),
                () -> assertTrue(text.contains("[PUBLISHER]"), "PUBLISHER 섹션 누락"),
                () -> assertTrue(text.contains("[DESCRIPTION]"), "DESCRIPTION 섹션 누락")
        );
    }

    @Test
    @DisplayName("build(BookPayload): book 필드 기반 텍스트가 생성된다")
    void buildBookPayload() {
        BookUpsertMessage.BookPayload book = new BookUpsertMessage.BookPayload(
                1L, "9780000000001", "Title", "Author", "Publisher",
                "Desc", LocalDate.of(2025, 1, 1), 10000, List.of("C1","C2"),
                "img", 10L, 20L
        );

        String text = builder.build(book);

        assertAll(
                () -> assertTrue(text.contains("Title"), "title 누락"),
                () -> assertTrue(text.contains("Author"), "author 누락"),
                () -> assertTrue(text.contains("Publisher"), "publisher 누락"),
                () -> assertTrue(text.contains("Desc"), "description 누락")
        );
    }
}
