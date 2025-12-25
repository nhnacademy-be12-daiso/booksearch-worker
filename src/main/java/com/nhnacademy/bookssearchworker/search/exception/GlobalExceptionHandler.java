package com.nhnacademy.bookssearchworker.search.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. @Valid 유효성 검사 실패 시 (ISBN 누락 등)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationErrors(MethodArgumentNotValidException e) {
        // 에러 필드 메시지들을 모아서 반환
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body("요청 데이터 오류: " + errorMessage);
    }

    // 2. 비즈니스 로직 예외 (카테고리 누락 등 우리가 throw new IllegalArgumentException 한 것)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return ResponseEntity.badRequest().body("오류: " + e.getMessage());
    }

    // 3. 그 외 알 수 없는 서버 에러 (Elasticsearch 연결 실패 등)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneralException(Exception e) {
        log.error("서버 내부 오류 발생", e);
        return ResponseEntity.internalServerError().body("서버 처리 중 오류가 발생했습니다: " + e.getMessage());
    }
}