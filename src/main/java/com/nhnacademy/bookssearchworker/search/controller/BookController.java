package com.nhnacademy.bookssearchworker.search.controller;

import com.nhnacademy.bookssearchworker.search.dto.SearchResponseDto;
import com.nhnacademy.bookssearchworker.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController("BookSearchController")
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class BookController {
    private final SearchService searchService;

    // 기본 도서 검색
    @GetMapping("/basic")
    public SearchResponseDto search(@RequestParam String query) {
        return searchService.basicSearch(query);
    }

    // AI 도서 검색
    @GetMapping("/ai")
    public SearchResponseDto aiSearch(@RequestParam String query) {
        return searchService.aiSearch(query);
    }
}
