package com.nhnacademy.bookssearchworker.message;

import java.util.List;

/**
 * AI 분석 요청 메시지
 */
public record AiAnalysisRequestMessage(
        String requestId,
        List<String> isbns,
        long timestamp
) {}