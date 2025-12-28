
package com.nhnacademy.bookssearchworker.search.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.bookssearchworker.search.dto.BookResponseDto;
import com.nhnacademy.bookssearchworker.search.dto.SearchResponseDto;
import com.nhnacademy.bookssearchworker.search.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(classes = BookControllerTest.Config.class)
class BookControllerTest {

    @Configuration
    @Import(BookController.class)
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    @Autowired
    BookController controller;

    @MockitoBean
    SearchService searchService;

    @Autowired
    ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/search/basic - basicSearch를 호출하고 응답을 JSON으로 반환한다")
    void basicEndpoint_returnsJson() throws Exception {
        SearchResponseDto dto = SearchResponseDto.builder().bookList(List.of(
                BookResponseDto.builder().isbn("111").title("T").price(1000).matchRate(50).build()
        )).build();

        given(searchService.basicSearch("q")).willReturn(dto);

        mockMvc.perform(get("/api/search/basic").param("query", "q"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookList[0].isbn").value("111"))
                .andExpect(jsonPath("$.bookList[0].title").value("T"))
                .andExpect(jsonPath("$.bookList[0].matchRate").value(50));
    }

    @Test
    @DisplayName("GET /api/search/ai - aiSearch를 호출하고 응답을 JSON으로 반환한다")
    void aiEndpoint_returnsJson() throws Exception {
        SearchResponseDto dto = SearchResponseDto.builder().bookList(List.of(
                BookResponseDto.builder().isbn("222").title("AI").price(2000).matchRate(80).build()
        )).build();

        given(searchService.aiSearch("q")).willReturn(dto);

        mockMvc.perform(get("/api/search/ai").param("query", "q"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookList[0].isbn").value("222"))
                .andExpect(jsonPath("$.bookList[0].title").value("AI"))
                .andExpect(jsonPath("$.bookList[0].matchRate").value(80));
    }
}
