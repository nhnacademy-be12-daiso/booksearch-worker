package com.nhnacademy.bookssearchworker.rabbit;

import com.nhnacademy.bookssearchworker.exception.WorkerProcessingException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 재시도 및 DLQ 발행 전담 컴포넌트
 */
@Component
public class RabbitRetryPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.main:team3.booksearch.exchange}")
    private String mainExchange;

    @Value("${rabbitmq.exchange.retry:team3.booksearch.retry.dlx}")
    private String retryExchange;

    @Value("${rabbitmq.exchange.dlx:team3.booksearch.dlx}")
    private String dlxExchange;

    public static final String HDR_RETRY_COUNT = "x-retry-count";
    public static final String HDR_ERROR_CODE = "x-error-code";
    public static final String HDR_ERROR_MESSAGE = "x-error-message";
    public static final int MAX_RETRY_COUNT = 3;

    public RabbitRetryPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // 재시도 큐로 메시지 발행
    public void toRetry(Message original, String retryRoutingKey, int nextRetryCount) {
        MessageProperties props = copyProperties(original.getMessageProperties());
        props.getHeaders().put(HDR_RETRY_COUNT, nextRetryCount);
        rabbitTemplate.send(retryExchange, retryRoutingKey, new Message(original.getBody(), props));
    }

    // DLQ로 메시지 발행
    public void toDlq(Message original, String failRoutingKey, WorkerProcessingException ex) {
        MessageProperties props = copyProperties(original.getMessageProperties());
        props.getHeaders().put(HDR_ERROR_CODE, ex.getErrorCode().name());
        props.getHeaders().put(HDR_ERROR_MESSAGE, safeMsg(ex.getMessage()));
        rabbitTemplate.send(dlxExchange, failRoutingKey, new Message(original.getBody(), props));
    }

    // 메인 교환기로 메시지 발행
    public void toMain(Message original, String routingKey) {
        rabbitTemplate.send(mainExchange, routingKey, original);
    }

    // 메시지의 재시도 횟수 조회
    public int getRetryCount(MessageProperties props) {
        if (props == null || props.getHeaders() == null) return 0;
        Object v = props.getHeaders().get(HDR_RETRY_COUNT);
        if (v == null) return 0;
        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return l.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignore) { return 0; }
        }
        return 0;
    }

    // 재시도 큐로 메시지 발행 (예외 정보 포함)
    public void publishToRetry(Message original, String retryRoutingKey, int nextRetryCount, WorkerProcessingException ex) {
        MessageProperties props = copyProperties(original.getMessageProperties());
        props.getHeaders().put(HDR_RETRY_COUNT, nextRetryCount);
        rabbitTemplate.send(retryExchange, retryRoutingKey, new Message(original.getBody(), props));
    }

    // DLQ로 메시지 발행 (예외 정보 포함)
    public void publishToDlx(Message original, String failRoutingKey, int retryCount, WorkerProcessingException ex) {
        MessageProperties props = copyProperties(original.getMessageProperties());
        props.getHeaders().put(HDR_RETRY_COUNT, retryCount);
        props.getHeaders().put(HDR_ERROR_CODE, ex.getErrorCode().name());
        props.getHeaders().put(HDR_ERROR_MESSAGE, safeMsg(ex.getMessage()));
        rabbitTemplate.send(dlxExchange, failRoutingKey, new Message(original.getBody(), props));
    }

    // 원하는 시간 지연 후 재시도 큐로 메시지 발행(TTL 활용)
    public void publishToRetryWithDelay(Message original, String retryRoutingKey, int nextRetryCount, long delayMs) {
        MessageProperties props = copyProperties(original.getMessageProperties());

        props.getHeaders().put(HDR_RETRY_COUNT, nextRetryCount);
        // RabbitMQ 메시지 만료 시간 설정 (String으로 넣어야 함)
        props.setExpiration(String.valueOf(delayMs));

        rabbitTemplate.send(retryExchange, retryRoutingKey, new Message(original.getBody(), props));
    }

    // 메시지 내용을 안전하게 자르기
    private static String safeMsg(String s) {
        if (s == null) return "";
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }

    // 메시지 속성 복사
    private static MessageProperties copyProperties(MessageProperties src) {
        MessageProperties dst = new MessageProperties();

        if (src == null) return dst;

        // 기본 메타
        dst.setContentType(src.getContentType());
        dst.setContentEncoding(src.getContentEncoding());
        dst.setCorrelationId(src.getCorrelationId());
        dst.setMessageId(src.getMessageId());
        dst.setTimestamp(src.getTimestamp());
        dst.setDeliveryMode(src.getDeliveryMode());

        // headers 복사
        Map<String, Object> headers = new HashMap<>();
        if (src.getHeaders() != null) {
            headers.putAll(src.getHeaders());
        }
        dst.getHeaders().putAll(headers);

        // Spring이 채우는 received* 정보는 복사하지 않아도 무방
        return dst;
    }
}
