/*
 * NOTE:
 * - This test suite intentionally avoids any real external calls (RabbitMQ / ES / AI).
 * - All external dependencies are replaced using @MockitoBean (Spring Boot Mockito override).
 * - If your project uses older Spring Boot (without @MockitoBean), replace @MockitoBean with @MockBean.
 */
package com.nhnacademy.bookssearchworker.worker.consumer;

import com.nhnacademy.bookssearchworker.worker.es.EsBookDocumentClient;
import com.nhnacademy.bookssearchworker.worker.message.BookDeleteMessage;
import com.nhnacademy.bookssearchworker.worker.rabbit.RabbitRetryPublisher;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = BookDeleteConsumerTest.TestConfig.class)
@TestPropertySource(properties = {
        "rabbitmq.routing.book-delete-retry=rk.delete.retry",
        "rabbitmq.routing.book-delete-fail=rk.delete.fail"
})
class BookDeleteConsumerTest {

    @Import(BookDeleteConsumer.class)
    static class TestConfig { }

    @MockitoBean EsBookDocumentClient es;
    @MockitoBean RabbitRetryPublisher retryPublisher;

    // consume() 파라미터로 들어오는 Channel도 @MockitoBean으로 준비해둔다 (Mockito.mock 금지 대응)
    @MockitoBean Channel channel;

    @Autowired
    BookDeleteConsumer consumer;

    @Test
    @DisplayName("정상 처리: ES delete 호출 + ack")
    void consume_success() throws Exception {
        BookDeleteMessage msg = new BookDeleteMessage("req-1", "9780000000001", System.currentTimeMillis(), "test");
        Message amqp = new Message("x".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(retryPublisher.getRetryCount(any())).thenReturn(0);

        consumer.consume(msg, amqp, channel, 10L);

        assertAll(
                () -> verify(es).deleteById("9780000000001"),
                () -> verify(channel).basicAck(10L, false),
                () -> verify(retryPublisher, never()).toRetry(any(), anyString(), anyInt()),
                () -> verify(retryPublisher, never()).toDlq(any(), anyString(), any())
        );
    }

    @Test
    @DisplayName("실패/재시도: isbn blank면 retry 발행 + ack")
    void consume_invalidMessage_toRetry() throws Exception {
        BookDeleteMessage msg = new BookDeleteMessage("req-2", "   ", System.currentTimeMillis(), "test");
        Message amqp = new Message("x".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(retryPublisher.getRetryCount(any())).thenReturn(0);

        consumer.consume(msg, amqp, channel, 11L);

        assertAll(
                () -> verify(retryPublisher).toRetry(eq(amqp), eq("rk.delete.retry"), eq(1)),
                () -> verify(channel).basicAck(11L, false),
                () -> verify(es, never()).deleteById(anyString()),
                () -> verify(retryPublisher, never()).toDlq(any(), anyString(), any())
        );
    }

    @Test
    @DisplayName("실패/최종: retryCount==MAX면 DLQ 발행 + ack")
    void consume_invalidMessage_toDlq() throws Exception {
        BookDeleteMessage msg = new BookDeleteMessage("req-3", "", System.currentTimeMillis(), "test");
        Message amqp = new Message("x".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(retryPublisher.getRetryCount(any())).thenReturn(RabbitRetryPublisher.MAX_RETRY_COUNT);

        consumer.consume(msg, amqp, channel, 12L);

        assertAll(
                () -> verify(retryPublisher).toDlq(eq(amqp), eq("rk.delete.fail"), any()),
                () -> verify(channel).basicAck(12L, false),
                () -> verify(retryPublisher, never()).toRetry(any(), anyString(), anyInt()),
                () -> verify(es, never()).deleteById(anyString())
        );
    }
}
