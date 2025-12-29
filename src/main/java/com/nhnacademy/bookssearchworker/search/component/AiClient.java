package com.nhnacademy.bookssearchworker.search.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    // WebClient 주입
    private final WebClient webClient;

    // AI 서비스 URL 설정
    @Value("${app.ai.embedding-url}")
    private String embeddingUrl;

    @Value("${app.ai.reranker-url}")
    private String rerankerUrl;

    @Value("${app.ai.gemini-url}")
    private String geminiUrl;

    @Value("${app.ai.gemini-api-key}")
    private String geminiApiKey;

    // 타임아웃 설정
    @Value("${app.ai.timeout.embedding-seconds}")
    private long embeddingTimeoutSeconds;

    @Value("${app.ai.timeout.rerank-seconds}")
    private long rerankTimeoutSeconds;

    @Value("${app.ai.timeout.gemini-seconds}")
    private long geminiTimeoutSeconds;

    // Embedding 생성
    public List<Double> generateEmbedding(String text) {
        long start = System.currentTimeMillis();
        try {
            EmbeddingResponse response = webClient.post()
                    .uri(embeddingUrl)
                    .bodyValue(Map.of("model", "bge-m3", "prompt", text))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            resp -> toRuntimeException("embedding", resp.statusCode(), resp.bodyToMono(String.class)))
                    .bodyToMono(EmbeddingResponse.class)
                    .timeout(Duration.ofSeconds(embeddingTimeoutSeconds))
                    .retryWhen(retrySpec("embedding"))
                    .block();

            if (response == null || response.embedding() == null) {
                log.warn("[AiClient] embedding 응답이 비어있음 ({}ms)", System.currentTimeMillis() - start);
                return Collections.emptyList();
            }

            log.debug("[AiClient] embedding 성공 ({}ms)", System.currentTimeMillis() - start);
            return response.embedding();

        } catch (WebClientResponseException e) {
            log.error("[AiClient] embedding 실패 ({}ms) status={} body={}",
                    System.currentTimeMillis() - start, e.getStatusCode(), truncate(e.getResponseBodyAsString(), 500));
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[AiClient] embedding 실패 ({}ms): {}", System.currentTimeMillis() - start, e.toString());
            return Collections.emptyList();
        }
    }

    // Rerank 수행
    public List<Map<String, Object>> rerank(String query, List<String> texts) {
        long start = System.currentTimeMillis();
        try {
            List<Map<String, Object>> response = webClient.post()
                    .uri(rerankerUrl)
                    .bodyValue(Map.of("query", query, "texts", texts))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            resp -> toRuntimeException("rerank", resp.statusCode(), resp.bodyToMono(String.class)))
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .timeout(Duration.ofSeconds(rerankTimeoutSeconds))
                    .retryWhen(retrySpec("rerank"))
                    .block();

            if (response == null) {
                log.warn("[AiClient] rerank 응답이 null ({}ms)", System.currentTimeMillis() - start);
                return Collections.emptyList();
            }

            log.debug("[AiClient] rerank 성공 ({}ms)", System.currentTimeMillis() - start);
            return response;

        } catch (WebClientResponseException e) {
            log.error("[AiClient] rerank 실패 ({}ms) status={} body={}",
                    System.currentTimeMillis() - start, e.getStatusCode(), truncate(e.getResponseBodyAsString(), 500));
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[AiClient] rerank 실패 ({}ms): {}", System.currentTimeMillis() - start, e.toString());
            return Collections.emptyList();
        }
    }

    // Gemini LLM 응답 생성
    public String generateAnswer(String prompt) {
        long start = System.currentTimeMillis();
        try {
            GeminiRequest request = new GeminiRequest(List.of(new Content(List.of(new Part(prompt)))));

            var uri = UriComponentsBuilder
                    .fromHttpUrl(geminiUrl)
                    .queryParam("key", geminiApiKey)
                    .build(true)
                    .toUri();

            GeminiResponse response = webClient.post()
                    .uri(uri)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            resp -> toRuntimeException("gemini", resp.statusCode(), resp.bodyToMono(String.class)))
                    .bodyToMono(GeminiResponse.class)
                    .timeout(Duration.ofSeconds(geminiTimeoutSeconds))
                    .retryWhen(retrySpec("gemini"))
                    .block();

            if (response != null
                    && response.candidates() != null
                    && !response.candidates().isEmpty()
                    && response.candidates().get(0).content() != null
                    && response.candidates().get(0).content().parts() != null
                    && !response.candidates().get(0).content().parts().isEmpty()
                    && response.candidates().get(0).content().parts().get(0) != null) {

                log.debug("[AiClient] gemini 성공 ({}ms)", System.currentTimeMillis() - start);
                return response.candidates().get(0).content().parts().get(0).text();
            }

            log.warn("[AiClient] gemini 응답 포맷이 예상과 다름 ({}ms)", System.currentTimeMillis() - start);
            return "{}";

        } catch (WebClientResponseException e) {
            // Gemini는 "추천" 성격이면 실패해도 계속 진행하는 전략 OK
            log.warn("[AiClient] Gemini 실패 ({}ms) status={} body={}",
                    System.currentTimeMillis() - start, e.getStatusCode(), truncate(e.getResponseBodyAsString(), 800));
            return "{}";
        } catch (Exception e) {
            log.warn("[AiClient] Gemini 지연/오류. AI 추천 없이 진행 ({}ms): {}",
                    System.currentTimeMillis() - start, e.toString());
            return "{}";
        }
    }

    // 재시도 정책
    private Retry retrySpec(String name) {
        return Retry.backoff(2, Duration.ofMillis(300)) // 총 2회 재시도
                .maxBackoff(Duration.ofSeconds(2))
                .filter(this::isRetryable)
                .doBeforeRetry(rs -> log.warn("[AiClient] {} retry {}: {}",
                        name, rs.totalRetries() + 1, rs.failure().toString()));
    }

    // 재시도 가능한 오류 판단 -> 네트워크 문제, 타임아웃, 5xx, 429
    private boolean isRetryable(Throwable t) {
        if (t instanceof WebClientRequestException) return true;
        if (t instanceof TimeoutException) return true;

        if (t instanceof WebClientResponseException wre) {
            int code = wre.getStatusCode().value();
            return code >= 500 || code == 429;
        }
        return false;
    }

    // 오류 응답을 RuntimeException으로 변환
    private Mono<? extends Throwable> toRuntimeException(String apiName, HttpStatusCode status, Mono<String> bodyMono) {
        return bodyMono.defaultIfEmpty("")
                .map(body -> new RuntimeException(
                        "[AiClient][" + apiName + "] HTTP " + status + " body=" + truncate(body, 800)
                ));
    }

    // 긴 문자열 자르기
    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...(truncated)";
    }

    record EmbeddingResponse(List<Double> embedding) {}
    record GeminiRequest(List<Content> contents) {}
    record Content(List<Part> parts) {}
    record Part(String text) {}
    record GeminiResponse(List<Candidate> candidates) {}
    record Candidate(Content content) {}
}
