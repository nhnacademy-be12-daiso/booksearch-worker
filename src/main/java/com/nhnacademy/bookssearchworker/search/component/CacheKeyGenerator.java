package com.nhnacademy.bookssearchworker.search.component;

import kr.co.shineware.nlp.komoran.constant.DEFAULT_MODEL;
import kr.co.shineware.nlp.komoran.core.Komoran;
import kr.co.shineware.nlp.komoran.model.Token;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CacheKeyGenerator {

    private final Komoran komoran = new Komoran(DEFAULT_MODEL.FULL);

    // 불용어 (캐시 키에서 제외할 단어들)
    private static final Set<String> STOP_WORDS = Set.of(
            "추천", "책", "도서", "좀", "해줘", "알려줘", "찾아줘", "무슨", "어떤", "검색"
    );

    /**
     * @param type "basic" 또는 "ai"
     * @param query 사용자 질문
     * @return 예: "ai:부트:스프링"
     */
    public String generateKey(String type, String query) {
        // 1. 특수문자 제거
        String cleanQuery = query.replaceAll("[^가-힣a-zA-Z0-9]", " ");

        // 2. 명사(NNG, NNP) 및 영어(SL) 추출 + 불용어 제거
        List<String> keywords = komoran.analyze(cleanQuery).getTokenList().stream()
                .filter(token -> token.getPos().startsWith("NN") || token.getPos().equals("SL"))
                .map(Token::getMorph)
                .filter(word -> !STOP_WORDS.contains(word))
                .sorted() // 가나다순 정렬 (순서 무관하게 만들기)
                .collect(Collectors.toList());

        // 키워드가 없으면 원문 사용 (예: "안녕하세요")
        String normalizedQuery = keywords.isEmpty() ? cleanQuery.trim().replaceAll("\\s+", "_") : String.join(":", keywords);

        return type + ":" + normalizedQuery;
    }
}