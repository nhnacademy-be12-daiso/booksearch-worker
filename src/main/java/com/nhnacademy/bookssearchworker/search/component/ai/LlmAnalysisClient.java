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
                // 빈 응답이 오면 로그를 남겨서 알 수 있게 함
                log.warn("⚠️ Gemini가 분석 결과로 빈 JSON({})을 반환했습니다. (모든 책이 기준 미달로 판단됨)");
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
            if (desc.length() > 150) desc = desc.substring(0, 150);

            bookInfo.append(String.format("| ISBN: %s | 제목: %s | 설명: %s... |\n",
                    book.getIsbn(), book.getTitle(), desc));
        }

        return String.format("""
질문: "%s"
위 목록(총 %d권)을 분석해.

[규칙]
1. matchRate: 질문 관련성(0~99). 관련이 거의 없으면 10을 기본으로 줄 것.
2. reason: 질문('%s')과의 연결고리를 분명히 하여 '왜 이 책을 추천하는지' 핵심 근거를 설명한 문장(한국어, 100자 이내, 줄바꿈 금지).
3. 모든 책을 포함해서 각각에 대해 작성할 것. 점수가 낮아도 절대 제외하지 말 것.
4. 결과는 JSON 포맷만 반환. 값은 다음 스키마를 따를 것: key는 ISBN, value는 { "reason": "...", "matchRate": NN }.

[도서 목록]
%s

[예시]
{
  "9791162244222": {
    "reason": "실무 예제가 많아 질문 실무적용에 도움",
    "matchRate": 95
  }
}
""", userQuery, books.size(), userQuery, bookInfo.toString());
    }

}