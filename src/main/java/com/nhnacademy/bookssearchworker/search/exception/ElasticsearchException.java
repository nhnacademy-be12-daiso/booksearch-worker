package com.nhnacademy.bookssearchworker.search.exception;

public class ElasticsearchException extends SearchModuleException {
    public ElasticsearchException(String message, Throwable cause) { super("ElasticsearchEngine", message, cause); }
}