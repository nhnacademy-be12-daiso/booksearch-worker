
package com.nhnacademy.bookssearchworker.search.component.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.bookssearchworker.search.component.AiClient;
import com.nhnacademy.bookssearchworker.search.domain.Book;
import com.nhnacademy.bookssearchworker.search.dto.AiResultDto;
import com.nhnacademy.bookssearchworker.search.exception.LlmAnalysisException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@SpringJUnitConfig(classes = LlmAnalysisClientTest.Config.class)
class LlmAnalysisClientTest {

    @Configuration
    @Import(LlmAnalysisClient.class)
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    LlmAnalysisClient llmAnalysisClient;

    @MockitoBean
    AiClient aiClient;

    @Test
    @DisplayName("Gemini 응답이 null/blank/{} 이면 빈 맵을 반환한다")
    void blankOrEmptyJson_returnsEmptyMap() {
        Book b = Book.builder().isbn("111").title("A").description("d").build();

        given(aiClient.generateAnswer(anyString())).willReturn(null);
        assertThat(llmAnalysisClient.analyzeBooks("q", List.of(b))).isEmpty();

        given(aiClient.generateAnswer(anyString())).willReturn("   ");
        assertThat(llmAnalysisClient.analyzeBooks("q", List.of(b))).isEmpty();

        given(aiClient.generateAnswer(anyString())).willReturn("{}");
        assertThat(llmAnalysisClient.analyzeBooks("q", List.of(b))).isEmpty();
    }

    @Test
    @DisplayName("```json ... ``` 형태의 응답이면 코드펜스를 제거하고 JSON으로 파싱한다")
    void parsesJsonWithCodeFence() {
        Book b = Book.builder().isbn("9791162244222").title("T").description("<p>desc</p>").build();

        String response = """
```json
{
  \"9791162244222\": { \"reason\": \"이유\", \"matchRate\": 95 }
}
```
""";

        given(aiClient.generateAnswer(anyString())).willReturn(response);

        Map<String, AiResultDto> result = llmAnalysisClient.analyzeBooks("질문", List.of(b));

        assertThat(result).containsKey("9791162244222");
        assertThat(result.get("9791162244222").matchRate()).isEqualTo(95);
        assertThat(result.get("9791162244222").reason()).isEqualTo("이유");
    }

    @Test
    @DisplayName("파싱 실패나 호출 실패 시 LlmAnalysisException으로 감싸서 던진다")
    void failure_wrapsLlmAnalysisException() {
        Book b = Book.builder().isbn("111").title("A").description("d").build();

        given(aiClient.generateAnswer(anyString())).willReturn("not-json");

        assertThatThrownBy(() -> llmAnalysisClient.analyzeBooks("q", List.of(b)))
                .isInstanceOf(LlmAnalysisException.class)
                .hasMessageContaining("Gemini 분석 및 파싱 오류");

        given(aiClient.generateAnswer(anyString())).willThrow(new RuntimeException("down"));

        assertThatThrownBy(() -> llmAnalysisClient.analyzeBooks("q", List.of(b)))
                .isInstanceOf(LlmAnalysisException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
