package com.nhnacademy.bookssearchworker.search.component.ai;

import com.nhnacademy.bookssearchworker.search.component.AiClient;
import com.nhnacademy.bookssearchworker.search.domain.Book;
import com.nhnacademy.bookssearchworker.search.exception.RerankingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RerankingClient {
    private final AiClient aiClient;

    public List<Map<String, Object>> rerank(String query, List<Book> candidates) {
        try {
            // 텍스트 변환 로직도 여기에 숨김
            List<String> docTexts = candidates.stream()
                    .map(b -> b.getTitle() + " " + stripHtml(b.getDescription()))
                    .toList();
            log.info("[RerankingClient] 리랭킹 요청. Query: {}, Docs: {}", query, docTexts);
            return aiClient.rerank(query, docTexts);
        } catch (Exception e) {
            log.error("[RerankingClient] 리랭킹 실패. Query: {}", query, e);
            throw new RerankingException("Rerank API 호출 오류", e);
        }
    }

    // 간단한 문자열 처리는 private으로 내부에 둠
    private String stripHtml(String html) {
        if (html == null) return "";
        String stripped = html.replaceAll("<[^>]*>", "");
        return stripped.substring(0, Math.min(stripped.length(), 50));
    }
}