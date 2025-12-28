
package com.nhnacademy.bookssearchworker.search.component.assembler;

import com.nhnacademy.bookssearchworker.search.component.BookMapper;
import com.nhnacademy.bookssearchworker.search.domain.Book;
import com.nhnacademy.bookssearchworker.search.dto.AiResultDto;
import com.nhnacademy.bookssearchworker.search.dto.BookResponseDto;
import com.nhnacademy.bookssearchworker.search.dto.BookWithScore;
import com.nhnacademy.bookssearchworker.search.dto.SearchResponseDto;
import com.nhnacademy.bookssearchworker.search.service.DiscountPolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@SpringJUnitConfig(classes = SearchResultAssemblerTest.Config.class)
class SearchResultAssemblerTest {

    @Configuration
    @Import(SearchResultAssembler.class)
    static class Config {}

    @Autowired
    SearchResultAssembler assembler;

    @MockitoBean
    BookMapper bookMapper;

    @MockitoBean
    DiscountPolicyService discountPolicyService;

    private static Book book(String isbn, String title) {
        return Book.builder().isbn(isbn).title(title).description("d").price(10000).build();
    }

    @Test
    @DisplayName("assembleBasicResult: Book -> DTO 변환 후 할인 적용을 호출한다")
    void assembleBasicResult_mapsAndAppliesDiscounts() {
        Book b1 = book("111", "A");
        Book b2 = book("222", "B");

        BookResponseDto d1 = BookResponseDto.builder().isbn("111").matchRate(50).price(10000).build();
        BookResponseDto d2 = BookResponseDto.builder().isbn("222").matchRate(50).price(10000).build();

        given(bookMapper.toDto(b1, 50)).willReturn(d1);
        given(bookMapper.toDto(b2, 50)).willReturn(d2);

        SearchResponseDto res = assembler.assembleBasicResult(List.of(b1, b2));

        assertThat(res.getBookList()).containsExactly(d1, d2);
        then(discountPolicyService).should(times(1)).applyDiscounts(List.of(d1, d2));
    }

    @Test
    @DisplayName("assembleAiResult: DTO 리스트를 matchRate 내림차순으로 정렬한다")
    void assembleAiResult_sortsByMatchRateDesc() {
        Book b1 = book("111", "A");
        Book b2 = book("222", "B");

        List<BookWithScore> ranked = List.of(
                new BookWithScore(b1, 0.2),
                new BookWithScore(b2, 0.9)
        );

        BookResponseDto d1 = BookResponseDto.builder().isbn("111").matchRate(10).price(10000).build();
        BookResponseDto d2 = BookResponseDto.builder().isbn("222").matchRate(90).price(10000).build();

        given(bookMapper.toDtoList(List.of(b1, b2), 0))
                .willReturn(new ArrayList<>(List.of(d1, d2)));

        SearchResponseDto res = assembler.assembleAiResult(
                ranked,
                Map.of(
                        "111", new AiResultDto("r1", 10),
                        "222", new AiResultDto("r2", 90)
                )
        );

        assertThat(res.getBookList()).containsExactly(d2, d1);
        then(bookMapper).should().applyAiEvaluation(anyList(), anyMap());
        then(discountPolicyService).should().applyDiscounts(anyList());
    }


    @Test
    @DisplayName("applyRerankScores: 상위 limit에만 score를 반영하고 score 기준으로 정렬한다")
    void applyRerankScores_appliesOnlyWithinLimit_andSortsByScore() {
        Book a = book("111", "A");
        Book b = book("222", "B");
        Book c = book("333", "C");

        List<Map<String, Object>> scores = List.of(
                Map.of("score", 0.1),
                Map.of("score", 0.9)
        );

        List<BookWithScore> result = assembler.applyRerankScores(List.of(a, b, c), scores, 2);

        // b(0.9) -> a(0.1) -> c(0.0)
        assertThat(result).extracting(BookWithScore::book).containsExactly(b, a, c);
        assertThat(result).extracting(BookWithScore::score).containsExactly(0.9, 0.1, 0.0);
    }
}
