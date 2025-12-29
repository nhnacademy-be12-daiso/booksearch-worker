
package com.nhnacademy.bookssearchworker.search.component.ai;

import com.nhnacademy.bookssearchworker.search.component.AiClient;
import com.nhnacademy.bookssearchworker.search.domain.Book;
import com.nhnacademy.bookssearchworker.search.exception.RerankingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@SpringJUnitConfig(classes = RerankingClientTest.Config.class)
class RerankingClientTest {

    @Configuration
    @Import(RerankingClient.class)
    static class Config {}

    @Autowired
    RerankingClient rerankingClient;

    @MockitoBean
    AiClient aiClient;

    @Test
    @DisplayName("리랭킹 요청 시 후보 책 설명의 HTML 태그를 제거하고 50자까지만 전달한다")
    void stripsHtmlAndTruncatesDescription() {
        Book b = Book.builder()
                .isbn("111")
                .title("제목")
                .description("<p>설명<strong>강조</strong> 그리고 <a href='x'>링크</a> 입니다. 이 뒤는 잘려야 합니다.</p>")
                .build();

        given(aiClient.rerank(anyString(), anyList())).willReturn(List.of(Map.of("score", 0.5)));

        rerankingClient.rerank("query", List.of(b));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        then(aiClient).should().rerank(eq("query"), captor.capture());

        List<String> docs = captor.getValue();
        assertThat(docs).hasSize(1);

        String sent = docs.get(0);
        assertThat(sent).startsWith("제목 ");
        assertThat(sent).doesNotContain("<p>").doesNotContain("</p>").doesNotContain("<strong>");
        assertThat(sent.length()).isLessThanOrEqualTo("제목 ".length() + 50);
    }

    @Test
    @DisplayName("AiClient 호출이 실패하면 RerankingException으로 감싸서 던진다")
    void failure_wrapsRerankingException() {
        given(aiClient.rerank(anyString(), anyList())).willThrow(new RuntimeException("down"));

        Book b = Book.builder().isbn("111").title("t").description("d").build();

        assertThatThrownBy(() -> rerankingClient.rerank("q", List.of(b)))
                .isInstanceOf(RerankingException.class)
                .hasMessageContaining("Rerank API 호출 오류")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
