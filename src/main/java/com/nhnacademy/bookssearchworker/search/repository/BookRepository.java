package com.nhnacademy.bookssearchworker.search.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.nhnacademy.bookssearchworker.search.domain.Book;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Repository("BookSearchRepository")
@RequiredArgsConstructor
public class BookRepository {

    private final ElasticsearchClient esClient;
    private static final String INDEX_NAME = "books";

    /**
     * 1. ISBN 단건 조회 (정확도 100%)
     */
    public List<Book> findByIsbn(String isbn) {
        try {
            SearchResponse<Book> response = esClient.search(s -> s
                    .index(INDEX_NAME)
                    .query(q -> q.term(t -> t.field("isbn").value(isbn))), Book.class);
            return extractHits(response);
        } catch (IOException e) {
            log.error(" [Repository] ISBN 조회 실패: isbn={}", isbn, e);
            return Collections.emptyList();
        }
    }

    /**
     * 2. 하이브리드 검색 (Vector + Keyword)
     * - 임베딩 서버가 죽어서 vector가 비어있으면 -> KNN 검색을 자동으로 생략 (Keyword만 검색)
     */
    public List<Book> searchHybrid(String query, List<Float> vector, int size) {
        try {
            // 벡터 유효성 검사
            boolean useVector = (vector != null && !vector.isEmpty());

            SearchResponse<Book> response = esClient.search(s -> {
                s.size(size);
                s.index(INDEX_NAME);

                // [Smart Logic] 벡터가 있을 때만 KNN 절을 추가함
                if (useVector) {
                    s.knn(k -> k
                            .field("embedding")
                            .queryVector(vector)
                            .k(size)
                            .numCandidates(100)
                            .boost(3.0f)
                    );
                }

                // 키워드 검색 (항상 수행)
                s.query(q -> q.bool(b -> b
                        .should(m -> m.multiMatch(mm -> mm
                                .query(query)
                                .fields(
                                        "isbn^10.0",        // 1. ISBN (가장 중요)
                                        "title^5.0",        // 2. 제목
                                        "author^4.0",       // 3. 저자
                                        "categories^3.0",   // 4. 카테고리
                                        "publisher^2.0",    // 5. 출판사
                                        "description^1.0",  // 6. 설명
                                        "reviews^0.5"       // 7. 리뷰
                                )
                                .analyzer("korean_analyzer")
                                .minimumShouldMatch("2<75%")
                        ))
                        .should(t -> t.term(tm -> tm
                                .field("isbn.keyword")
                                .value(query)
                                .boost(15.0f)
                        ))
                ));

                // 성능 최적화: 임베딩 필드는 결과 JSON에서 제외
                s.source(src -> src.filter(f -> f.excludes("embedding")));

                return s;
            }, Book.class);

            return extractHits(response);

        } catch (IOException e) {
            log.error(" [Repository] 하이브리드 검색 실패: query={}", query, e);
            return Collections.emptyList();
        }
    }

    // Helper: SearchResponse -> List<Book> 변환
    private List<Book> extractHits(SearchResponse<Book> response) {
        return response.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
    }
}