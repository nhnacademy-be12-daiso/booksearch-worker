package com.nhnacademy.bookssearchworker.search.service;

import com.nhnacademy.bookssearchworker.search.component.CacheKeyGenerator;
import com.nhnacademy.bookssearchworker.search.component.QueryPreprocessor;
import com.nhnacademy.bookssearchworker.search.component.ai.EmbeddingClient;
import com.nhnacademy.bookssearchworker.search.component.ai.LlmAnalysisClient;
import com.nhnacademy.bookssearchworker.search.component.ai.RerankingClient;
import com.nhnacademy.bookssearchworker.search.component.assembler.SearchResultAssembler;
import com.nhnacademy.bookssearchworker.search.component.engine.ElasticsearchEngine;
import com.nhnacademy.bookssearchworker.search.domain.Book;
import com.nhnacademy.bookssearchworker.search.dto.AiResultDto;
import com.nhnacademy.bookssearchworker.search.dto.BookWithScore;
import com.nhnacademy.bookssearchworker.search.dto.SearchResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchEngine elasticsearchEngine;
    private final EmbeddingClient embeddingClient;
    private final RerankingClient rerankingClient;
    private final LlmAnalysisClient llmClient;

    private final SearchResultAssembler assembler;
    private final QueryPreprocessor queryPreprocessor;
    private final CacheKeyGenerator keyGenerator;
    private final RedisCacheService redisCacheService;

    private static final int RERANK_LIMIT = 10;
    private static final int AI_EVAL_SIZE = 5;

    /**
     * AI 검색
     * - 하이브리드 검색(키워드+벡터)
     * - 상위 10권 리랭킹
     * - Gemini로 책별 추천 이유 생성
     *
     * 외부 서비스 장애 시에는 단계별로 가능한 만큼만 수행합니다.
     */
    public SearchResponseDto aiSearch(String userQuery) {
        String cacheKey = keyGenerator.generateKey("ai", userQuery);
        SearchResponseDto cached = redisCacheService.get(cacheKey, SearchResponseDto.class);
        if (cached != null) {
            log.debug("[AiSearch] 캐시 히트. key={}", cacheKey);
            return cached;
        }

        String refinedQuery = queryPreprocessor.extractKeywords(userQuery);
        log.info("[AiSearch] 정제된 쿼리: {}", refinedQuery);

        // 1) 임베딩 생성: 실패하면 벡터 검색을 제외하고 키워드 검색만 수행
        List<Float> embedding;
        try {
            embedding = embeddingClient.createEmbedding(refinedQuery);
        } catch (Exception e) {
            log.warn("[Fallback] 임베딩 서버 통신 실패 -> 벡터 검색 제외하고 키워드 검색만 진행합니다. msg={}", e.getMessage());
            embedding = Collections.emptyList(); // 빈 리스트면 Repository가 알아서 벡터 검색을 뺌
        }

        // 2) Elasticsearch 하이브리드 검색 (여기서 실패하면 검색 자체가 불가능하므로 예외를 그대로 올립니다.)
        List<Book> candidates;
        try {
            candidates = elasticsearchEngine.search(refinedQuery, embedding);
            log.info("[AiSearch] Elasticsearch 검색 결과 수: {}", candidates.size());
        } catch (Exception e) {
            log.error("[Search] Elasticsearch 검색 실패: query='{}'", refinedQuery, e);
            throw e;
        }
        if (candidates.isEmpty()) return SearchResponseDto.empty();

        // 3) 리랭킹: 실패하면 ES 결과 순서를 그대로 사용
        List<BookWithScore> rankedBooks;
        try {
            // 상위 N개만 리랭킹 시도
            int targetSize = Math.min(candidates.size(), RERANK_LIMIT);
            List<Map<String, Object>> scores = rerankingClient.rerank(refinedQuery, candidates.subList(0, targetSize));

            // 점수 반영
            rankedBooks = assembler.applyRerankScores(candidates, scores, RERANK_LIMIT);
            log.info("[AiSearch] 리랭킹 완료. 상위 권 점수 반영됨.");

        } catch (Exception e) {
            log.warn("[Fallback] 리랭킹 서버 통신 실패 -> 리랭킹 없이 다음 단계로 진행합니다. msg={}", e.getMessage());

            // 리랭킹 실패 시에도 기존 후보 목록은 유지합니다.
            rankedBooks = candidates.stream()
                    .map(b -> new BookWithScore(b, 0.5)) // 기본 점수 부여
                    .toList();
        }

        // 4) Gemini 분석: 실패하면 AI 답변 없이 결과만 반환
        Map<String, AiResultDto> aiAnalysis;
        try {
            List<Book> topBooks = rankedBooks.stream()
                    .limit(AI_EVAL_SIZE)
                    .map(BookWithScore::book)
                    .toList();

            aiAnalysis = llmClient.analyzeBooks(userQuery, topBooks);

        } catch (Exception e) {
            log.warn("[Fallback] Gemini API 통신 실패 -> AI 답변 없이 결과만 반환합니다. msg={}", e.getMessage());
            aiAnalysis = Collections.emptyMap(); // 빈 맵 반환 -> 조립기가 알아서 멘트 생략함
        }

        // 5) 최종 조립 및 캐싱(AI 검색만 캐싱)
        SearchResponseDto result = assembler.assembleAiResult(rankedBooks, aiAnalysis);
        if(!aiAnalysis.isEmpty()) {
            // AI 분석이 포함된 경우에만 캐싱
            redisCacheService.save(cacheKey, result, Duration.ofHours(12));
            log.info("[AiSearch] 결과 캐싱 완료. key={}", cacheKey);
        }
        else {
            log.info("[AiSearch] AI 분석 없음 -> 캐싱 생략");
        }

        return result;
    }

    // 일반 검색: 하이브리드 검색만 수행 (캐싱 없음)
    public SearchResponseDto basicSearch(String userQuery) {
        // ISBN 전용 검색
        if (userQuery.matches("^[0-9-]+$")) {
            log.info("[BasicSearch] ISBN 전용 검색 수행: {}", userQuery);
            return assembler.assembleBasicResult(elasticsearchEngine.searchByIsbn(userQuery));
        }

        String refinedQuery = queryPreprocessor.extractKeywords(userQuery);
        log.info("[BasicSearch] 정제된 쿼리: {}", refinedQuery);

        List<Float> embedding;
        try {
            embedding = embeddingClient.createEmbedding(refinedQuery);
        } catch (Exception e) {
            log.warn("[Fallback] 임베딩 서버 통신 실패 -> 벡터 검색 제외하고 키워드 검색만 진행합니다. msg={}", e.getMessage());
            embedding = Collections.emptyList();
        }

        List<Book> books;
        try {
            books = elasticsearchEngine.search(refinedQuery, embedding);
            log.info("[BasicSearch] Elasticsearch 검색 결과 수: {}", books.size());
        } catch (Exception e) {
            log.error("[Search] Elasticsearch 검색 실패: query='{}'", refinedQuery, e);
            throw e;
        }
        SearchResponseDto result = assembler.assembleBasicResult(books);
        return result;
    }
}