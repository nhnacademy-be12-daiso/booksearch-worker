package com.nhnacademy.bookssearchworker.search.component.engine;

import com.nhnacademy.bookssearchworker.search.domain.Book;
import com.nhnacademy.bookssearchworker.search.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ElasticsearchEngine {

    private final BookRepository bookRepository;
    private static final int DEFAULT_FETCH_SIZE = 50;

    public List<Book> search(String query, List<Float> embedding) {
        List<Book> candidates = bookRepository.searchHybrid(query, embedding, DEFAULT_FETCH_SIZE);
        return candidates == null ? Collections.emptyList() : candidates;
    }

    public List<Book> searchByIsbn(String isbn) {
        return bookRepository.findByIsbn(isbn);
    }
}