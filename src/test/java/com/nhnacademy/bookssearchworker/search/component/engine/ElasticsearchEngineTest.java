
package com.nhnacademy.bookssearchworker.search.component.engine;

import com.nhnacademy.bookssearchworker.search.domain.Book;
import com.nhnacademy.bookssearchworker.search.repository.BookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@SpringJUnitConfig(classes = ElasticsearchEngineTest.Config.class)
class ElasticsearchEngineTest {

    @Configuration
    @Import(ElasticsearchEngine.class)
    static class Config {}

    @Autowired
    ElasticsearchEngine engine;

    @MockitoBean
    BookRepository bookRepository;

    @Test
    @DisplayName("search: repository가 null 반환하면 빈 리스트로 처리한다")
    void search_nullFromRepo_returnsEmpty() {
        given(bookRepository.searchHybrid(eq("q"), anyList(), anyInt())).willReturn(null);

        List<Book> result = engine.search("q", Collections.emptyList());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("search: repository 결과가 있으면 그대로 반환한다")
    void search_returnsRepoList() {
        Book b = Book.builder().isbn("111").title("A").build();
        given(bookRepository.searchHybrid(eq("q"), anyList(), anyInt())).willReturn(List.of(b));

        List<Book> result = engine.search("q", List.of(0.1f));

        assertThat(result).containsExactly(b);
    }

    @Test
    @DisplayName("searchByIsbn: repository.findByIsbn을 위임한다")
    void searchByIsbn_delegates() {
        engine.searchByIsbn("isbn-1");
        then(bookRepository).should().findByIsbn("isbn-1");
    }
}
