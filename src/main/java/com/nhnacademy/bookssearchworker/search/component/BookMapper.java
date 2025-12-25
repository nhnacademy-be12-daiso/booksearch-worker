package com.nhnacademy.bookssearchworker.search.component;


import com.nhnacademy.bookssearchworker.search.domain.Book;
import com.nhnacademy.bookssearchworker.search.dto.AiResultDto;
import com.nhnacademy.bookssearchworker.search.dto.BookResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BookMapper {

    // 단일 변환
    public BookResponseDto toDto(Book book, int matchRate) {
        return BookResponseDto.builder()
                .id(book.getId())
                .isbn(book.getIsbn())
                .title(book.getTitle())
                .author(book.getAuthor())
                .publisher(book.getPublisher())
                .description(book.getDescription())
                .imageUrl(book.getImageUrl())
                .price(book.getPrice())
                .categories(book.getCategories())
                .matchRate(matchRate)
                .build();
    }

    // 리스트 변환 (기본 점수 적용)
    public List<BookResponseDto> toDtoList(List<Book> books, int defaultScore) {
        return books.stream()
                .map(b -> toDto(b, defaultScore))
                .collect(Collectors.toList());
    }

    // AI 결과 병합
    public void applyAiEvaluation(List<BookResponseDto> dtos, Map<String, AiResultDto> aiResults) {
        for (BookResponseDto dto : dtos) {
            // AI 결과가 정상적으로 존재하는 경우
            if (aiResults.containsKey(dto.getIsbn())) {
                AiResultDto res = aiResults.get(dto.getIsbn());

                // 점수 업데이트
                dto.setMatchRate(res.matchRate());

                // 멘트 설정
                dto.setAiAnswer(res.reason());
            }
            else {
                log.warn("AI 평가 결과에 도서 정보가 없습니다. ISBN: {}", dto.getIsbn());
                dto.setAiAnswer(null);
                // matchRate는 리랭킹 점수(또는 0점)를 유지
            }
        }
    }
}