/*
 * NOTE:
 * - This test suite intentionally avoids any real external calls (RabbitMQ / ES / AI).
 * - All external dependencies are replaced using @MockitoBean (Spring Boot Mockito override).
 * - If your project uses older Spring Boot (without @MockitoBean), replace @MockitoBean with @MockBean.
 */
package com.nhnacademy.bookssearchworker.worker.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertSame;

class ErrorLogUtilTest {

    @Test
    @DisplayName("root(): 가장 안쪽 cause를 반환한다")
    void root_returnsDeepestCause() {
        RuntimeException deep = new RuntimeException("deep");
        RuntimeException mid = new RuntimeException("mid", deep);
        RuntimeException top = new RuntimeException("top", mid);

        assertSame(deep, ErrorLogUtil.root(top), "deepest cause가 반환되어야 함");
    }

    @Test
    @DisplayName("origin(): 우리 패키지 스택트레이스가 있으면 해당 지점을 반환한다")
    void origin_returnsFirstOurPackageStack() {
        RuntimeException ex = new RuntimeException("boom");
        ex.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("java.lang.String", "valueOf", "String.java", 10),
                new StackTraceElement("com.nhnacademy.bookssearchworker.worker.consumer.BookUpsertConsumer", "consume", "BookUpsertConsumer.java", 55),
                new StackTraceElement("com.other.App", "run", "App.java", 1),
        });

        assertEquals(
                "com.nhnacademy.bookssearchworker.worker.consumer.BookUpsertConsumer#consume:55",
                ErrorLogUtil.origin(ex),
                "우리 패키지 첫 스택이 반환되어야 함"
        );
    }

    @Test
    @DisplayName("origin(): 우리 패키지 스택이 없으면 최상단 스택을 fallback으로 반환한다")
    void origin_fallbackTopStack() {
        RuntimeException ex = new RuntimeException("x");
        ex.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("a.b.C", "m", "C.java", 123),
                new StackTraceElement("d.e.F", "n", "F.java", 456),
        });

        assertEquals("a.b.C#m:123", ErrorLogUtil.origin(ex));
    }

    @Test
    @DisplayName("brief(): 메시지 공백을 정리하고 300자로 자른다")
    void brief_normalizesAndTruncates() {
        String longMsg = ("hello\n\tworld ").repeat(100); // > 300 chars
        RuntimeException ex = new RuntimeException(longMsg);

        String brief = ErrorLogUtil.brief(ex);

        assertAll(
                () -> assertTrue(brief.startsWith("RuntimeException:"), "예외 타입 prefix 누락"),
                () -> assertFalse(brief.contains("\n"), "줄바꿈이 정리되어야 함"),
                () -> assertTrue(brief.length() <= ("RuntimeException: ".length() + 300), "300자 제한을 넘어가면 안됨")
        );
    }
}
