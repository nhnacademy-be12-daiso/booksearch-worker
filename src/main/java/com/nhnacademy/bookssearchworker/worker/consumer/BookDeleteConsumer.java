package com.nhnacademy.bookssearchworker.worker.consumer;

import com.nhnacademy.bookssearchworker.worker.es.EsBookDocumentClient;
import com.nhnacademy.bookssearchworker.worker.exception.WorkerProcessingException;
import com.nhnacademy.bookssearchworker.worker.message.BookDeleteMessage;
import com.nhnacademy.bookssearchworker.worker.rabbit.RabbitRetryPublisher;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookDeleteConsumer {

    private final EsBookDocumentClient es;
    private final RabbitRetryPublisher retryPublisher;

    @Value("${rabbitmq.routing-key.book-delete-retry}")
    private String RK_RETRY;
    @Value("${rabbitmq.routing-key.book-delete-fail}")
    private String RK_FAIL;

    @RabbitListener(queues = "${rabbitmq.queue.book-delete}", containerFactory = "rabbitListenerContainerFactory")
    public void consume(
            BookDeleteMessage msg,
            Message amqpMessage,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws Exception {
        int retryCount = retryPublisher.getRetryCount(amqpMessage.getMessageProperties());

        try {
            String isbn = (msg == null) ? null : msg.isbn();

            log.info("[BOOK_DELETE] consume isbn={}, requestId={}, retryCount={}", isbn, msg == null ? null : msg.requestId(), retryCount);

            if (isbn == null || isbn.isBlank()) {
                throw new WorkerProcessingException(WorkerProcessingException.ErrorCode.INVALID_MESSAGE, "BookDeleteMessage.isbn is null/blank");
            }

            es.deleteById(isbn);

            channel.basicAck(deliveryTag, false);
            log.info("[BOOK_DELETE] success isbn={}", isbn);
        } catch (Exception e) {
            WorkerProcessingException wpe = wrap(e);

            if (retryCount < RabbitRetryPublisher.MAX_RETRY_COUNT) {
                int next = retryCount + 1;
                retryPublisher.toRetry(amqpMessage, RK_RETRY, next);
                channel.basicAck(deliveryTag, false);
                log.warn("[BOOK_DELETE] failed -> retry rk={}, nextRetry={}, cause={}", RK_RETRY, next, wpe.getMessage());
            } else {
                retryPublisher.toDlq(amqpMessage, RK_FAIL, wpe);
                channel.basicAck(deliveryTag, false);
                log.error("[BOOK_DELETE] failed -> dlq rk={}, retries={}, cause={}", RK_FAIL, retryCount, wpe.getMessage(), wpe);
            }
        }
    }

    private WorkerProcessingException wrap(Exception e) {
        if (e instanceof WorkerProcessingException w) return w;
        return new WorkerProcessingException(WorkerProcessingException.ErrorCode.UNKNOWN, e.getMessage(), e);
    }
}
