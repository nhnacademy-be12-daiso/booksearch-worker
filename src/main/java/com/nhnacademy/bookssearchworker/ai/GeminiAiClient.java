package com.nhnacademy.bookssearchworker.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.bookssearchworker.exception.GeminiQuotaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAiClient {

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai.gemini-url}")
    private String geminiUrl;

    @Value("${app.ai.gemini-api-key}")
    private String apiKey;

    /**
     * [변경] 다건 분석 요청
     * @param books 분석할 책 정보 리스트
     * @return ISBN을 Key로 하는 분석 결과 Map
     */
    public Map<String, AiResult> analyzeBulk(List<BookInfo> books) {
        if (books.isEmpty()) return Map.of();

        // 1. 프롬프트 구성 (JSON Array로 책 정보를 넘김)
        StringBuilder booksJson = new StringBuilder("[");
        for (BookInfo b : books) {
            String cleanDesc = (b.description() == null) ? "" : b.description();
            if (cleanDesc.length() > 300) cleanDesc = cleanDesc.substring(0, 300); // 토큰 절약을 위해 길이 제한 강화

            booksJson.append(String.format("{\"isbn\":\"%s\", \"title\":\"%s\", \"author\":\"%s\", \"description\":\"%s\"},",
                    b.isbn(), b.title(), b.author(), cleanDesc.replace("\"", "'").replace("\n", " ")));
        }
        if (booksJson.length() > 1) booksJson.setLength(booksJson.length() - 1); // 마지막 콤마 제거
        booksJson.append("]");

        String prompt = """
                너는 도서 쇼핑몰의 전문 도서 분석 AI다. 다음 책들의 목록(JSON)을 보고 분석하여 결과를 반환하라.
                
                [분석 규칙]
                1. 결과는 반드시 **ISBN을 Key로 갖는 JSON 객체**여야 한다.
                2. 각 책마다 'pros', 'cons', 'recommendedFor' (각각 문자열 배열)를 포함하라.
                3. **[핵심] 내용 유추:** 책 설명이 없거나 부족할 경우, 절대 "정보 부족", "설명 없음", "판단 불가"와 같은 말을 적지 마라. 대신 책 제목, 저자, 카테고리를 통해 일반적인 장단점을 유추하여 작성하라.
                4. **[핵심] 가독성:** 모든 문장은 서술형(~다)이 아닌, **핵심 키워드 위주의 명사형 종결(개조식)**로 작성하라. (예: "설명이 친절함" -> "친절하고 쉬운 설명")
                5. **[핵심] 분량:** 각 항목은 공백 포함 20자 내외로 짧고 간결하게 작성하여 한눈에 들어오게 하라.
                6. 응답은 오직 JSON 문자열만 출력하라. (마크다운, 코드블럭 ```json 금지)
                
                [도서 목록]
                %s
                
                [출력 예시]
                {
                  "9791162244222": {
                      "pros": ["직관적인 예제 구성", "최신 트렌드 반영", "입문자 맞춤형 난이도"],
                      "cons": ["심화 내용 부족", "실습 환경 설정 복잡"],
                      "recommendedFor": ["C언어 입문자", "비전공자", "빠르게 기초를 떼고 싶은 분"]
                  }
                }
        """.formatted(booksJson.toString());

        // 2. API 호출
        Map<String, Object> body = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{Map.of("text", prompt)})
                }
        );

        try {
            Map resp = webClient.post()
                    .uri(geminiUrl + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.value() == 429, response ->
                            response.bodyToMono(String.class).map(errorBody ->
                                    new GeminiQuotaException("Gemini Quota Exceeded: " + errorBody)
                            )
                    )
                    .bodyToMono(Map.class)
//                    .timeout(Duration.ofSeconds(90)) // 배치라 시간이 좀 더 걸릴 수 있음
                    .block();

            String text = extractText(resp);
            return parseBulkResult(text);

        } catch(GeminiQuotaException e) {
            log.warn("[Gemini] Quota limit reached.");
            throw e;
        } catch (Exception e) {
            log.error("[Gemini] Bulk analysis failed", e);
            throw new RuntimeException("Gemini bulk call failed", e);
        }
    }

    // JSON String -> Map<String, AiResult> 파싱
    private Map<String, AiResult> parseBulkResult(String jsonText) {
        try {
            // 마크다운 코드블럭이 포함된 경우 제거
            if (jsonText.startsWith("```json")) {
                jsonText = jsonText.substring(7);
            }
            if (jsonText.startsWith("```")) {
                jsonText = jsonText.substring(3);
            }
            if (jsonText.endsWith("```")) {
                jsonText = jsonText.substring(0, jsonText.length() - 3);
            }

            return objectMapper.readValue(jsonText, new TypeReference<Map<String, AiResult>>() {});
        } catch (Exception e) {
            log.error("[Gemini] JSON Parsing Error. text={}", jsonText, e);
            return new HashMap<>(); // 파싱 실패 시 빈 맵 반환 (전체 실패 처리됨)
        }
    }

    // (기존 extractText 메서드 유지)
    private String extractText(Map resp) {
        try {
            List candidates = (List) resp.get("candidates");
            Map candidate = (Map) candidates.get(0);
            Map content = (Map) candidate.get("content");
            List parts = (List) content.get("parts");
            Map part = (Map) parts.get(0);
            return (String) part.get("text");
        } catch (Exception e) {
            return "{}";
        }
    }

    // 내부 전송용 DTO
    public record BookInfo(String isbn, String title, String author, String description) {}

    // 결과 DTO
    public record AiResult(List<String> pros, List<String> cons, List<String> recommendedFor) {}
}