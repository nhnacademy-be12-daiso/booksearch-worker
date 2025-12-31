package com.nhnacademy.bookssearchworker.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Books Search API",
                version = "v1",
                description = "도서 검색 API 문서"
        )
)
public class OpenApiConfig {}
