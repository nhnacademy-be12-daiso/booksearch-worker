package com.nhnacademy.bookssearchworker.search.component.ai;

import com.nhnacademy.bookssearchworker.search.component.AiClient;
import com.nhnacademy.bookssearchworker.search.exception.EmbeddingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingClient {
    private final AiClient aiClient;

    public List<Float> createEmbedding(String text) {
        try {
            List<Double> embedding = aiClient.generateEmbedding(text);
            if (embedding == null || embedding.isEmpty()) return Collections.emptyList();
            return embedding.stream().map(Double::floatValue).toList();
        } catch (Exception e) {
            // 여기서 로그를 남기고 상위로 예외를 던짐
            log.error("[EmbeddingClient] 임베딩 생성 실패. Query: {}", text, e);
            throw new EmbeddingException("Gemini 임베딩 API 호출 오류", e);
        }
    }
}