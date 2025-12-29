package com.nhnacademy.bookssearchworker.worker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = WorkerConfig.class)
class WorkerConfigTest {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    WebClient webClient;

    @Test
    @DisplayName("WorkerConfig: ObjectMapper는 JavaTimeModule 포함 + 날짜 타임스탬프 비활성화")
    void objectMapper_hasJavaTimeModule_andNoTimestamps() throws Exception {
        assertNotNull(objectMapper, "ObjectMapper bean should exist");

        String json = objectMapper.writeValueAsString(LocalDate.of(2025, 1, 2));
        assertEquals("\"2025-01-02\"", json, "WRITE_DATES_AS_TIMESTAMPS=false 이므로 ISO-8601 문자열이어야 함");
    }

    @Test
    @DisplayName("WorkerConfig: WebClient bean 생성이 가능하다(외부 호출 없음)")
    void webClient_isCreated() {
        assertNotNull(webClient, "WebClient bean should exist");
    }
}
