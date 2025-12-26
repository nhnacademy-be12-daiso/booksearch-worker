package com.nhnacademy.bookssearchworker.search.component.assembler;

import com.nhnacademy.bookssearchworker.search.component.BookMapper;
import com.nhnacademy.bookssearchworker.search.domain.Book;
import com.nhnacademy.bookssearchworker.search.dto.AiResultDto;
import com.nhnacademy.bookssearchworker.search.dto.BookResponseDto;
import com.nhnacademy.bookssearchworker.search.dto.BookWithScore;
import com.nhnacademy.bookssearchworker.search.dto.SearchResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SearchResultAssembler {

    private final BookMapper bookMapper;
    private static final int FINAL_RESULT_SIZE = 50;

    // 1. 일반 검색 결과 조립
    public SearchResponseDto assembleBasicResult(List<Book> books) {
        List<BookResponseDto> dtos = books.stream()
                .limit(FINAL_RESULT_SIZE)
                .map(b -> bookMapper.toDto(b, 50)) // 기본 점수 50
                .toList();
        return SearchResponseDto.builder().bookList(dtos).build();
    }

    // 2. AI 검색 결과 조립 (리랭킹 + AI 분석 병합)
    public SearchResponseDto assembleAiResult(List<BookWithScore> rankedBooks, Map<String, AiResultDto> aiAnalysis) {
        // 상위 50개 자르기
        List<Book> targetBooks = rankedBooks.stream()
                .limit(FINAL_RESULT_SIZE)
                .map(BookWithScore::book)
                .toList();

        // DTO 변환
        List<BookResponseDto> dtos = bookMapper.toDtoList(targetBooks, 0);

        // AI 결과 매핑 (Mapper 위임)
        bookMapper.applyAiEvaluation(dtos, aiAnalysis);

        // 최종 정렬 (점수 높은 순)
        dtos.sort(Comparator.comparingInt(BookResponseDto::getMatchRate).reversed());

        return SearchResponseDto.builder().bookList(dtos).build();
    }

    // 3. 리랭킹 점수 매핑 로직 (복잡한 스트림 로직 격리)
    public List<BookWithScore> applyRerankScores(List<Book> original, List<Map<String, Object>> scores, int limit) {
        int target = Math.min(original.size(), limit);
        List<BookWithScore> result = new ArrayList<>(original.size());

        // 상위 target권은 score 있으면 반영, 없으면 0점
        for (int i = 0; i < target; i++) {
            double score = 0.0;
            if (scores != null && i < scores.size()) {
                Object v = scores.get(i).get("score");
                if (v instanceof Number n) score = n.doubleValue();
            }
            result.add(new BookWithScore(original.get(i), score));
        }

        // 나머지는 0점 처리
        for (int i = target; i < original.size(); i++) {
            result.add(new BookWithScore(original.get(i), 0.0));
        }

        result.sort(Comparator.comparingDouble(BookWithScore::score).reversed());
        return result;
    }

}