
package com.nhnacademy.bookssearchworker.search.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QueryPreprocessorTest {

    private final QueryPreprocessor preprocessor = new QueryPreprocessor();

    @Test
    @DisplayName("불용어를 제거하고 공백을 정리한다")
    void removesStopPatternsAndNormalizesSpaces() {
        String input = "도움이 될만한 스프링 부트 책 추천해줘";
        String result = preprocessor.extractKeywords(input);

        // "도움이 될만한", "책", "추천해줘" 제거 후 "스프링 부트"만 남는 형태를 기대
        assertThat(result).contains("스프링").contains("부트");
        assertThat(result).doesNotContain("도움").doesNotContain("추천");
        assertThat(result).doesNotContain("  ");
    }

    @Test
    @DisplayName("기술 키워드 표기를 보정한다(c언어/C++/sql/jpa/api 등)")
    void fixesCapitalizationForTechKeywords() {
        assertThat(preprocessor.extractKeywords("c언어")).isEqualTo("C언어");
        assertThat(preprocessor.extractKeywords("c++")).isEqualTo("C++");
        assertThat(preprocessor.extractKeywords("sql")).isEqualTo("SQL");
        assertThat(preprocessor.extractKeywords("msa")).isEqualTo("MSA");
        assertThat(preprocessor.extractKeywords("jpa")).isEqualTo("JPA");
        assertThat(preprocessor.extractKeywords("api")).isEqualTo("API");
        assertThat(preprocessor.extractKeywords("c#")).isEqualTo("C#");
    }

    @Test
    @DisplayName("특수문자는 제거하되 C++ / C# 기호는 유지한다")
    void removesSpecialCharsButKeepsPlusAndSharp() {
        String input = "C++!! C#@@ 스프링??";
        String result = preprocessor.extractKeywords(input);

        assertThat(result).contains("C++").contains("C#").contains("스프링");
        assertThat(result).doesNotContain("!").doesNotContain("@").doesNotContain("?");
    }
}
