package com.local.ortfacetest;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
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

    // قسم 6: زر التصدير في المنتصف، وتحته زرا الحذف جنباً إلى جنب (حذف الذاكرة وحذف المحرك والنموذج)
    void build(LinearLayout root, Button deleteEngineBtn) {
        LinearLayout box = UiStyle.card(act, root);
        UiStyle.cardHead(act, box, 6, "السجل");

        buildExportRow(box);
        buildDeleteRow(box, deleteEngineBtn);
    }

    private void buildExportRow(LinearLayout box) {
        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button exportBtn = new Button(act);
        exportBtn.setText("تصدير السجل إلى ملف نصي");
        UiStyle.primary(act, exportBtn);
        UiStyle.addButton(act, top, exportBtn, 0);
        exportBtn.setOnClickListener(v -> startExport());
    }

    private void buildDeleteRow(LinearLayout box, Button deleteEngineBtn) {
        LinearLayout split = new LinearLayout(act);
        split.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = UiStyle.dp(act, 8);
        box.addView(split, sp);

        Button clearBtn = new Button(act);
        clearBtn.setText("حذف ذاكرة السجل");
        UiStyle.danger(act, clearBtn);
        clearBtn.setOnClickListener(v -> clearMemory());

        addSplitButton(split, clearBtn, false);
        addSplitButton(split, deleteEngineBtn, true);
    }

    // زران متساويا العرض في صف واحد بلا التفاف
    private void addSplitButton(LinearLayout split, Button b, boolean hasGapBefore) {
        b.setTextSize(14);
        b.setPadding(UiStyle.dp(act, 6), 0, UiStyle.dp(act, 6), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, UiStyle.dp(act, 48), 1f);
        if (hasGapBefore) lp.setMarginStart(UiStyle.dp(act, 8));
        split.addView(b, lp);
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
