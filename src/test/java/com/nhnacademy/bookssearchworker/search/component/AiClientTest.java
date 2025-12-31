package com.nhnacademy.bookssearchworker.search.component;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AiClient 테스트 (외부 통신 없음)
 * - WebClient 체인 모킹 대신 ExchangeFunction을 @MockitoBean으로 대체해 응답을 제어한다.
 * - AiClient의 동작은 "성공/실패/응답 포맷 이상/재시도"까지 커버한다.
 */
@SpringBootTest(classes = {
        AiClient.class,
        AiClientTest.WebClientTestConfig.class
})
@TestPropertySource(properties = {
        "app.ai.embedding-url=http://fake.local/embedding",
        "app.ai.reranker-url=http://fake.local/rerank",
        "app.ai.gemini-url=http://fake.local/gemini",
        "app.ai.gemini-api-key=test-key",
        "app.ai.timeout.embedding-seconds=1",
        "app.ai.timeout.rerank-seconds=1",
        "app.ai.timeout.gemini-seconds=1"
})
class AiClientTest {

    @Autowired
    AiClient aiClient;

    @MockitoBean
    ExchangeFunction exchangeFunction;

    @BeforeEach
    void setUp() {
        reset(exchangeFunction);
    }

    @TestConfiguration
    static class WebClientTestConfig {

        /**
         * WebClient를 ExchangeFunction 기반으로 구성.
         * 이 ExchangeFunction 빈은 테스트에서 @MockitoBean으로 override 된다.
         */
        @Bean
        ExchangeFunction exchangeFunction() {
            return request -> Mono.error(new IllegalStateException("ExchangeFunction mock is not configured"));
        }

        @Bean
        WebClient webClient(ExchangeFunction exchangeFunction) {
            return WebClient.builder()
                    .exchangeFunction(exchangeFunction)
                    .build();
        }
    }

    // --------- helpers ---------

    private static ClientResponse okJson(String json) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build();
    }

    private static ClientResponse errorJson(int status, String json) {
        return ClientResponse.create(HttpStatus.valueOf(status))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build();
    }

    private static WebClientResponseException wcre(int status, String body) {
        return WebClientResponseException.create(
                status,
                "status " + status,
                HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
    }

    // =========================
    // 1) Embedding
    // =========================

    @Nested
    @DisplayName("generateEmbedding()")
    class GenerateEmbeddingTests {

        @Test
        @DisplayName("성공: embedding 리스트를 그대로 반환한다")
        void success_returnsEmbedding() {
            // AiClient는 {"embedding":[...]}를 EmbeddingResponse(record)로 받음
            // (generateEmbedding 로직) :contentReference[oaicite:2]{index=2}
            when(exchangeFunction.exchange(any(ClientRequest.class)))
                    .thenReturn(Mono.just(okJson("{\"embedding\":[0.1,0.2,0.3]}")));

            List<Double> embedding = aiClient.generateEmbedding("hello");

            assertThat(embedding)
                    .as("성공 시 embedding 값이 비면 안 됨")
                    .containsExactly(0.1, 0.2, 0.3);

            verify(exchangeFunction, times(1)).exchange(any(ClientRequest.class));
        }

        @Test
        @DisplayName("응답 포맷 이상: embedding 필드가 없으면 emptyList()")
        void emptyBodyOrMissingField_returnsEmptyList() {
            // {"embedding": null} 또는 {} -> response.embedding() == null 케이스
            // (null/비어있음 처리) :contentReference[oaicite:3]{index=3}
            when(exchangeFunction.exchange(any(ClientRequest.class)))
                    .thenReturn(Mono.just(okJson("{}")));

            List<Double> embedding = aiClient.generateEmbedding("hello");

            assertThat(embedding)
                    .as("embedding 필드가 없으면 emptyList 반환")
                    .isEmpty();

            verify(exchangeFunction, times(1)).exchange(any(ClientRequest.class));
        }

        @Test
        @DisplayName("WebClientResponseException: emptyList()로 복구한다")
        void webClientResponseException_returnsEmptyList() {
            // (WebClientResponseException 캐치) :contentReference[oaicite:4]{index=4}
            when(exchangeFunction.exchange(any(ClientRequest.class)))
                    .thenReturn(Mono.error(wcre(500, "server boom")));

            List<Double> embedding = aiClient.generateEmbedding("hello");

            assertThat(embedding)
                    .as("WebClientResponseException이면 emptyList로 복구해야 함")
                    .isEmpty();

            verify(exchangeFunction, times(3)).exchange(any(ClientRequest.class));
        }

        @Test
        @DisplayName("재시도: WebClientRequestException은 최대 2회 재시도 후 성공하면 성공 값 반환")
        void retryable_webClientRequestException_retriesAndSucceeds() {
            // (retrySpec/isRetryable) :contentReference[oaicite:5]{index=5}
            AtomicInteger attempt = new AtomicInteger(0);

            when(exchangeFunction.exchange(any(ClientRequest.class)))
                    .thenAnswer(inv -> {
                        int n = attempt.getAndIncrement();
                        if (n < 2) {
                            // isRetryable -> WebClientRequestException true :contentReference[oaicite:6]{index=6}
                            return Mono.error(new WebClientRequestException(
                                    new IOException("network"),
                                    HttpMethod.POST,
                                    URI.create("http://fake.local/embedding"),
                                    HttpHeaders.EMPTY
                            ));
                        }
                        return Mono.just(okJson("{\"embedding\":[9.9]}"));
                    });

            List<Double> embedding = aiClient.generateEmbedding("hello");

            assertThat(embedding)
                    .as("재시도 후 성공하면 embedding 결과가 나와야 함")
                    .containsExactly(9.9);

            // 최초 1회 + 재시도 2회 = 총 3회 호출 기대
            verify(exchangeFunction, times(3)).exchange(any(ClientRequest.class));
        }

        @Test
        @DisplayName("재시도 안 함: RuntimeException은 isRetryable=false라 1회만 시도한다")
        void nonRetryable_exception_noRetry() {
            when(exchangeFunction.exchange(any(ClientRequest.class)))
                    .thenReturn(Mono.error(new RuntimeException("boom")));

            List<Double> embedding = aiClient.generateEmbedding("hello");

            assertThat(embedding)
                    .as("비재시도 예외는 emptyList로 복구")
                    .isEmpty();

            verify(exchangeFunction, times(1)).exchange(any(ClientRequest.class));
        }
    }

    // =========================
    // 2) Rerank
    // =========================

    @Nested
    @DisplayName("rerank()")
    class RerankTests {

        @Test
        @DisplayName("성공: 결과 리스트를 그대로 반환한다")
        void success_returnsList() {
            // (rerank 로직) :contentReference[oaicite:7]{index=7}
            when(exchangeFunction.exchange(any(ClientRequest.class)))
                    .thenReturn(Mono.just(okJson("[{\"score\":0.9,\"index\":0},{\"score\":0.1,\"index\":1}]")));

            List<Map<String, Object>> result = aiClient.rerank("q", List.of("a", "b"));

            assertThat(result)
                    .as("성공 시 rerank 결과가 2개여야 함")
                    .hasSize(2);

            assertThat(result.get(0))
                    .as("첫 번째 결과 score 확인")
                    .containsEntry("score", 0.9);

            verify(exchangeFunction, times(1)).exchange(any(ClientRequest.class));
        }

        @Test
        @DisplayName("응답이 JSON null이면 response==null로 판단하고 emptyList()")
        void jsonNull_returnsEmptyList() {
            // response == null 처리 :contentReference[oaicite:8]{index=8}
            when(exchangeFunction.exchange(any(ClientRequest.class)))
                    .thenReturn(Mono.just(okJson("null")));

            List<Map<String, Object>> result = aiClient.rerank("q", List.of("a"));

            assertThat(result)
                    .as("JSON null이면 emptyList")
                    .isEmpty();

            verify(exchangeFunction, times(1)).exchange(any(ClientRequest.class));
        }

        @Test
        @DisplayName("WebClientResponseException: emptyList()로 복구한다")
        void webClientResponseException_returnsEmptyList() {
            // (rerank 예외 처리) :contentReference[oaicite:9]{index=9}
            when(exchangeFunction.exchange(any(ClientRequest.class)))
                    .thenReturn(Mono.error(wcre(502, "bad gateway")));

            List<Map<String, Object>> result = aiClient.rerank("q", List.of("a"));

            assertThat(result).as("예외 시 emptyList 복구").isEmpty();
            verify(exchangeFunction, times(3)).exchange(any(ClientRequest.class));
        }

        @Test
        @DisplayName("onStatus 에러: 500 응답이면 emptyList()로 복구한다")
        void http5xx_response_returnsEmptyList() {
            // retrieve().onStatus(...)로 RuntimeException 발생 :contentReference[oaicite:10]{index=10}
            when(exchangeFunction.exchange(any(ClientRequest.class)))
                    .thenReturn(Mono.just(errorJson(500, "{\"message\":\"err\"}")));

            List<Map<String, Object>> result = aiClient.rerank("q", List.of("a"));

            assertThat(result).as("HTTP 5xx면 emptyList 복구").isEmpty();
            verify(exchangeFunction, times(1)).exchange(any(ClientRequest.class));
        }
    }

    // =========================
    // 3) Gemini
    // =========================

    @Nested
    @DisplayName("generateAnswer()")
    class GenerateAnswerTests {

        @Test
        @DisplayName("성공: candidates[0].content.parts[0].text 반환")
        void success_returnsText() {
            // (정상 포맷이면 text 반환) :contentReference[oaicite:11]{index=11}
            when(exchangeFunction.exchange(any(ClientRequest.class)))
                    .thenAnswer(inv -> {
                        ClientRequest req = inv.getArgument(0);
                        String url = req.url().toString();

                        assertThat(url)
                                .as("geminiUrl에 key 파라미터가 붙어야 함 (queryParam(\"key\", apiKey))")
                                .contains("http://fake.local/gemini")
                                .contains("key=test-key");

                        return Mono.just(okJson("""
                                {
                                  "candidates": [
                                    { "content": { "parts": [ { "text": "hello-answer" } ] } }
                                  ]
                                }
                                """));
                    });

            String answer = aiClient.generateAnswer("prompt");

            assertThat(answer)
                    .as("정상 응답이면 텍스트가 반환되어야 함")
                    .isEqualTo("hello-answer");

            verify(exchangeFunction, times(1)).exchange(any(ClientRequest.class));
        }

        @Test
        @DisplayName("포맷 이상: candidates 비어있으면 \"{}\" 반환")
        void badFormat_returnsEmptyJson() {
            // (예상 포맷 아니면 "{}") :contentReference[oaicite:12]{index=12}
            when(exchangeFunction.exchange(any(ClientRequest.class)))
                    .thenReturn(Mono.just(okJson("{\"candidates\":[]}")));

            String answer = aiClient.generateAnswer("prompt");

            assertThat(answer)
                    .as("후보가 없으면 {}로 fallback")
                    .isEqualTo("{}");

            verify(exchangeFunction, times(1)).exchange(any(ClientRequest.class));
        }

        @Test
        @DisplayName("WebClientResponseException: Gemini는 실패해도 \"{}\"로 진행")
        void webClientResponseException_returnsEmptyJson() {
            // (Gemini 예외는 warn 후 "{}") :contentReference[oaicite:13]{index=13}
            when(exchangeFunction.exchange(any(ClientRequest.class)))
                    .thenReturn(Mono.error(wcre(429, "rate limit")));

            String answer = aiClient.generateAnswer("prompt");

            assertThat(answer).as("Gemini 실패 시 {}").isEqualTo("{}");
            verify(exchangeFunction, times(3)).exchange(any(ClientRequest.class));
        }

        @Test
        @DisplayName("onStatus 에러: 500 응답이면 \"{}\" 반환")
        void http5xx_response_returnsEmptyJson() {
            when(exchangeFunction.exchange(any(ClientRequest.class)))
                    .thenReturn(Mono.just(errorJson(500, "{\"message\":\"err\"}")));

            String answer = aiClient.generateAnswer("prompt");

            assertThat(answer).as("HTTP 5xx면 {} 반환").isEqualTo("{}");
            verify(exchangeFunction, times(1)).exchange(any(ClientRequest.class));
        }
    }
}
