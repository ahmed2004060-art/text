package com.local.ortfacetest;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
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
    private static final int REQ_HUMANSEG = 6;
    private static final int REQ_YOLOX = 7;
    private static final int REQ_PERSON = 8;
    private static final int REQ_PALM = 9;
    private static final int REQ_HAND = 10;
    private static final int REQ_POSE = 11;

    private static final int GRAY = UiStyle.MUTED;
    private static final int GREEN = UiStyle.OK_INK;
    private static final int RED = UiStyle.ERR;

    private interface Job {
        ExtraResult run(Bitmap src, File model) throws Exception;
    }

    private final class Panel {
        final String fileName;
        final int request;
        final boolean auto;        // النماذج التي تقبل ترتيب NCHW أو NHWC
        final boolean needsYuNet;  // تحتاج نموذج YuNet الأصلي جاهزاً
        final String[] requires;   // ملفات نماذج إضافية أخرى لا بد من استيرادها
        String title;
        String hint;
        String runText;
        Job job;
        TextView status;
        Button importBtn;
        Button runBtn;

        Panel(String fileName, int request, boolean auto, boolean needsYuNet, String... requires) {
            this.fileName = fileName;
            this.request = request;
            this.auto = auto;
            this.needsYuNet = needsYuNet;
            this.requires = requires;
        }

        File file() {
            return new File(host.modelsDir(), fileName);
        }
    }

    private final ExtraHost host;
    private final Activity act;
    private final java.util.List<Panel> panels = new java.util.ArrayList<>();

    ExtraModels(ExtraHost host) {
        this.host = host;
        this.act = host.activity();
        registerPanels();
    }

    // ------------------------------------------------------------------ تسجيل النماذج (بلا أي بناء للشاشة)

    private void registerPanels() {
        // 1) SFace
        register(new Panel(SFaceModel.FILE, REQ_SFACE, false, true),
                SFaceModel.TITLE, SFaceModel.HINT, "تشغيل SFace", new Job() {
                    @Override
                    public ExtraResult run(Bitmap src, File model) throws Exception {
                        return SFaceModel.run(host, src, model);
                    }
                });

        // 2) MobileFaceNet
        register(new Panel(ExpressionModel.FILE, REQ_EXPRESSION, false, true),
                ExpressionModel.TITLE, ExpressionModel.HINT, "تشغيل MobileFaceNet", new Job() {
                    @Override
                    public ExtraResult run(Bitmap src, File model) throws Exception {
                        return ExpressionModel.run(host, src, model);
                    }
                });

        // 3) PPHumanSeg
        register(new Panel(PpHumanSegModel.FILE, REQ_HUMANSEG, true, false),
                PpHumanSegModel.TITLE, PpHumanSegModel.HINT, "تشغيل PPHumanSeg", new Job() {
                    @Override
                    public ExtraResult run(Bitmap src, File model) throws Exception {
                        return PpHumanSegModel.run(host, src, model);
                    }
                });

        // 4) YOLOX
        register(new Panel(YoloxModel.FILE, REQ_YOLOX, true, false),
                YoloxModel.TITLE, YoloxModel.HINT, "تشغيل YOLOX", new Job() {
                    @Override
                    public ExtraResult run(Bitmap src, File model) throws Exception {
                        return YoloxModel.run(host, src, model);
                    }
                });

        // 5) MediaPipe اكتشاف الأشخاص
        register(new Panel(PersonDetModel.FILE, REQ_PERSON, true, false),
                PersonDetModel.TITLE, PersonDetModel.HINT, "تشغيل اكتشاف الأشخاص", new Job() {
                    @Override
                    public ExtraResult run(Bitmap src, File model) throws Exception {
                        return PersonDetModel.run(host, src, model);
                    }
                });

        // 6) MediaPipe اكتشاف الكفوف
        register(new Panel(PalmDetModel.FILE, REQ_PALM, true, false),
                PalmDetModel.TITLE, PalmDetModel.HINT, "تشغيل اكتشاف الكفوف", new Job() {
                    @Override
                    public ExtraResult run(Bitmap src, File model) throws Exception {
                        return PalmDetModel.run(host, src, model);
                    }
                });

        // 7) MediaPipe وضعية اليد (يحتاج اكتشاف الكفوف)
        register(new Panel(HandPoseModel.FILE, REQ_HAND, true, false, PalmDetModel.FILE),
                HandPoseModel.TITLE, HandPoseModel.HINT, "تشغيل وضعية اليد", new Job() {
                    @Override
                    public ExtraResult run(Bitmap src, File model) throws Exception {
                        return HandPoseModel.run(host, src, model);
                    }
                });

        // 8) MediaPipe وضعية الجسم (يحتاج اكتشاف الأشخاص)
        register(new Panel(PoseModel.FILE, REQ_POSE, true, false, PersonDetModel.FILE),
                PoseModel.TITLE, PoseModel.HINT, "تشغيل وضعية الجسم", new Job() {
                    @Override
                    public ExtraResult run(Bitmap src, File model) throws Exception {
                        return PoseModel.run(host, src, model);
                    }
                });
    }

    private void register(Panel p, String title, String hint, String runText, Job job) {
        p.title = title;
        p.hint = hint;
        p.runText = runText;
        p.job = job;
        panels.add(p);
    }

    // ------------------------------------------------------------------ الشريط العلوي: استيراد كل نموذج وحالته

    void buildImports(ImportBar bar) {
        for (Panel p : panels) buildImportItem(bar, p);
    }

    private void buildImportItem(ImportBar bar, final Panel p) {
        p.importBtn = new Button(act);
        p.importBtn.setText("استيراد النموذج من الهاتف");
        UiStyle.secondary(act, p.importBtn);
        p.importBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!host.busy()) pick(p.request);
            }
        });

        p.status = new TextView(act);
        UiStyle.statusPill(act, p.status, 8);
        File f = p.file();
        if (f.isFile() && f.length() > 0) {
            setStatus(p, "الحالة: محفوظ — " + (f.length() / 1024) + " كيلوبايت", GREEN);
        } else {
            setStatus(p, "الحالة: غير مستورد", GRAY);
        }
        bar.add(p.title, p.importBtn, p.status);
    }

    // ------------------------------------------------------------------ قسم 5: النماذج الإضافية (التشغيل)

    void buildRunCard(LinearLayout root) {
        LinearLayout box = UiStyle.card(act, root);
        UiStyle.cardHead(act, box, 5, "النماذج الإضافية");

        HorizontalScrollView scroll = new HorizontalScrollView(act);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT, HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        box.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        for (Panel p : panels) buildRunItem(row, p);
    }

    // عنوان ووصف ثم زر التشغيل مثبّت أسفل العنصر ليتساوى مع بقية الأزرار
    private void buildRunItem(LinearLayout row, final Panel p) {
        LinearLayout item = new LinearLayout(act);
        item.setOrientation(LinearLayout.VERTICAL);

        TextView title = label(p.title, 13, true, UiStyle.INK);
        title.setTypeface(UiStyle.medium());
        title.setTextDirection(View.TEXT_DIRECTION_RTL);
        title.setGravity(android.view.Gravity.START);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.bottomMargin = dp(6);
        item.addView(title, tp);

        TextView hint = label(p.hint, 13, false, UiStyle.MUTED);
        hint.setTextDirection(View.TEXT_DIRECTION_RTL);
        hint.setGravity(android.view.Gravity.START);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(2);
        hp.bottomMargin = dp(8);
        item.addView(hint, hp);

        item.addView(new View(act), new LinearLayout.LayoutParams(0, 0, 1f));

        p.runBtn = new Button(act);
        p.runBtn.setText(p.runText);
        UiStyle.secondary(act, p.runBtn);
        UiStyle.addButton(act, item, p.runBtn, 0);
        p.runBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!host.busy()) launch(p, p.job);
            }
        });

        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(230), LinearLayout.LayoutParams.MATCH_PARENT);
        if (row.getChildCount() > 0) ip.setMarginStart(dp(12));
        row.addView(item, ip);
    }

    // يُستدعى كلما تغيّرت حالة الأزرار في الشاشة
    void refresh() {
        for (Panel p : panels) refreshPanel(p);
    }

    private void refreshPanel(Panel p) {
        if (p == null || p.importBtn == null || p.runBtn == null) return;
        File f = p.file();
        boolean ready = host.engineReady() && host.image() != null && f.isFile() && f.length() > 0;
        if (ready && p.needsYuNet && !host.yunetReady()) ready = false;
        for (String other : p.requires) {
            File o = new File(host.modelsDir(), other);
            if (!o.isFile() || o.length() == 0) ready = false;
        }
        p.runBtn.setEnabled(ready);
    }

    // ------------------------------------------------------------------ اختيار الملف

    private void pick(int code) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        Panel pp = find(code);
        ActivityLog.info(pp == null ? "النماذج الإضافية" : pp.title, "فتح مستعرض الملفات", "طلب المستخدم اختيار ملف النموذج");
        try {
            act.startActivityForResult(intent, code);
        } catch (Exception e) {
            ActivityLog.failed(pp == null ? "النماذج الإضافية" : pp.title, "فتح مستعرض الملفات", -1,
                    "تعذر فتح مستعرض الملفات: " + e.getMessage());
            Panel p = find(code);
            if (p != null) setStatus(p, "الحالة: فشل — تعذر فتح مستعرض الملفات: " + e.getMessage(), RED);
        }
    }

    private Panel find(int code) {
        for (Panel p : panels) if (p.request == code) return p;
        return null;
    }

    // true إذا كان الطلب يخص النماذج الإضافية
    boolean onResult(int requestCode, Uri uri) {
        Panel p = find(requestCode);
        if (p == null) return false;
        importModel(p, uri);
        return true;
    }

    // ------------------------------------------------------------------ الاستيراد

    private void importModel(final Panel p, final Uri uri) {
        if (!host.engineReady()) {
            setStatus(p, "الحالة: فشل — استورد المحرك أولاً حتى أتأكد من سلامة النموذج", RED);
            ActivityLog.failed(p.title, "استيراد النموذج", -1, "رُفض الاستيراد لأن المحرك غير جاهز");
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
                    String bad = validate(p, tmp);
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
                if (color == GREEN) ActivityLog.ok(p.title, "استيراد النموذج", -1, message);
                else ActivityLog.failed(p.title, "استيراد النموذج", -1, message);
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
    private String validate(Panel p, File f) {
        if (p.auto) return validateAuto(f);
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

    // للنماذج التي تقبل ترتيب NCHW أو NHWC: يكفي أن تُحمَّل وتكون مدخلاتها صورة بثلاث قنوات ولها مخرج واحد على الأقل
    private String validateAuto(File f) {
        try {
            AutoSession s = AutoSession.load(f, 1);
            if (s.outputs < 1) return "النموذج بلا مخرجات";
            return null;
        } catch (Throwable t) {
            return reason(t);
        }
    }

    // ------------------------------------------------------------------ التشغيل

    private void launch(final Panel p, final Job job) {
        final Bitmap src = host.image();
        if (src == null || !host.engineReady() || (p.needsYuNet && !host.yunetReady())) return;
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
                if (err[0] != null) {
                    ActivityLog.failed(p.title, "تشغيل التحليل", -1, "فشل في مرحلة النموذج: " + err[0]);
                } else {
                    String t = res[0].text == null ? "" : res[0].text.replace('\n', ' ');
                    ActivityLog.ok(p.title, "تشغيل التحليل", -1,
                            "اكتمل التشغيل بدون خطأ | الصورة " + src.getWidth() + "×"
                                    + src.getHeight() + " | النتيجة: " + t);
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
        UiStyle.status(p.status, text, color);
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
        return t;
    }
}
