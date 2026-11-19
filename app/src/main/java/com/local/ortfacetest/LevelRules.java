package com.local.ortfacetest;

// فرع التشخيص: يحدد حالة النموذج (يعمل بكامل قدرته / متوسط / لم يعمل) مع سبب الحكم
final class LevelRules {
    private LevelRules() {
    }

    static String diagnose(int okCount, int failedCount) {
        String reason = " — السبب: نجحت " + okCount + " عملية وفشلت " + failedCount + " عملية";
        if (failedCount == 0) return "يعمل بكامل قدرته" + reason;
        if (okCount == 0) return "لم يعمل" + reason;
        return "متوسط" + reason;
    }
}
