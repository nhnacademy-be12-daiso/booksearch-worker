package com.nhnacademy.bookssearchworker.search.component.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.bookssearchworker.search.component.AiClient;
import com.nhnacademy.bookssearchworker.search.config.SearchUtils;
import com.nhnacademy.bookssearchworker.search.domain.Book;
import com.nhnacademy.bookssearchworker.search.dto.AiResultDto;
import com.nhnacademy.bookssearchworker.search.exception.LlmAnalysisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmAnalysisClient {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public Map<String, AiResultDto> analyzeBooks(String userQuery, List<Book> books) {
        try {
            String prompt = createEvaluationPrompt(userQuery, books);
            String rawResponse = aiClient.generateAnswer(prompt);

            if (rawResponse == null || rawResponse.isBlank() || rawResponse.equals("{}")) {
                log.warn("Gemini가 분석 결과로 빈 JSON을 반환했습니다.");
                return Collections.emptyMap();
            }

            String jsonResponse = rawResponse
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            return objectMapper.readValue(jsonResponse, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("[LlmAnalysisClient] 도서 분석 실패. Query: {}", userQuery, e);
            throw new LlmAnalysisException("Gemini 분석 및 파싱 오류", e);
        }
    }

    private String createEvaluationPrompt(String userQuery, List<Book> books) {
        StringBuilder bookInfo = new StringBuilder();

        for (Book book : books) {
            String desc = SearchUtils.stripHtml(book.getDescription());
            if (desc.length() > 150) desc = desc.substring(0, 50);

            bookInfo.append(String.format("| ISBN: %s | 제목: %s | 설명: %s... |\n",
                    book.getIsbn(), book.getTitle(), desc));
        }

        return String.format("""
                질문: "%s"
                위 목록(총 %d권)을 분석해.

                [규칙]
                1. **matchRate**: 질문 관련성(0~99). 관련 없으면 최소 10점을 줄 것.
                2. **reason**: **장점/단점 없이**, 오직 질문에 맞춘 '추천 이유'만 작성할 것.
                   - **하나의 문자열**로 반환할 것.
                   - 길이는 **100~150자**로 제한할 것.
                   - 질문('%s')과의 연결고리를 반드시 포함할 것.
                3. 🔥 **중요: 점수가 낮아도 절대 제외하지 말고, 목록에 있는 모든 책을 포함할 것.**
                4. 결과는 **JSON** 포맷만 반환.

                [도서 목록]
                %s

                [JSON 반환 예시]
                {
                  "9791162244222": {
                    "reason": "질문과 직접 연결되는 추천 이유(100~150자). 예: 사용자의 요구를 충족하는 실무 중심 예제와 최신 사례를 제공하여 빠른 적용이 가능함.",
                    "matchRate": 95
                  }
                }
                """, userQuery, books.size(), userQuery, bookInfo.toString());
    }
}
