
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@SpringJUnitConfig(classes = SearchServiceTest.Config.class)
class SearchServiceTest {

    @Configuration
    @Import(SearchService.class)
    static class Config {}

    @Autowired
    SearchService searchService;

    @MockitoBean
    CacheKeyGenerator keyGenerator;

    @MockitoBean
    QueryPreprocessor queryPreprocessor;

    @MockitoBean
    EmbeddingClient embeddingClient;

    @MockitoBean
    ElasticsearchEngine elasticsearchEngine;

    @MockitoBean
    RerankingClient rerankingClient;

    @MockitoBean
    LlmAnalysisClient llmClient;

    @MockitoBean
    SearchResultAssembler assembler;

    @MockitoBean
    RedisCacheService redisCacheService;

    private static Book book(String isbn, String title) {
        return Book.builder()
                .id("id-" + isbn)
                .isbn(isbn)
                .title(title)
                .author("author")
                .publisher("pub")
                .description("desc")
                .price(20000)
                .build();
    }

    @Nested
    @DisplayName("aiSearch()")
    class AiSearch {

        @Test
        @DisplayName("캐시 히트면 그대로 반환하고, 하위 의존성 호출은 하지 않는다")
        void cacheHit_returnsCached_andSkipsDownstream() {
            String userQuery = "자바 추천해줘";
            String cacheKey = "ai:java";

            SearchResponseDto cached = SearchResponseDto.builder()
                    .bookList(Collections.emptyList())
                    .build();

            given(keyGenerator.generateKey("ai", userQuery)).willReturn(cacheKey);
            given(redisCacheService.get(cacheKey, SearchResponseDto.class)).willReturn(cached);

            SearchResponseDto result = searchService.aiSearch(userQuery);

            assertThat(result).isSameAs(cached);

            then(queryPreprocessor).shouldHaveNoInteractions();
            then(embeddingClient).shouldHaveNoInteractions();
            then(elasticsearchEngine).shouldHaveNoInteractions();
            then(rerankingClient).shouldHaveNoInteractions();
            then(llmClient).shouldHaveNoInteractions();
            then(assembler).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("임베딩 생성 실패 시 벡터 검색을 제외하고 계속 진행하며, AI 분석이 있으면 캐시 저장한다")
        void embeddingFailure_fallbackToKeywordOnly_andCachesOnlyWhenAiAnalysisExists() {
            String userQuery = "스프링 부트 책";
            String refined = "스프링 부트";
            String cacheKey = "ai:spring";

            List<Book> candidates = List.of(book("111", "A"), book("222", "B"));

            List<Map<String, Object>> scores = List.of(
                    Map.of("score", 0.9),
                    Map.of("score", 0.1)
            );

            List<BookWithScore> ranked = List.of(
                    new BookWithScore(candidates.get(0), 0.9),
                    new BookWithScore(candidates.get(1), 0.1)
            );

            Map<String, AiResultDto> ai = Map.of(
                    "111", new AiResultDto("추천 이유", 95)
            );

            SearchResponseDto assembled = SearchResponseDto.builder().bookList(Collections.emptyList()).build();

            given(keyGenerator.generateKey("ai", userQuery)).willReturn(cacheKey);
            given(redisCacheService.get(cacheKey, SearchResponseDto.class)).willReturn(null);

            given(queryPreprocessor.extractKeywords(userQuery)).willReturn(refined);

            given(embeddingClient.createEmbedding(refined)).willThrow(new RuntimeException("embed down"));

            given(elasticsearchEngine.search(refined, Collections.emptyList())).willReturn(candidates);

            given(rerankingClient.rerank(eq(refined), anyList())).willReturn(scores);
            given(assembler.applyRerankScores(candidates, scores, 10)).willReturn(ranked);

            given(llmClient.analyzeBooks(userQuery, List.of(candidates.get(0), candidates.get(1)))).willReturn(ai);

            given(assembler.assembleAiResult(ranked, ai)).willReturn(assembled);

            SearchResponseDto result = searchService.aiSearch(userQuery);

            assertThat(result).isSameAs(assembled);

            then(elasticsearchEngine).should(times(1)).search(refined, Collections.emptyList());
            then(redisCacheService).should(times(1)).save(eq(cacheKey), eq(assembled), eq(Duration.ofHours(12)));
        }

        @Test
        @DisplayName("리랭킹 실패 시 기본 점수로 진행하고, AI 분석이 비어있으면 캐싱하지 않는다")
        void rerankFailure_usesDefaultScore_andSkipsCachingWhenAiEmpty() {
            String userQuery = "자료구조";
            String refined = "자료구조";
            String cacheKey = "ai:ds";

            List<Book> candidates = List.of(book("111", "A"), book("222", "B"));

            SearchResponseDto assembled = SearchResponseDto.builder().bookList(Collections.emptyList()).build();

            given(keyGenerator.generateKey("ai", userQuery)).willReturn(cacheKey);
            given(redisCacheService.get(cacheKey, SearchResponseDto.class)).willReturn(null);

            given(queryPreprocessor.extractKeywords(userQuery)).willReturn(refined);
            given(embeddingClient.createEmbedding(refined)).willReturn(List.of(0.1f, 0.2f));
            given(elasticsearchEngine.search(refined, List.of(0.1f, 0.2f))).willReturn(candidates);

            given(rerankingClient.rerank(eq(refined), anyList())).willThrow(new RuntimeException("rerank down"));

            // 리랭킹 실패 경로에서는 applyRerankScores가 호출되면 안 됨
            // LLM은 상위 5권(여기서는 2권) 대상으로 호출됨
            given(llmClient.analyzeBooks(eq(userQuery), anyList())).willReturn(Collections.emptyMap());

            // assembleAiResult는 기본 점수가 들어간 BookWithScore 목록으로 호출됨
            given(assembler.assembleAiResult(anyList(), eq(Collections.emptyMap()))).willReturn(assembled);

            SearchResponseDto result = searchService.aiSearch(userQuery);

            assertThat(result).isSameAs(assembled);

            then(assembler).should(never()).applyRerankScores(anyList(), anyList(), anyInt());
            then(redisCacheService).should(never()).save(anyString(), any(), any());
        }

        @Test
        @DisplayName("Elasticsearch가 실패하면 예외를 그대로 전파한다(검색 불가)")
        void elasticsearchFailure_propagatesException() {
            String userQuery = "테스트";
            String refined = "테스트";
            String cacheKey = "ai:test";

            given(keyGenerator.generateKey("ai", userQuery)).willReturn(cacheKey);
            given(redisCacheService.get(cacheKey, SearchResponseDto.class)).willReturn(null);
            given(queryPreprocessor.extractKeywords(userQuery)).willReturn(refined);
            given(embeddingClient.createEmbedding(refined)).willReturn(Collections.emptyList());

            given(elasticsearchEngine.search(refined, Collections.emptyList()))
                    .willThrow(new IllegalStateException("ES down"));

            assertThatThrownBy(() -> searchService.aiSearch(userQuery))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ES down");

            then(rerankingClient).shouldHaveNoInteractions();
            then(llmClient).shouldHaveNoInteractions();
            then(assembler).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("검색 결과가 비어있으면 empty()를 반환한다")
        void emptyCandidates_returnsEmptyResponse() {
            String userQuery = "없는책";
            String refined = "없는책";
            String cacheKey = "ai:none";

            given(keyGenerator.generateKey("ai", userQuery)).willReturn(cacheKey);
            given(redisCacheService.get(cacheKey, SearchResponseDto.class)).willReturn(null);
            given(queryPreprocessor.extractKeywords(userQuery)).willReturn(refined);
            given(embeddingClient.createEmbedding(refined)).willReturn(Collections.emptyList());
            given(elasticsearchEngine.search(refined, Collections.emptyList())).willReturn(Collections.emptyList());

            SearchResponseDto result = searchService.aiSearch(userQuery);

            assertThat(result.getBookList()).isEmpty();

            then(rerankingClient).shouldHaveNoInteractions();
            then(llmClient).shouldHaveNoInteractions();
            then(assembler).shouldHaveNoInteractions();
            then(redisCacheService).should(never()).save(anyString(), any(), any());
        }

        @Test
        @DisplayName("Gemini 분석 실패 시 AI 답변 없이 결과만 반환하고 캐싱하지 않는다")
        void llmFailure_returnsResultWithoutCaching() {
            String userQuery = "DB";
            String refined = "DB";
            String cacheKey = "ai:db";

            List<Book> candidates = List.of(book("111", "A"));

            List<Map<String, Object>> scores = List.of(Map.of("score", 0.7));
            List<BookWithScore> ranked = List.of(new BookWithScore(candidates.get(0), 0.7));
            SearchResponseDto assembled = SearchResponseDto.builder().bookList(Collections.emptyList()).build();

            given(keyGenerator.generateKey("ai", userQuery)).willReturn(cacheKey);
            given(redisCacheService.get(cacheKey, SearchResponseDto.class)).willReturn(null);

            given(queryPreprocessor.extractKeywords(userQuery)).willReturn(refined);
            given(embeddingClient.createEmbedding(refined)).willReturn(Collections.emptyList());
            given(elasticsearchEngine.search(refined, Collections.emptyList())).willReturn(candidates);

            given(rerankingClient.rerank(eq(refined), anyList())).willReturn(scores);
            given(assembler.applyRerankScores(candidates, scores, 10)).willReturn(ranked);

            given(llmClient.analyzeBooks(eq(userQuery), anyList())).willThrow(new RuntimeException("gemini down"));
            given(assembler.assembleAiResult(ranked, Collections.emptyMap())).willReturn(assembled);

            SearchResponseDto result = searchService.aiSearch(userQuery);

            assertThat(result).isSameAs(assembled);
            then(redisCacheService).should(never()).save(anyString(), any(), any());
        }
    }

    @Nested
    @DisplayName("basicSearch()")
    class BasicSearch {

        @Test
        @DisplayName("query가 숫자/하이픈만이면 ISBN 전용 검색을 수행한다")
        void isbnOnlyQuery_routesToIsbnSearch() {
            String isbnQuery = "978-1234-5678";
            List<Book> byIsbn = List.of(book(isbnQuery, "ISBN Book"));
            SearchResponseDto assembled = SearchResponseDto.builder().bookList(Collections.emptyList()).build();

            given(elasticsearchEngine.searchByIsbn(isbnQuery)).willReturn(byIsbn);
            given(assembler.assembleBasicResult(byIsbn)).willReturn(assembled);

            SearchResponseDto result = searchService.basicSearch(isbnQuery);

            assertThat(result).isSameAs(assembled);

            then(queryPreprocessor).shouldHaveNoInteractions();
            then(embeddingClient).shouldHaveNoInteractions();
            then(elasticsearchEngine).should(never()).search(anyString(), anyList());
        }

        @Test
        @DisplayName("임베딩 실패 시 벡터 검색 제외하고 키워드 검색만 수행한다")
        void embeddingFailure_fallbackToKeywordOnly() {
            String userQuery = "c언어 추천";
            String refined = "C언어";

            List<Book> books = List.of(book("111", "C"));

            SearchResponseDto assembled = SearchResponseDto.builder().bookList(Collections.emptyList()).build();

            given(queryPreprocessor.extractKeywords(userQuery)).willReturn(refined);
            given(embeddingClient.createEmbedding(refined)).willThrow(new RuntimeException("embed down"));
            given(elasticsearchEngine.search(refined, Collections.emptyList())).willReturn(books);
            given(assembler.assembleBasicResult(books)).willReturn(assembled);

            SearchResponseDto result = searchService.basicSearch(userQuery);

            assertThat(result).isSameAs(assembled);
            then(elasticsearchEngine).should(times(1)).search(refined, Collections.emptyList());
        }

        @Test
        @DisplayName("Elasticsearch 검색 실패 시 예외를 그대로 전파한다")
        void elasticsearchFailure_propagates() {
            String userQuery = "스프링";
            String refined = "스프링";

            given(queryPreprocessor.extractKeywords(userQuery)).willReturn(refined);
            given(embeddingClient.createEmbedding(refined)).willReturn(Collections.emptyList());
            given(elasticsearchEngine.search(refined, Collections.emptyList()))
                    .willThrow(new IllegalStateException("ES down"));

            assertThatThrownBy(() -> searchService.basicSearch(userQuery))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ES down");
        }
    }
}
