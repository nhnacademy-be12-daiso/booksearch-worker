package com.nhnacademy.bookssearchworker.search.exception;

import lombok.Getter;

@Getter
public class SearchModuleException extends RuntimeException {
    private final String componentName; // 에러가 발생한 컴포넌트 이름

    public SearchModuleException(String componentName, String message, Throwable cause) {
        super(String.format("[%s] %s", componentName, message), cause);
        this.componentName = componentName;
    }
}