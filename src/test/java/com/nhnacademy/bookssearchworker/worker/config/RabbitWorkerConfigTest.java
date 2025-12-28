package com.nhnacademy.bookssearchworker.worker.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = RabbitWorkerConfig.class)
class RabbitWorkerConfigTest {

    @MockitoBean
    ConnectionFactory connectionFactory;

    @Autowired
    MessageConverter messageConverter;

    @Autowired
    RabbitTemplate rabbitTemplate;
    @Test
    @DisplayName("RabbitWorkerConfig: MessageConverter는 Jackson2JsonMessageConverter로 생성된다")
    void messageConverter_isJackson() {
        assertNotNull(messageConverter, "messageConverter bean should exist");
        assertTrue(messageConverter instanceof Jackson2JsonMessageConverter,
                "Jackson2JsonMessageConverter 여야 함 (메시지 역직렬화 실패 방지)");
    }

    @Test
    @DisplayName("RabbitWorkerConfig: RabbitTemplate는 Converter가 주입된다(외부 브로커 연결 없음)")
    void rabbitTemplate_hasConverter() {
        assertNotNull(rabbitTemplate, "rabbitTemplate bean should exist");
        assertSame(messageConverter, rabbitTemplate.getMessageConverter(),
                "RabbitTemplate는 messageConverter를 사용해야 함");
    }
}
