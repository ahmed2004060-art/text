package com.local.ortfacetest;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// فرع التخزين: يحفظ كل حدث في ملف داخل التطبيق فور وقوعه (حتى يبقى السجل لو تعطل التطبيق)
final class ActivityLog {

    enum Level {
        OK("ناجح"), MEDIUM("متوسط"), FAILED("فشل"), CRASH("تعطل"), INFO("معلومة");

        final String ar;

        Level(String ar) {
            this.ar = ar;
        }
    }

    static final class Entry {
        final long time;
        final Level level;
        final String category;
        final String action;
        final long ms;       // -1 إذا لم يُقَس زمن
        final String details;

        Entry(long time, Level level, String category, String action, long ms, String details) {
            this.time = time;
            this.level = level;
            this.category = category;
            this.action = action;
            this.ms = ms;
            this.details = details;
        }
    }

    private static final Object LOCK = new Object();
    private static File logFile;

    private ActivityLog() {
    }

    static void init(Context c) {
        synchronized (LOCK) {
            File dir = new File(c.getFilesDir(), "activity");
            if (!dir.isDirectory()) dir.mkdirs();
            logFile = new File(dir, "activity_log.txt");
        }
    }

    static String format(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(time));
    }

    // ------------------------------------------------------------------ التسجيل

    static void add(Level level, String category, String action, long ms, String details) {
        synchronized (LOCK) {
            if (logFile == null) return;
            String line = System.currentTimeMillis() + "\t" + level.name() + "\t" + esc(category) + "\t"
                    + esc(action) + "\t" + ms + "\t" + esc(details) + "\n";
            try (FileOutputStream out = new FileOutputStream(logFile, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } catch (Throwable ignored) {
            }
        }
    }

    static void info(String category, String action, String details) {
        add(Level.INFO, category, action, -1, details);
    }

    static void ok(String category, String action, long ms, String details) {
        add(Level.OK, category, action, ms, details);
    }

    static void failed(String category, String action, long ms, String details) {
        add(Level.FAILED, category, action, ms, details);
    }

    static void crash(String category, String action, String details) {
        add(Level.CRASH, category, action, -1, details);
    }

    // ------------------------------------------------------------------ القراءة والحذف

    static List<Entry> all() {
        List<Entry> list = new ArrayList<>();
        synchronized (LOCK) {
            if (logFile == null || !logFile.isFile()) return list;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(logFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.split("\t", -1);
                    if (p.length < 6) continue;
                    try {
                        list.add(new Entry(Long.parseLong(p[0]), Level.valueOf(p[1]), unesc(p[2]),
                                unesc(p[3]), Long.parseLong(p[4]), unesc(p[5])));
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return list;
    }

    static void clear() {
        synchronized (LOCK) {
            if (logFile != null) logFile.delete();
        }
    }

    // ------------------------------------------------------------------ ترميز السطر

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '\t') sb.append("\\t");
            else if (c == '\n') sb.append("\\n");
            else if (c != '\r') sb.append(c);
        }
        return sb.toString();
    }

    private static String unesc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
