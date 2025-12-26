// java
package com.nhnacademy.bookssearchworker.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponseDto {
    private List<BookResponseDto> bookList;

    public static SearchResponseDto empty() {
        return SearchResponseDto.builder()
                .bookList(Collections.emptyList())
                .build();
    }
}
