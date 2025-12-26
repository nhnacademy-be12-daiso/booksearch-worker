package com.nhnacademy.bookssearchworker.search.component;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class QueryPreprocessor {

    // 1. 불용어 (기존 유지)
    private static final List<String> STOP_PATTERNS = Arrays.asList(
            "도움이 될만한", "도움되는", "도움 되는", "도움되는",
            "공부하고 싶은데", "공부하고 싶어", "공부하고", "공부하는데",
            "추천 좀 해줄수 있어", "추천해줄수 있어", "해줄수 있어", "할수 있어",
            "추천해줘", "추천해", "추천 좀", "알려줘", "찾아줘",
            "에 대해서", "에 대해", "관련된", "관련한", "관련",
            "싶은데", "싶은", "싶어",
            "책", "도서", "교재", "좀", "해줘", "있는"
    );

    public String extractKeywords(String sentence) {
        if (sentence == null || sentence.isBlank()) return "";

        // 1) 자주 들어오는 기술 키워드의 대소문자/표기 보정 (예: c언어 -> C언어, sql -> SQL)
        // 입력이 제각각이어도 검색 품질이 크게 흔들리지 않게 하기 위함
        String fixedQuery = fixCapitalization(sentence);

        // [2단계] 불용어 제거 (기존 로직)
        for (String pattern : STOP_PATTERNS) {
            fixedQuery = fixedQuery.replace(pattern, " ");
        }

        // [3단계] 특수문자 제거 (C++, C# 등은 살려야 하므로 로직 보완)
        // 알파벳, 숫자, 한글, 공백, 그리고 (+, #) 기호는 살림
        fixedQuery = fixedQuery.replaceAll("[^a-zA-Z0-9가-힣\\s+#]", " ");

        return fixedQuery.replaceAll("\\s+", " ").trim();
    }

    /**
     * 특정 프로그래밍 언어나 고유명사를 강제로 대문자화 하는 메서드
     */
    private String fixCapitalization(String query) {
        String result = query;

        // (?i) : 대소문자 구분 없이 찾겠다는 정규식 플래그

        // c언어 -> C언어
        result = result.replaceAll("(?i)c언어", "C언어");

        // c++ -> C++ (특수문자 이스케이프 주의)
        result = result.replaceAll("(?i)c\\+\\+", "C++");

        // c# -> C#
        result = result.replaceAll("(?i)c#", "C#");

        // sql -> SQL
        result = result.replaceAll("(?i)sql", "SQL");

        // msa -> MSA
        result = result.replaceAll("(?i)msa", "MSA");

        // jpa -> JPA
        result = result.replaceAll("(?i)jpa", "JPA");

        // api -> API
        result = result.replaceAll("(?i)api", "API");

        return result;
    }
}