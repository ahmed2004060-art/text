package com.local.ortfacetest;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

// قسم "النماذج الإضافية" في الشاشة: لكل نموذج لوحة مستقلة (استيراد + تشغيل). النماذج الإضافية تُضاف هنا لوحةً لوحة
final class ExtraModels {
    private static final int REQ_SFACE = 4;
    private static final int REQ_EXPRESSION = 5;

    private static final int GRAY = 0xFF6B7280;
    private static final int GREEN = 0xFF15803D;
    private static final int RED = 0xFFB91C1C;

    private interface Job {
        ExtraResult run(Bitmap src, File model) throws Exception;
    }

    private final class Panel {
        final String fileName;
        final int request;
        TextView status;
        Button importBtn;
        Button runBtn;

        Panel(String fileName, int request) {
            this.fileName = fileName;
            this.request = request;
        }

        File file() {
            return new File(host.modelsDir(), fileName);
        }
    }

    private final ExtraHost host;
    private final Activity act;
    private Panel sface;
    private Panel expression;

    ExtraModels(ExtraHost host) {
        this.host = host;
        this.act = host.activity();
    }

    // ------------------------------------------------------------------ بناء الشاشة

    void build(LinearLayout root, View before) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.WHITE);
        box.setPadding(dp(12), dp(8), dp(12), dp(12));
        box.addView(label("5. النماذج الإضافية", 18, true, Color.BLACK));

        sface = new Panel(SFaceModel.FILE, REQ_SFACE);
        addPanel(box, sface, SFaceModel.TITLE, SFaceModel.HINT, "تشغيل SFace", new Job() {
            @Override
            public ExtraResult run(Bitmap src, File model) throws Exception {
                return SFaceModel.run(host, src, model);
            }
        });

        expression = new Panel(ExpressionModel.FILE, REQ_EXPRESSION);
        addPanel(box, expression, ExpressionModel.TITLE, ExpressionModel.HINT, "تشغيل MobileFaceNet", new Job() {
            @Override
            public ExtraResult run(Bitmap src, File model) throws Exception {
                return ExpressionModel.run(host, src, model);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        int at = root.indexOfChild(before);
        if (at < 0) root.addView(box, lp);
        else root.addView(box, at, lp);
    }

    private void addPanel(LinearLayout box, final Panel p, String title, String hint,
                          String runText, final Job job) {
        box.addView(label(title, 16, true, Color.BLACK));
        box.addView(label(hint, 13, false, GRAY));

        p.importBtn = new Button(act);
        p.importBtn.setText("استيراد النموذج من الهاتف");
        box.addView(p.importBtn);
        p.importBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pick(p.request);
            }
        });

        p.status = label("", 15, false, GRAY);
        box.addView(p.status);
        File f = p.file();
        if (f.isFile() && f.length() > 0) {
            setStatus(p, "الحالة: محفوظ — " + (f.length() / 1024) + " كيلوبايت", GREEN);
        } else {
            setStatus(p, "الحالة: غير مستورد", GRAY);
        }

        p.runBtn = new Button(act);
        p.runBtn.setText(runText);
        box.addView(p.runBtn);
        p.runBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch(p, job);
            }
        });
    }

    // يُستدعى كلما تغيّرت حالة الأزرار في الشاشة
    void refresh() {
        refreshPanel(sface);
        refreshPanel(expression);
    }

    private void refreshPanel(Panel p) {
        if (p == null || p.importBtn == null) return;
        boolean busy = host.busy();
        File f = p.file();
        p.importBtn.setEnabled(!busy);
        p.runBtn.setEnabled(!busy && host.engineReady() && host.yunetReady()
                && host.image() != null && f.isFile() && f.length() > 0);
    }

    // ------------------------------------------------------------------ اختيار الملف

    private void pick(int code) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            act.startActivityForResult(intent, code);
        } catch (Exception e) {
            Panel p = code == REQ_SFACE ? sface : expression;
            setStatus(p, "الحالة: فشل — تعذر فتح مستعرض الملفات: " + e.getMessage(), RED);
        }
    }

    // true إذا كان الطلب يخص النماذج الإضافية
    boolean onResult(int requestCode, Uri uri) {
        if (requestCode == REQ_SFACE) {
            importModel(sface, uri);
            return true;
        }
        if (requestCode == REQ_EXPRESSION) {
            importModel(expression, uri);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ الاستيراد

    private void importModel(final Panel p, final Uri uri) {
        if (!host.engineReady()) {
            setStatus(p, "الحالة: فشل — استورد المحرك أولاً حتى أتأكد من سلامة النموذج", RED);
            return;
        }
        host.setBusy(true);
        setStatus(p, "الحالة: جارٍ الاستيراد...", GRAY);
        host.execute(new Runnable() {
            @Override
            public void run() {
                String message;
                int color;
                File tmp = new File(host.modelsDir(), p.fileName + ".tmp");
                try {
                    File dir = host.modelsDir();
                    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("تعذر إنشاء مجلد التخزين");
                    try (InputStream in = act.getContentResolver().openInputStream(uri);
                         FileOutputStream out = new FileOutputStream(tmp)) {
                        if (in == null) throw new IOException("تعذر قراءة الملف");
                        byte[] buffer = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                    if (tmp.length() == 0) throw new IOException("الملف فارغ");
                    String bad = validate(tmp);
                    if (bad != null) {
                        tmp.delete();
                        message = "الحالة: فشل — " + bad;
                        color = RED;
                    } else {
                        File target = p.file();
                        target.delete();
                        if (!tmp.renameTo(target)) throw new IOException("تعذر حفظ النموذج");
                        message = "الحالة: جاهز — " + (target.length() / 1024) + " كيلوبايت";
                        color = GREEN;
                    }
                } catch (Throwable t) {
                    tmp.delete();
                    message = "الحالة: فشل — " + reason(t);
                    color = RED;
                }
                try {
                    host.restoreYuNet();
                } catch (Throwable ignored) {
                }
                final String m = message;
                final int c = color;
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setStatus(p, m, c);
                        host.setBusy(false);
                    }
                });
            }
        });
    }

    // يعيد null إذا كان الملف نموذجاً سليماً بمدخل 112×112 ومخرج واحد، وإلا سبب الرفض
    private String validate(File f) {
        String r = NativeBridge.loadModel(f.getAbsolutePath());
        if (!r.startsWith("OK|")) {
            return "الملف ليس نموذجاً سليماً: " + (r.startsWith("ERR|") ? r.substring(4) : r);
        }
        String[] parts = r.split("\\|", -1);
        try {
            int w = Integer.parseInt(parts[1]);
            int h = Integer.parseInt(parts[2]);
            if ((w != 0 && w != FaceAlign.SIZE) || (h != 0 && h != FaceAlign.SIZE)) {
                return "مدخل النموذج " + w + "×" + h + " والمتوقع 112×112";
            }
        } catch (Exception ignored) {
        }
        String csv = parts.length > 3 ? parts[3] : "";
        int outputs = csv.isEmpty() ? 0 : csv.split(",").length;
        if (outputs != 1) return "عدد مخرجات النموذج " + outputs + " والمتوقع 1";
        return null;
    }

    // ------------------------------------------------------------------ التشغيل

    private void launch(final Panel p, final Job job) {
        final Bitmap src = host.image();
        if (src == null || !host.engineReady() || !host.yunetReady()) return;
        host.setBusy(true);
        host.showResult("جارٍ التحليل...", null, false);
        host.execute(new Runnable() {
            @Override
            public void run() {
                final ExtraResult[] res = new ExtraResult[1];
                final String[] err = new String[1];
                try {
                    res[0] = job.run(src, p.file());
                } catch (Throwable t) {
                    err[0] = reason(t);
                }
                try {
                    host.restoreYuNet();
                } catch (Throwable ignored) {
                }
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (err[0] != null) {
                            host.showResult("فشل في مرحلة النموذج: " + err[0], null, true);
                        } else {
                            host.showResult(res[0].text, res[0].image, false);
                        }
                        host.setBusy(false);
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------ مساعدات

    private void setStatus(Panel p, String text, int color) {
        p.status.setText(text);
        p.status.setTextColor(color);
    }

    private static String reason(Throwable t) {
        if (t instanceof OutOfMemoryError) return "الذاكرة لا تكفي";
        String m = t.getMessage();
        return (m == null || m.isEmpty()) ? t.getClass().getSimpleName() : m;
    }

    private int dp(float v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, int sp, boolean bold, int color) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }
}
