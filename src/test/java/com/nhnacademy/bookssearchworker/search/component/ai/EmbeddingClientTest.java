
package com.nhnacademy.bookssearchworker.search.component.ai;

import com.nhnacademy.bookssearchworker.search.component.AiClient;
import com.nhnacademy.bookssearchworker.search.exception.EmbeddingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@SpringJUnitConfig(classes = EmbeddingClientTest.Config.class)
class EmbeddingClientTest {

    @Configuration
    @Import(EmbeddingClient.class)
    static class Config {}

    @Autowired
    EmbeddingClient embeddingClient;

    @MockitoBean
    AiClient aiClient;

    @Test
    @DisplayName("AiClient가 Double 임베딩을 반환하면 Float로 변환한다")
    void convertsDoubleToFloat() {
        given(aiClient.generateEmbedding("hello")).willReturn(List.of(1.25, 2.5, 3.75));

        List<Float> result = embeddingClient.createEmbedding("hello");

        assertThat(result).containsExactly(1.25f, 2.5f, 3.75f);
    }

    @Test
    @DisplayName("AiClient가 null/빈 리스트를 반환하면 빈 리스트를 반환한다")
    void nullOrEmpty_returnsEmpty() {
        given(aiClient.generateEmbedding("q1")).willReturn(null);
        assertThat(embeddingClient.createEmbedding("q1")).isEmpty();

        given(aiClient.generateEmbedding("q2")).willReturn(List.of());
        assertThat(embeddingClient.createEmbedding("q2")).isEmpty();
    }

    @Test
    @DisplayName("AiClient 호출이 실패하면 EmbeddingException으로 감싸서 던진다")
    void failure_wrapsEmbeddingException() {
        given(aiClient.generateEmbedding("boom")).willThrow(new RuntimeException("down"));

        assertThatThrownBy(() -> embeddingClient.createEmbedding("boom"))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("Gemini 임베딩 API 호출 오류")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
