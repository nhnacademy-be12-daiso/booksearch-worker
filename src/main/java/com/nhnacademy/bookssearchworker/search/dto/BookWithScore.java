package com.nhnacademy.bookssearchworker.search.dto;


import com.nhnacademy.bookssearchworker.search.domain.Book;

public record BookWithScore(Book book, double score) {}
