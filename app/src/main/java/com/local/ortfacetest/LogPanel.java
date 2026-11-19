package com.local.ortfacetest;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import java.io.OutputStream;

// فرع الشاشة الخاص بالسجل: زر التصدير وزر حذف الذاكرة فقط
final class LogPanel {
    private static final int REQ_EXPORT = 20;

    private final Activity act;

    LogPanel(Activity act) {
        this.act = act;
    }

    // ------------------------------------------------------------------ بناء القسم

    void build(LinearLayout root, View before) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(UiStyle.shape(act, Color.WHITE, 12, UiStyle.LINE));
        box.setPadding(UiStyle.dp(act, 14), UiStyle.dp(act, 12), UiStyle.dp(act, 14), UiStyle.dp(act, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiStyle.dp(act, 12);
        int at = root.indexOfChild(before);
        if (at < 0) root.addView(box, lp);
        else root.addView(box, at, lp);

        UiStyle.cardTitle(act, box, "6. السجل");

        Button exportBtn = new Button(act);
        exportBtn.setText("تصدير السجل إلى ملف نصي");
        UiStyle.primary(act, exportBtn);
        UiStyle.addButton(act, box, exportBtn);
        exportBtn.setOnClickListener(v -> startExport());

        Button clearBtn = new Button(act);
        clearBtn.setText("حذف ذاكرة السجل");
        UiStyle.danger(act, clearBtn);
        UiStyle.addButton(act, box, clearBtn);
        clearBtn.setOnClickListener(v -> clearMemory());
    }

    // ------------------------------------------------------------------ التصدير

    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, LogExporter.fileName());
        try {
            act.startActivityForResult(intent, REQ_EXPORT);
        } catch (Exception e) {
            ActivityLog.failed("السجل", "تصدير السجل", -1, "تعذر فتح نافذة الحفظ: " + e.getMessage());
        }
    }

    // true إذا كان الطلب يخص التصدير
    boolean onResult(int requestCode, Uri uri) {
        if (requestCode != REQ_EXPORT) return false;
        final Uri target = uri;
        new Thread(() -> {
            try (OutputStream out = act.getContentResolver().openOutputStream(target, "wt")) {
                if (out == null) throw new java.io.IOException("تعذر فتح الملف للكتابة");
                LogExporter.write(out);
                ActivityLog.ok("السجل", "تصدير السجل", -1, "تم حفظ التقرير في ملف نصي على الجهاز");
            } catch (Throwable t) {
                String m = t.getMessage();
                ActivityLog.failed("السجل", "تصدير السجل", -1,
                        "فشل التصدير: " + (m == null ? t.getClass().getSimpleName() : m));
            }
        }).start();
        return true;
    }

    // ------------------------------------------------------------------ حذف الذاكرة

    private void clearMemory() {
        ActivityLog.clear();
    }
}
