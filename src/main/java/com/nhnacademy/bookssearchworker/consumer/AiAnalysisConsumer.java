package com.nhnacademy.bookssearchworker.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.nhnacademy.bookssearchworker.ai.GeminiAiClient;
import com.nhnacademy.bookssearchworker.es.EsBookDocumentClient;
import com.nhnacademy.bookssearchworker.exception.GeminiQuotaException;
import com.nhnacademy.bookssearchworker.exception.WorkerProcessingException;
import com.nhnacademy.bookssearchworker.message.AiAnalysisRequestMessage;
import com.nhnacademy.bookssearchworker.rabbit.RabbitRetryPublisher;
import com.nhnacademy.bookssearchworker.util.ErrorLogUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 분석 요청 처리 컨슈머 (Batch)
 * - 429 오류: 24시간 대기 (무한 재시도)
 * - 일반 오류: 3회 재시도 후 Fail(DLQ)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnalysisConsumer {

    private final EsBookDocumentClient es;
    private final GeminiAiClient gemini;
    private final RabbitRetryPublisher retryPublisher;

    @Value("${rabbitmq.routing-key.ai-analysis-retry}")
    private String RK_RETRY;
    @Value("${rabbitmq.routing-key.ai-analysis-fail}")
    private String RK_FAIL;

    // 24시간 (429 오류용)
    private static final long QUOTA_RESET_DELAY_MS = 24 * 60 * 60 * 1000L;

    @RabbitListener(queues = "${rabbitmq.queue.ai-analysis}", containerFactory = "rabbitListenerContainerFactory")
    public void consume(AiAnalysisRequestMessage msg, Message amqpMessage, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {

        int retryCount = retryPublisher.getRetryCount(amqpMessage.getMessageProperties());

        try {
            if (msg == null || msg.isbns() == null || msg.isbns().isEmpty()) {
                throw new WorkerProcessingException(
                        WorkerProcessingException.ErrorCode.INVALID_MESSAGE,
                        "ISBN list is empty or null");
            }

            log.info("[AI Batch] consume size={}, requestId={}, retry={}", msg.isbns().size(), msg.requestId(), retryCount);

            // 1. ES 데이터 준비
            List<GeminiAiClient.BookInfo> booksToSend = new ArrayList<>();
            for (String isbn : msg.isbns()) {
                // 개별 조회 실패는 로그만 남기고 스킵 (전체 재시도 X)
                try {
                    es.getSourceById(isbn).ifPresent(node -> booksToSend.add(mapToBookInfo(isbn, node)));
                } catch (Exception e) {
                    log.warn("[AI Batch] ES fetch failed for isbn={}", isbn);
                }
            }

            Map<String, GeminiAiClient.AiResult> aiResults;
            aiResults = gemini.analyzeBulk(booksToSend);


            // 3. 결과 업데이트
            for (GeminiAiClient.BookInfo book : booksToSend) {
                GeminiAiClient.AiResult result = aiResults.get(book.isbn());
                if (result != null && !result.pros().isEmpty()) {
                    log.info("[Dry-run] ISBN={} | Gemini 응답: {}", book.isbn(), result);
                    try {
                        es.updateById(book.isbn(), new AiUpdate(result));
                    } catch (Exception e) {
                        log.error("[AI Batch] Update failed isbn={}", book.isbn(), e);
                    }
                }
            }

            log.info("[AI Batch] Batch processing completed.");
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            // [일반 케이스] 그 외 모든 오류 (503, ES 오류 등) -> 기존 로직대로 3회 재시도 후 Fail
            WorkerProcessingException wrapped = (e instanceof WorkerProcessingException wpe)
                    ? wpe
                    : new WorkerProcessingException(WorkerProcessingException.ErrorCode.UNKNOWN,
                    "AI batch processing failed", e);

            String where = ErrorLogUtil.origin(wrapped);
            String brief = ErrorLogUtil.brief(wrapped);

            log.warn("[AI Batch] failed rkRetry={}, rkFail={}, retry={}, code={}, where={}, cause={}",
                    RK_RETRY, RK_FAIL, retryCount, wrapped.getErrorCode(), where, brief);

            if (retryCount < 3) {
                retryPublisher.publishToRetry(amqpMessage, RK_RETRY, retryCount + 1, wrapped);
            } else {
                retryPublisher.publishToDlx(amqpMessage, RK_FAIL, retryCount, wrapped);
            }

            channel.basicAck(deliveryTag, false);
        }
    }

    private GeminiAiClient.BookInfo mapToBookInfo(String isbn, JsonNode src) {
        return new GeminiAiClient.BookInfo(
                isbn,
                src.path("title").asText(""),
                src.path("author").asText(""),
                src.path("description").asText("")
        );
    }

    private record AiUpdate(GeminiAiClient.AiResult aiResult) {}
}