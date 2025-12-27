package com.nhnacademy.bookssearchworker.worker.embedding;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OllamaEmbeddingClient {

    private final WebClient webClient;

    @Value("${app.ai.embedding-url}")
    private String embeddingUrl;

    @Value("${app.ai.embedding-model}")
    private String model;

    // 너희 ES mapping이 1024라면 이 값도 같이 맞춰서 강제 검증
    @Value("${app.ai.embedding-dim:1024}")
    private int expectedDim;

    public List<Float> embed(String text) {
        if (text == null) text = "";

        // Ollama embeddings는 prompt 키가 맞는 환경
        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", text
        );

        Map<String, Object> resp = webClient.post()
                .uri(embeddingUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(60))
                .block();

        if (resp == null) {
            throw new IllegalStateException("embedding response is null");
        }

        List<Float> vec = parseEmbedding(resp);

        // 길이 검증 (여기서 걸러서 ES 400 재시도 지옥 방지)
        if (vec == null || vec.isEmpty()) {
            throw new IllegalStateException("embedding is empty");
        }
        if (expectedDim > 0 && vec.size() != expectedDim) {
            throw new IllegalStateException("embedding dimension mismatch: got=" + vec.size() + ", expected=" + expectedDim);
        }

        return vec;
    }

    @SuppressWarnings("unchecked")
    private List<Float> parseEmbedding(Map<String, Object> resp) {
        // 케이스1) { "embedding": [...] }
        if (resp.containsKey("embedding")) {
            Object raw = resp.get("embedding");
            if (raw instanceof List<?> list) {
                return list.stream()
                        .map(x -> ((Number) x).floatValue())
                        .toList();
            }
        }

        // 케이스2) { "data": [ { "embedding": [...] } ] }
        if (resp.containsKey("data")) {
            Object raw = resp.get("data");
            if (raw instanceof List<?> data && !data.isEmpty()) {
                Object first = data.get(0);
                if (first instanceof Map<?, ?> m) {
                    Object emb = m.get("embedding");
                    if (emb instanceof List<?> list) {
                        return list.stream()
                                .map(x -> ((Number) x).floatValue())
                                .toList();
                    }
                }
            }
        }

        throw new IllegalStateException("Unknown embedding response schema: " + resp.keySet());
    }
}
