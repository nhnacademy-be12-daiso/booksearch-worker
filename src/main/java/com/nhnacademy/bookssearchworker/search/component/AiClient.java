package com.nhnacademy.bookssearchworker.search.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final WebClient webClient;

    @Value("${app.ai.embedding-url}")
    private String embeddingUrl;

    @Value("${app.ai.reranker-url}")
    private String rerankerUrl;

    @Value("${app.ai.gemini-url}")
    private String geminiUrl;

    @Value("${app.ai.gemini-api-key}")
    private String geminiApiKey;

    // 1. 임베딩: 필수 -> 타임아웃 X (서버 응답 기다림)
    // 2. 리랭킹: 필수 -> 타임아웃 X (서버 응답 기다림)

    public List<Double> generateEmbedding(String text) {
        try {
            Map response = webClient.post().uri(embeddingUrl)
                    .bodyValue(Map.of("model", "bge-m3", "prompt", text))
                    .retrieve()
                    .bodyToMono(Map.class)
                    // .timeout(...) <-- 제거됨
                    .retryWhen(Retry.fixedDelay(2, Duration.ofMillis(300)))
                    .block();
            return (List<Double>) response.get("embedding");
        } catch (Exception e) {
            log.error("[AiClient] 임베딩 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> rerank(String query, List<String> texts) {
        try {
            return webClient.post().uri(rerankerUrl)
                    .bodyValue(Map.of("query", query, "texts", texts))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    // .timeout(...) <-- 제거됨
                    .block();
        } catch (Exception e) {
            log.error("[AiClient] 리랭킹 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public String generateAnswer(String prompt) {
        try {
            GeminiRequest request = new GeminiRequest(List.of(new Content(List.of(new Part(prompt)))));

            GeminiResponse response = webClient.post()
                    .uri(geminiUrl + "?key=" + geminiApiKey)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .block();

            if (response != null && !response.candidates().isEmpty()) {
                return response.candidates().get(0).content().parts().get(0).text();
            }
            return "{}";
        } catch (Exception e) {
            log.warn("[AiClient] Gemini 응답 오류. AI 추천 없이 진행.");
            return "{}";
        }
    }

    // DTO Records
    record GeminiRequest(List<Content> contents) {}
    record Content(List<Part> parts) {}
    record Part(String text) {}
    record GeminiResponse(List<Candidate> candidates) {}
    record Candidate(Content content) {}
}