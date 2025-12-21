package com.nhnacademy.bookssearchworker.consumer;

import com.nhnacademy.bookssearchworker.embedding.EmbeddingTextBuilder;
import com.nhnacademy.bookssearchworker.embedding.OllamaEmbeddingClient;
import com.nhnacademy.bookssearchworker.es.BookUpsertDoc;
import com.nhnacademy.bookssearchworker.es.EsBookDocumentClient;
import com.nhnacademy.bookssearchworker.exception.WorkerProcessingException;
import com.nhnacademy.bookssearchworker.message.BookUpsertMessage;
import com.nhnacademy.bookssearchworker.rabbit.RabbitRetryPublisher;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookUpsertConsumer {

    private static final int EXPECTED_EMBEDDING_DIMS = 1024;

    private final EsBookDocumentClient es;
    private final EmbeddingTextBuilder textBuilder;
    private final OllamaEmbeddingClient embeddingClient;
    private final RabbitRetryPublisher retryPublisher;

    @Value("${rabbitmq.routing-key.book-upsert-retry}")
    private String RK_RETRY;
    @Value("${rabbitmq.routing-key.book-upsert-fail}")
    private String RK_FAIL;

    @RabbitListener(queues = "${rabbitmq.queue.book-upsert}", containerFactory = "rabbitListenerContainerFactory")
    public void consume(
            BookUpsertMessage msg,
            Message amqpMessage,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws Exception {
        int retryCount = retryPublisher.getRetryCount(amqpMessage.getMessageProperties());

        try {
            BookUpsertMessage.BookPayload book = (msg == null) ? null : msg.book();
            String isbn = (book == null) ? null : book.isbn();

            log.info("[BOOK_UPSERT] consume isbn={}, requestId={}, retryCount={}",
                    isbn, msg == null ? null : msg.requestId(), retryCount);

            if (isbn == null || isbn.isBlank()) {
                throw new WorkerProcessingException(
                        WorkerProcessingException.ErrorCode.INVALID_MESSAGE,
                        "BookUpsertMessage.book.isbn is null/blank"
                );
            }

            String text = textBuilder.build(book);
            if (text == null || text.isBlank()) {
                throw new WorkerProcessingException(
                        WorkerProcessingException.ErrorCode.INVALID_MESSAGE,
                        "Embedding input text is blank (title/author/publisher/description are all empty?)"
                );
            }

            List<Float> vec = embeddingClient.embed(text);
            log.debug("[BOOK_UPSERT] embedding dim={}",vec.size());

            // ✅ 핵심: ES에 빈 embedding(0차원) 보내지 못하게 차단
            if (vec == null || vec.isEmpty()) {
                throw new WorkerProcessingException(
                        WorkerProcessingException.ErrorCode.EMBEDDING_FAILED,
                        "Embedding is empty (size=0). Check embedding server response."
                );
            }
            if (vec.size() != EXPECTED_EMBEDDING_DIMS) {
                throw new WorkerProcessingException(
                        WorkerProcessingException.ErrorCode.EMBEDDING_FAILED,
                        "Embedding dimension mismatch: got=" + vec.size() + ", expected=" + EXPECTED_EMBEDDING_DIMS
                );
            }

            log.info("[BOOK_UPSERT] embedding ok isbn={}, dims={}", isbn, vec.size());

            BookUpsertDoc doc = new BookUpsertDoc(
                    isbn,
                    book.title(),
                    book.author(),
                    book.publisher(),
                    book.description(),
                    book.pubDate(),
                    book.price(),
                    book.imageUrl(), // ✅ BookUpsertDoc 필드명은 image_url이지만 record 생성자는 순서로 들어감
                    vec
            );

            es.updateById(isbn, doc);

            channel.basicAck(deliveryTag, false);
            log.info("[BOOK_UPSERT] success isbn={}", isbn);
        } catch (Exception e) {
            WorkerProcessingException wpe = wrap(e);

            if (retryCount < RabbitRetryPublisher.MAX_RETRY_COUNT) {
                int next = retryCount + 1;
                retryPublisher.toRetry(amqpMessage, RK_RETRY, next);
                channel.basicAck(deliveryTag, false);
                log.warn("[BOOK_UPSERT] failed -> retry rk={}, nextRetry={}, cause={}", RK_RETRY, next, wpe.getMessage());
            } else {
                retryPublisher.toDlq(amqpMessage, RK_FAIL, wpe);
                channel.basicAck(deliveryTag, false);
                log.error("[BOOK_UPSERT] failed -> dlq rk={}, retries={}, cause={}", RK_FAIL, retryCount, wpe.getMessage(), wpe);
            }
        }
    }

    private WorkerProcessingException wrap(Exception e) {
        if (e instanceof WorkerProcessingException w) return w;
        return new WorkerProcessingException(WorkerProcessingException.ErrorCode.UNKNOWN, e.getMessage(), e);
    }
}
