/*
 * NOTE:
 * - This test suite intentionally avoids any real external calls (RabbitMQ / ES / AI).
 * - All external dependencies are replaced using @MockitoBean (Spring Boot Mockito override).
 * - If your project uses older Spring Boot (without @MockitoBean), replace @MockitoBean with @MockBean.
 */
package com.nhnacademy.bookssearchworker.worker.rabbit;

import com.nhnacademy.bookssearchworker.worker.exception.WorkerProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = RabbitRetryPublisherTest.TestConfig.class)
@TestPropertySource(properties = {
        "rabbitmq.exchange.main=ex.main",
        "rabbitmq.exchange.retry=ex.retry",
        "rabbitmq.exchange.dlx=ex.dlx"
})
class RabbitRetryPublisherTest {

    @Import(RabbitRetryPublisher.class)
    static class TestConfig { }

    @MockitoBean
    RabbitTemplate rabbitTemplate;

    @Autowired
    RabbitRetryPublisher publisher;

    @Test
    @DisplayName("getRetryCount(): null/없음/타입별 parsing 동작이 명확하다")
    void getRetryCount_parsing() {
        assertEquals(0, publisher.getRetryCount(null), "props null이면 0");

        MessageProperties p0 = new MessageProperties();
        assertEquals(0, publisher.getRetryCount(p0), "header 없으면 0");

        MessageProperties p1 = new MessageProperties();
        p1.getHeaders().put(RabbitRetryPublisher.HDR_RETRY_COUNT, 2);
        assertEquals(2, publisher.getRetryCount(p1), "Integer header");

        MessageProperties p2 = new MessageProperties();
        p2.getHeaders().put(RabbitRetryPublisher.HDR_RETRY_COUNT, 3L);
        assertEquals(3, publisher.getRetryCount(p2), "Long header");

        MessageProperties p3 = new MessageProperties();
        p3.getHeaders().put(RabbitRetryPublisher.HDR_RETRY_COUNT, "1");
        assertEquals(1, publisher.getRetryCount(p3), "String numeric header");

        MessageProperties p4 = new MessageProperties();
        p4.getHeaders().put(RabbitRetryPublisher.HDR_RETRY_COUNT, "not-a-number");
        assertEquals(0, publisher.getRetryCount(p4), "String non-numeric -> 0");
    }

    @Test
    @DisplayName("toRetry(): retry exchange로 메시지 발행 + x-retry-count 갱신")
    void toRetry_setsHeaderAndSends() {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.getHeaders().put("h", "v");
        Message original = new Message("body".getBytes(StandardCharsets.UTF_8), props);

        publisher.toRetry(original, "rk.retry", 2);

        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("ex.retry"), eq("rk.retry"), cap.capture());

        Message sent = cap.getValue();
        assertAll(
                () -> assertArrayEquals(original.getBody(), sent.getBody(), "body는 동일해야 함"),
                () -> assertEquals(2, sent.getMessageProperties().getHeaders().get(RabbitRetryPublisher.HDR_RETRY_COUNT), "retry header 갱신"),
                () -> assertEquals("v", sent.getMessageProperties().getHeaders().get("h"), "기존 header 유지"),
                () -> assertEquals(MessageProperties.CONTENT_TYPE_JSON, sent.getMessageProperties().getContentType(), "메타 정보 복사")
        );
    }

    @Test
    @DisplayName("toDlq(): dlx exchange로 메시지 발행 + error headers 포함 + 메시지 길이 제한")
    void toDlq_setsErrorHeadersAndTruncates() {
        MessageProperties props = new MessageProperties();
        Message original = new Message("x".getBytes(StandardCharsets.UTF_8), props);

        String tooLong = "a".repeat(2500);
        WorkerProcessingException ex = new WorkerProcessingException(WorkerProcessingException.ErrorCode.AI_API_ERROR, tooLong);

        publisher.toDlq(original, "rk.fail", ex);

        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("ex.dlx"), eq("rk.fail"), cap.capture());

        MessageProperties sentProps = cap.getValue().getMessageProperties();
        assertAll(
                () -> assertEquals("AI_API_ERROR", sentProps.getHeaders().get(RabbitRetryPublisher.HDR_ERROR_CODE), "error code header"),
                () -> {
                    Object msg = sentProps.getHeaders().get(RabbitRetryPublisher.HDR_ERROR_MESSAGE);
                    assertNotNull(msg, "error message header");
                    assertTrue(((String) msg).length() <= 2000, "error message는 2000자 제한");
                }
        );
    }

    @Test
    @DisplayName("publishToRetryWithDelay(): TTL(expiration)을 String으로 설정한다")
    void publishToRetryWithDelay_setsExpiration() {
        MessageProperties props = new MessageProperties();
        Message original = new Message("b".getBytes(StandardCharsets.UTF_8), props);

        publisher.publishToRetryWithDelay(original, "rk.retry", 1, 5000L);

        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("ex.retry"), eq("rk.retry"), cap.capture());

        MessageProperties sent = cap.getValue().getMessageProperties();
        assertAll(
                () -> assertEquals("5000", sent.getExpiration(), "expiration은 String이어야 함"),
                () -> assertEquals(1, sent.getHeaders().get(RabbitRetryPublisher.HDR_RETRY_COUNT), "retry count 저장")
        );
    }
}
