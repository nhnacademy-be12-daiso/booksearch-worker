package com.nhnacademy.bookssearchworker.search.exception;

public class EmbeddingException extends SearchModuleException {
    public EmbeddingException(String message, Throwable cause) { super("EmbeddingClient", message, cause); }
}