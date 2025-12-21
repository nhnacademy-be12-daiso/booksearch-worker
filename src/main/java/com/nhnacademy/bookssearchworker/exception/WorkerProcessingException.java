package com.nhnacademy.bookssearchworker.exception;

public class WorkerProcessingException extends RuntimeException {

    private final ErrorCode errorCode;

    public WorkerProcessingException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public WorkerProcessingException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public enum ErrorCode {
        INVALID_MESSAGE,
        ES_NOT_FOUND,
        ES_ERROR,
        AI_API_ERROR,
        EMBEDDING_ERROR,
        EMBEDDING_FAILED,
        UNKNOWN
    }
}
