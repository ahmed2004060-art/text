package com.local.ortfacetest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// فرع التحليل: يحسب الإحصاءات من السجل المحفوظ
final class LogAnalysis {

    static final class Stat {
        int ok, medium, failed, crash, info;

        int total() {
            return ok + medium + failed + crash + info;
        }
    }

    private LogAnalysis() {
    }

    static Stat overall(List<ActivityLog.Entry> list) {
        Stat s = new Stat();
        for (ActivityLog.Entry e : list) add(s, e);
        return s;
    }

    static Map<String, Stat> byCategory(List<ActivityLog.Entry> list) {
        Map<String, Stat> map = new LinkedHashMap<>();
        for (ActivityLog.Entry e : list) {
            Stat s = map.get(e.category);
            if (s == null) {
                s = new Stat();
                map.put(e.category, s);
            }
            add(s, e);
        }
        return map;
    }

    private static void add(Stat s, ActivityLog.Entry e) {
        switch (e.level) {
            case OK:
                s.ok++;
                break;
            case MEDIUM:
                s.medium++;
                break;
            case FAILED:
                s.failed++;
                break;
            case CRASH:
                s.crash++;
                break;
            default:
                s.info++;
        }
    }

    // تشخيص حالة كل نموذج من أحداث تحميله وتشغيله
    static Map<String, String> modelDiagnosis(List<ActivityLog.Entry> list) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (ActivityLog.Entry e : list) {
            boolean modelEvent = e.action.equals("تشغيل التحليل") || e.action.equals("استيراد النموذج")
                    || e.action.equals("حالة النموذج");
            if (!modelEvent) continue;
            int[] c = counts.get(e.category);
            if (c == null) {
                c = new int[2];
                counts.put(e.category, c);
            }
            if (e.level == ActivityLog.Level.OK) c[0]++;
            else if (e.level == ActivityLog.Level.FAILED) c[1]++;
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            result.put(e.getKey(), LevelRules.diagnose(e.getValue()[0], e.getValue()[1]));
        }
        return result;
    }

    // ملخص عدد الأحداث حسب الحالة
    static String summary(List<ActivityLog.Entry> list) {
        if (list.isEmpty()) return "لا توجد أحداث محفوظة بعد";
        Stat s = overall(list);
        return "عدد الأحداث: " + s.total()
                + "\n✔ ناجح: " + s.ok
                + "   ◐ متوسط: " + s.medium
                + "   ✖ فشل: " + s.failed
                + "   ⚠ تعطل: " + s.crash;
    }
}
