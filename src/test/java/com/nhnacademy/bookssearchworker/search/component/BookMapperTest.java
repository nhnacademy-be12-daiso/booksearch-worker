
package com.nhnacademy.bookssearchworker.search.component;

import com.nhnacademy.bookssearchworker.search.domain.Book;
import com.nhnacademy.bookssearchworker.search.dto.AiResultDto;
import com.nhnacademy.bookssearchworker.search.dto.BookResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class BookMapperTest {

    private final BookMapper mapper = new BookMapper();

    @Test
    @DisplayName("toDto: Book 필드를 DTO로 매핑하고 matchRate를 설정한다")
    void toDto_mapsFields() {
        Book book = Book.builder()
                .id("id1")
                .isbn("111")
                .title("T")
                .author("A")
                .publisher("P")
                .description("D")
                .imageUrl("img")
                .price(12345)
                .categories(List.of("c1", "c2"))
                .publisherId(10L)
                .categoryId(20L)
                .build();

        BookResponseDto dto = mapper.toDto(book, 77);

        assertThat(dto.getIsbn()).isEqualTo("111");
        assertThat(dto.getTitle()).isEqualTo("T");
        assertThat(dto.getMatchRate()).isEqualTo(77);
        assertThat(dto.getPrice()).isEqualTo(12345);
        assertThat(dto.getPublisherId()).isEqualTo(10L);
        assertThat(dto.getCategoryId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("applyAiEvaluation: ISBN이 일치하면 matchRate/aiAnswer를 덮어쓴다")
    void applyAiEvaluation_updatesMatchingIsbn() {
        BookResponseDto dto1 = BookResponseDto.builder().isbn("111").matchRate(0).price(1000).build();
        BookResponseDto dto2 = BookResponseDto.builder().isbn("222").matchRate(0).price(1000).build();

        mapper.applyAiEvaluation(List.of(dto1, dto2), Map.of(
                "111", new AiResultDto("reason1", 90)
        ));

        assertThat(dto1.getMatchRate()).isEqualTo(90);
        assertThat(dto1.getAiAnswer()).isEqualTo("reason1");

        // 매칭되지 않으면 aiAnswer는 null, matchRate는 기존 유지
        assertThat(dto2.getAiAnswer()).isNull();
        assertThat(dto2.getMatchRate()).isEqualTo(0);
    }
}
