package com.nhnacademy.bookssearchworker.search.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookJsonDto {
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private String publicationDate;
    private Integer price;
    private String description;
    private String category1;
    private String category2;
    private String category3;
    private String imageUrl;
}