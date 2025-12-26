package com.nhnacademy.bookssearchworker.worker.util;

public final class ErrorLogUtil {
    private ErrorLogUtil() {}

    public static String origin(Throwable t) {
        if (t == null) return "-";
        Throwable c = root(t);
        for (StackTraceElement ste : c.getStackTrace()) {
            // 우리 패키지에서 터진 지점만 잡기 (원하면 패키지 범위 조정)
            if (ste.getClassName().startsWith("com.nhnacademy.bookssearchworker")) {
                return ste.getClassName() + "#" + ste.getMethodName() + ":" + ste.getLineNumber();
            }
        }
        // fallback
        StackTraceElement[] st = c.getStackTrace();
        if (st.length > 0) return st[0].getClassName() + "#" + st[0].getMethodName() + ":" + st[0].getLineNumber();
        return "-";
    }

    public static Throwable root(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur;
    }

    public static String brief(Throwable t) {
        if (t == null) return "";
        Throwable r = root(t);
        String msg = r.getMessage();
        msg = (msg == null) ? "" : msg.replaceAll("\\s+", " ");
        if (msg.length() > 300) msg = msg.substring(0, 300);
        return r.getClass().getSimpleName() + ": " + msg;
    }
}
