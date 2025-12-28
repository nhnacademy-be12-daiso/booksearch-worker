/*
 * NOTE:
 * - This test suite intentionally avoids any real external calls (RabbitMQ / ES / AI).
 * - All external dependencies are replaced using @MockitoBean (Spring Boot Mockito override).
 * - If your project uses older Spring Boot (without @MockitoBean), replace @MockitoBean with @MockBean.
 */
package com.nhnacademy.bookssearchworker.worker.consumer;

import com.nhnacademy.bookssearchworker.worker.embedding.EmbeddingTextBuilder;
import com.nhnacademy.bookssearchworker.worker.embedding.OllamaEmbeddingClient;
import com.nhnacademy.bookssearchworker.worker.es.EsBookDocumentClient;
import com.nhnacademy.bookssearchworker.worker.message.BookUpsertMessage;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = BookUpsertConsumerTest.TestConfig.class)
@TestPropertySource(properties = {
        "rabbitmq.routing.book-upsert-retry=rk.upsert.retry",
        "rabbitmq.routing.book-upsert-fail=rk.upsert.fail"
})
class BookUpsertConsumerTest {

    @Import(BookUpsertConsumer.class)
    static class TestConfig { }

    @MockitoBean EsBookDocumentClient es;
    @MockitoBean EmbeddingTextBuilder textBuilder;
    @MockitoBean OllamaEmbeddingClient embeddingClient;
    @MockitoBean RabbitRetryPublisher retryPublisher;
    @MockitoBean Channel channel; // 파라미터로 전달할 Channel도 MockitoBean으로 준비

    @Autowired
    BookUpsertConsumer consumer;

    private static BookUpsertMessage validMessage() {
        BookUpsertMessage.BookPayload book = new BookUpsertMessage.BookPayload(
                1L, "9780000000001", "Title", "Author", "Publisher",
                "Desc", LocalDate.of(2025, 1, 1), 10000, List.of("C1"),
                "img", 10L, 20L
        );
        return new BookUpsertMessage("req-1", book, System.currentTimeMillis(), "test");
    }

    private static List<Float> vec(int dims) {
        ArrayList<Float> v = new ArrayList<>(dims);
        for (int i = 0; i < dims; i++) v.add(0.01f);
        return v;
    }

    @Test
    @DisplayName("정상 처리: build->embed->ES update + ack")
    void consume_success() throws Exception {
        BookUpsertMessage msg = validMessage();
        Message amqp = new Message("x".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(retryPublisher.getRetryCount(any())).thenReturn(0);
        when(textBuilder.build(any(BookUpsertMessage.BookPayload.class))).thenReturn("text");
        when(embeddingClient.embed("text")).thenReturn(vec(1024));

        consumer.consume(msg, amqp, channel, 100L);

        assertAll(
                () -> verify(es).updateById(eq("9780000000001"), any()),
                () -> verify(channel).basicAck(100L, false),
                () -> verify(retryPublisher, never()).toRetry(any(), anyString(), anyInt()),
                () -> verify(retryPublisher, never()).toDlq(any(), anyString(), any())
        );
    }

    @Test
    @DisplayName("실패/재시도: isbn null/blank면 INVALID_MESSAGE -> retry + ack")
    void consume_invalidIsbn_toRetry() throws Exception {
        BookUpsertMessage.BookPayload book = new BookUpsertMessage.BookPayload(
                1L, "  ", "Title", "Author", "Publisher",
                "Desc", LocalDate.now(), 10000, List.of("C1"),
                "img", 10L, 20L
        );
        BookUpsertMessage msg = new BookUpsertMessage("req-2", book, System.currentTimeMillis(), "test");
        Message amqp = new Message("x".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(retryPublisher.getRetryCount(any())).thenReturn(0);

        consumer.consume(msg, amqp, channel, 101L);

        assertAll(
                () -> verify(retryPublisher).toRetry(eq(amqp), eq("rk.upsert.retry"), eq(1)),
                () -> verify(channel).basicAck(101L, false),
                () -> verify(es, never()).updateById(anyString(), any())
        );
    }

    @Test
    @DisplayName("실패/재시도: 임베딩 입력 텍스트가 blank면 retry + ack")
    void consume_blankText_toRetry() throws Exception {
        BookUpsertMessage msg = validMessage();
        Message amqp = new Message("x".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(retryPublisher.getRetryCount(any())).thenReturn(0);
        when(textBuilder.build(any(BookUpsertMessage.BookPayload.class))).thenReturn("   "); // blank

        consumer.consume(msg, amqp, channel, 102L);

        assertAll(
                () -> verify(retryPublisher).toRetry(eq(amqp), eq("rk.upsert.retry"), eq(1)),
                () -> verify(channel).basicAck(102L, false),
                () -> verify(embeddingClient, never()).embed(anyString()),
                () -> verify(es, never()).updateById(anyString(), any())
        );
    }

    @Test
    @DisplayName("실패/재시도: 임베딩 결과가 empty면 EMBEDDING_FAILED -> retry + ack")
    void consume_emptyEmbedding_toRetry() throws Exception {
        BookUpsertMessage msg = validMessage();
        Message amqp = new Message("x".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(retryPublisher.getRetryCount(any())).thenReturn(0);
        when(textBuilder.build(any(BookUpsertMessage.BookPayload.class))).thenReturn("text");
        when(embeddingClient.embed("text")).thenReturn(List.of()); // empty

        consumer.consume(msg, amqp, channel, 103L);

        assertAll(
                () -> verify(retryPublisher).toRetry(eq(amqp), eq("rk.upsert.retry"), eq(1)),
                () -> verify(channel).basicAck(103L, false),
                () -> verify(es, never()).updateById(anyString(), any())
        );
    }

    @Test
    @DisplayName("실패/재시도: 임베딩 차원 불일치면 EMBEDDING_FAILED -> retry + ack")
    void consume_dimMismatch_toRetry() throws Exception {
        BookUpsertMessage msg = validMessage();
        Message amqp = new Message("x".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(retryPublisher.getRetryCount(any())).thenReturn(0);
        when(textBuilder.build(any(BookUpsertMessage.BookPayload.class))).thenReturn("text");
        when(embeddingClient.embed("text")).thenReturn(vec(3)); // mismatch

        consumer.consume(msg, amqp, channel, 104L);

        assertAll(
                () -> verify(retryPublisher).toRetry(eq(amqp), eq("rk.upsert.retry"), eq(1)),
                () -> verify(channel).basicAck(104L, false),
                () -> verify(es, never()).updateById(anyString(), any())
        );
    }

    @Test
    @DisplayName("실패/최종: retryCount==MAX면 DLQ 발행 + ack (원인 예외가 Runtime/NPE라도 wrap 처리)")
    void consume_whenMaxRetry_toDlq() throws Exception {
        BookUpsertMessage msg = validMessage();
        Message amqp = new Message("x".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(retryPublisher.getRetryCount(any())).thenReturn(RabbitRetryPublisher.MAX_RETRY_COUNT);
        when(textBuilder.build(any(BookUpsertMessage.BookPayload.class))).thenReturn("text");
        when(embeddingClient.embed("text")).thenThrow(new NullPointerException("boom"));

        consumer.consume(msg, amqp, channel, 105L);

        assertAll(
                () -> verify(retryPublisher).toDlq(eq(amqp), eq("rk.upsert.fail"), any()),
                () -> verify(channel).basicAck(105L, false),
                () -> verify(retryPublisher, never()).toRetry(any(), anyString(), anyInt()),
                () -> verify(es, never()).updateById(anyString(), any())
        );
    }
}
