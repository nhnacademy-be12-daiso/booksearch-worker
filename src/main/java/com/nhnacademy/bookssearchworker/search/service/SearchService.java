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
    private static final int AI_EVAL_SIZE = 3;

    /**
     * AI 검색 (모든 단계에 Fallback 적용)
     */
    public SearchResponseDto aiSearch(String userQuery) {
        String cacheKey = keyGenerator.generateKey("ai", userQuery);
        SearchResponseDto cached = redisCacheService.get(cacheKey, SearchResponseDto.class);
        if (cached != null) return cached;

        String refinedQuery = queryPreprocessor.extractKeywords(userQuery);

        // 1단계: 임베딩 생성 (실패 시 -> 빈 리스트 반환 -> 키워드 검색만 수행)
        List<Float> embedding;
        try {
            embedding = embeddingClient.createEmbedding(refinedQuery);
        } catch (Exception e) {
            log.warn("⚠️ [Fallback] 임베딩 실패 (키워드 검색으로 전환): {}", e.getMessage());
            embedding = Collections.emptyList(); // 빈 리스트면 Repository가 알아서 벡터 검색을 뺌
        }

        // 2단계: Elasticsearch 검색 (여기가 실패하면 답이 없으므로 Exception 전파)
        List<Book> candidates = elasticsearchEngine.search(refinedQuery, embedding);
        if (candidates.isEmpty()) return SearchResponseDto.empty();

        // 3단계: 리랭킹 (실패 시 -> ES 원본 순서 유지)
        List<BookWithScore> rankedBooks;
        try {
            // 상위 N개만 리랭킹 시도
            int targetSize = Math.min(candidates.size(), RERANK_LIMIT);
            List<Map<String, Object>> scores = rerankingClient.rerank(refinedQuery, candidates.subList(0, targetSize));

            // 점수 반영
            rankedBooks = assembler.applyRerankScores(candidates, scores, RERANK_LIMIT);

        } catch (Exception e) {
            log.warn("⚠️ [Fallback] 리랭커 서버 오류 (ES 순서 유지): {}", e.getMessage());

            // 🔥 [핵심 수정] 리랭커가 죽어도 기존 찾은 책(candidates)은 버리면 안 됨!
            rankedBooks = candidates.stream()
                    .map(b -> new BookWithScore(b, 0.5)) // 기본 점수 부여
                    .toList();
        }

        // 4단계: Gemini AI 분석 (실패 시 -> 분석 멘트 없이 결과 반환)
        Map<String, AiResultDto> aiAnalysis;
        try {
            // 상위 3권만 분석
            List<Book> topBooks = rankedBooks.stream()
                    .limit(AI_EVAL_SIZE)
                    .map(BookWithScore::book)
                    .toList();

            aiAnalysis = llmClient.analyzeBooks(userQuery, topBooks);

        } catch (Exception e) {
            log.warn("⚠️ [Fallback] Gemini API 오류 (일반 리스트만 반환): {}", e.getMessage());
            aiAnalysis = Collections.emptyMap(); // 빈 맵 반환 -> 조립기가 알아서 멘트 생략함
        }

        // 5단계: 최종 조립 및 캐싱
        SearchResponseDto result = assembler.assembleAiResult(rankedBooks, aiAnalysis);
//        redisCacheService.save(cacheKey, result, Duration.ofHours(12));

        return result;
    }

    // 기본 검색도 동일한 Fallback 패턴 적용
    public SearchResponseDto basicSearch(String userQuery) {
        if (userQuery.matches("^[0-9-]+$")) {
            return assembler.assembleBasicResult(elasticsearchEngine.searchByIsbn(userQuery));
        }

        String cacheKey = keyGenerator.generateKey("basic", userQuery);
        SearchResponseDto cached = redisCacheService.get(cacheKey, SearchResponseDto.class);
        if (cached != null) return cached;

        String refinedQuery = queryPreprocessor.extractKeywords(userQuery);

        List<Float> embedding;
        try {
            embedding = embeddingClient.createEmbedding(refinedQuery);
        } catch (Exception e) {
            log.warn("⚠️ [Fallback-Basic] 임베딩 실패: {}", e.getMessage());
            embedding = Collections.emptyList();
        }

        List<Book> books = elasticsearchEngine.search(refinedQuery, embedding);
        SearchResponseDto result = assembler.assembleBasicResult(books);

//        redisCacheService.save(cacheKey, result, Duration.ofHours(1));
        return result;
    }
}