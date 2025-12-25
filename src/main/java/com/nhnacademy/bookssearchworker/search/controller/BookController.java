package com.nhnacademy.bookssearchworker.search.controller;

import com.nhnacademy.bookssearchworker.search.domain.Book;
import com.nhnacademy.bookssearchworker.search.dto.SearchResponseDto;
import com.nhnacademy.bookssearchworker.search.service.BookManagementService;
import com.nhnacademy.bookssearchworker.search.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController("BookSearchController")
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class BookController {
    private final BookManagementService bookManagementService;
    private final SearchService searchService;

    // 책 한 권 정보를 받아서 수정/등록하는 API
    @PutMapping("/update")
    public ResponseEntity<String> updateBook(@Valid @RequestBody Book bookDto) {
        BookManagementService.OperationResult result = bookManagementService.upsertBook(bookDto);
        if (result.success()) {
            return ResponseEntity.ok(result.message());
        }
        return ResponseEntity.status(502).body(result.message());
    }

    @DeleteMapping("/delete/{isbn}")
    public ResponseEntity<String> deleteBook(@PathVariable String isbn) {
        BookManagementService.OperationResult result = bookManagementService.deleteBook(isbn);
        if (result.success()) {
            return ResponseEntity.ok(result.message());
        }
        return ResponseEntity.status(502).body(result.message());
    }

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
