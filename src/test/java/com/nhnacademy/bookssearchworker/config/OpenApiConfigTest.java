package com.nhnacademy.bookssearchworker.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockingDetails;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = OpenApiConfig.class)
class OpenApiConfigTest {

    @MockitoBean
    private List<String> mockedList;

    @Test
    void configurationAnnotationPresent() {
        assertTrue(OpenApiConfig.class.isAnnotationPresent(Configuration.class),
                "OpenApiConfig는 @Configuration 애노테이션이 있어야 합니다.");
    }

    @Test
    void openApiDefinitionPresentAndInfoValuesMatch() {
        OpenAPIDefinition def = OpenApiConfig.class.getAnnotation(OpenAPIDefinition.class);
        assertNotNull(def, "`@OpenAPIDefinition`이 존재해야 합니다.");

        Info info = def.info();
        assertNotNull(info, "info 속성은 null이 아니어야 합니다.");
        assertEquals("Books Search API", info.title(), "title 값이 예상과 달라요.");
        assertEquals("v1", info.version(), "version 값이 예상과 달라요.");
        assertEquals("도서 검색 API 문서", info.description(), "description 값이 예상과 달라요.");
    }

    @Test
    void mockitoBeanProvidesMockObject() {
        assertNotNull(mockedList, "`@MockitoBean`로 주입된 필드가 null이면 안됩니다.");
        MockingDetails details = Mockito.mockingDetails(mockedList);
        assertTrue(details.isMock(), "주입된 객체는 Mockito mock이어야 합니다.");
    }
}
