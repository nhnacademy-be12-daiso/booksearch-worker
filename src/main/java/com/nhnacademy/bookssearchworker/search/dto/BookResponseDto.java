package com.nhnacademy.bookssearchworker.search.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookResponseDto {
    private String id;
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private int price;
    private String description;

    @Builder.Default
    private List<String> categories = new ArrayList<>();

    private String imageUrl;
    @Setter
    private String AiAnswer;
    @Setter
    private Integer matchRate;

    @Setter
    private Integer discountedPrice;

    @Setter
    private Long publisherId;

    @Setter
    private Long categoryId;
}