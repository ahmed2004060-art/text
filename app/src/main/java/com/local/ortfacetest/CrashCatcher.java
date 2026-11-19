package com.local.ortfacetest;

import java.io.PrintWriter;
import java.io.StringWriter;

// فرع التعطل: يسجل أي استثناء غير ممسوك ثم يترك النظام يكمل إغلاقه المعتاد
final class CrashCatcher {
    private CrashCatcher() {
    }

    static void install() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String trace = sw.toString();
                    ActivityLog.crash("النظام", "تعطل التطبيق (خيط: " + t.getName() + ")", trace);
                } catch (Throwable ignored) {
                }
                if (previous != null) previous.uncaughtException(t, e);
            }
        });
    }
}
