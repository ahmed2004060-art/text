package com.local.ortfacetest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

// فرع التصدير: يبني نص التقرير الكامل ويكتبه في الملف الذي اختاره المستخدم
final class LogExporter {
    private LogExporter() {
    }

    static String fileName() {
        return "ortface_log_" + ActivityLog.format(System.currentTimeMillis())
                .replace(' ', '_').replace(":", "-") + ".txt";
    }

    static String build() {
        List<ActivityLog.Entry> list = ActivityLog.all();
        StringBuilder sb = new StringBuilder();
        sb.append("===== تقرير سجل التطبيق =====\n");

        sb.append("----- الملخص العام -----\n").append(LogAnalysis.summary(list)).append("\n\n");

        sb.append("----- التحليل حسب القسم -----\n");
        for (Map.Entry<String, LogAnalysis.Stat> e : LogAnalysis.byCategory(list).entrySet()) {
            LogAnalysis.Stat s = e.getValue();
            sb.append(e.getKey()).append(": ناجح ").append(s.ok).append("، متوسط ").append(s.medium)
                    .append("، فشل ").append(s.failed).append("، تعطل ").append(s.crash)
                    .append("، معلومات ").append(s.info)
                    .append('\n');
        }

        sb.append("\n----- تشخيص حالة النموذج -----\n");
        for (Map.Entry<String, String> e : LogAnalysis.modelDiagnosis(list).entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }

        sb.append("\n----- كل الأحداث بالترتيب الزمني -----\n");
        int n = 1;
        for (ActivityLog.Entry e : list) {
            sb.append('#').append(n++).append('\n');
            sb.append("التاريخ والوقت: ").append(ActivityLog.format(e.time)).append('\n');
            sb.append("الحالة: ").append(e.level.ar).append('\n');
            sb.append("القسم: ").append(e.category).append('\n');
            sb.append("الإجراء: ").append(e.action).append('\n');
            if (e.ms >= 0) sb.append("الزمن: ").append(e.ms).append(" مللي ثانية\n");
            sb.append("التفاصيل: ").append(e.details).append("\n\n");
        }
        return sb.toString();
    }

    static void write(OutputStream out) throws IOException {
        out.write("\uFEFF".getBytes(StandardCharsets.UTF_8));
        out.write(build().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
