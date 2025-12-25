package com.nhnacademy.bookssearchworker.search.exception;

public class LlmAnalysisException extends SearchModuleException {
    public LlmAnalysisException(String message, Throwable cause) { super("LlmClient", message, cause); }
}