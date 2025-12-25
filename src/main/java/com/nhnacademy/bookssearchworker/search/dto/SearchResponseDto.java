// java
package com.nhnacademy.bookssearchworker.search.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResponseDto {
    private List<BookResponseDto> bookList;

    public static SearchResponseDto empty() {
        return SearchResponseDto.builder()
                .bookList(Collections.emptyList())
                .build();
    }
}
