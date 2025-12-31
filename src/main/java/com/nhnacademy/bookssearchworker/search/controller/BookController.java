package com.nhnacademy.bookssearchworker.search.controller;

import com.nhnacademy.bookssearchworker.search.dto.SearchResponseDto;
import com.nhnacademy.bookssearchworker.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController("BookSearchController")
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class BookController {
    private final SearchService searchService;

    // 기본 도서 검색
    @GetMapping("/basic")
    @Operation(summary = "기본 도서 검색", description = "키워드를 기반으로 도서를 검색합니다.")
    public SearchResponseDto search(
            @Parameter(description="검색어", example="해리포터")
            @RequestParam String query
    ) {
        return searchService.basicSearch(query);
    }

    // AI 도서 검색
    @GetMapping("/ai")
    @Operation(summary = "AI 도서 검색", description = "AI 기반 검색(분석/리랭킹 포함)을 수행합니다.")
    public SearchResponseDto aiSearch(
            @Parameter(description = "검색어", example = "자바 스프링")
            @RequestParam String query
    ) {
        return searchService.aiSearch(query);
    }
}
