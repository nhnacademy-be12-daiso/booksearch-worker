package com.nhnacademy.bookssearchworker.search.config;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.regex.Pattern;

public class SearchUtils {

    private static final Pattern STOP_WORDS = Pattern.compile("(?i)(추천|해줘|해 주라|관련|도서|책|알려줘|찾아줘|소개|목록|검색|리스트|보여줘)");
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern ROLE_REGEX = Pattern.compile("(지음|지은이|옮김|옮긴이|글|글쓴이|그림|그린이|엮음|엮은이|편|편집|저|공연구책임|감수)");

    // 검색어 정제
    public static String extractKeywords(String query) {
        if (query == null) return "";
        String refined = STOP_WORDS.matcher(query).replaceAll("")
                .replaceAll("\\s+", " ")
                .trim();
        return refined.isBlank() ? query : refined;
    }

    // HTML 태그 제거
    public static String stripHtml(String content) {
        return content == null ? "" : HTML_TAGS.matcher(content).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    // 저자 이름 정제
    public static String cleanAuthorName(String original) {
        if (original == null || original.isBlank()) return "";
        String normalized = original.replaceAll("[;/|]", ",")
                .replaceAll("\\(.*?\\)|\\[.*?\\]", "")
                .replaceAll("\\b" + ROLE_REGEX.pattern() + "\\b", " ")
                .replaceAll("\\s+", " ").trim();

        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .reduce((a, b) -> a + ", " + b)
                .orElse(normalized);
    }

    // 날짜 파싱
    public static LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    // 시그모이드 점수 변환 (AI Score -> 0~100점)
    public static int calculateSigmoidScore(double score) {
        double probability = 1.0 / (1.0 + Math.exp(-score));
        return (int) (probability * 100);
    }
}