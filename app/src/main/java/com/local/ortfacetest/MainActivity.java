package com.local.ortfacetest;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {

    private static final int REQ_ENGINE = 1;
    private static final int REQ_MODEL = 2;
    private static final int REQ_IMAGE = 3;

    private static final int GRAY = 0xFF6B7280;
    private static final int GREEN = 0xFF15803D;
    private static final int RED = 0xFFB91C1C;

    private static final String LIB_NAME = "libonnxruntime.so";
    private static final int[] STRIDES = {8, 16, 32};
    private static final String[] KINDS = {"cls", "obj", "bbox", "kps"};
    private static final int DYNAMIC_SIZE = 640;
    private static final float SCORE_THRESHOLD = 0.7f;
    private static final float NMS_THRESHOLD = 0.3f;
    private static final int MAX_IMAGE_SIDE = 4096;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView engineStatus;
    private TextView modelStatus;
    private TextView imageStatus;
    private TextView resultView;
    private Button engineBtn;
    private Button modelBtn;
    private Button imageBtn;
    private Button runBtn;
    private Button deleteBtn;
    private ImageView imageView;

    private volatile boolean busy;
    private volatile boolean engineReady;
    private volatile boolean modelReady;
    private volatile Bitmap sourceBitmap;
    private volatile int fixedW;
    private volatile int fixedH;
    private volatile int[] outIndex;
    private ExtraModels extras;

    private static final class Face {
        float x1, y1, x2, y2, score;
        final float[] lm = new float[10];
    }

    private static final class StageException extends Exception {
        final String stage;

        StageException(String stage, String message) {
            super(message);
            this.stage = stage;
        }
    }

    // ------------------------------------------------------------------ الشاشة

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        restoreSaved();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        return b;
    }

    private LinearLayout section(LinearLayout parent, String title) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.WHITE);
        box.setPadding(dp(12), dp(8), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        parent.addView(box, lp);
        box.addView(label(title, 18, true, Color.BLACK));
        return box;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF3F4F6);
        root.setPadding(dp(12), dp(16), dp(12), dp(24));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);

        TextView title = label("اختبار المحرك مع نموذج YuNet", 22, true, Color.BLACK);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        // 1) المحرك
        LinearLayout engineBox = section(root, "1. المحرك");
        engineBtn = button("استيراد المحرك من الهاتف");
        engineBox.addView(engineBtn);
        engineStatus = label("الحالة: غير مستورد", 15, false, GRAY);
        engineBox.addView(engineStatus);
        engineBtn.setOnClickListener(v -> pickFile(REQ_ENGINE, "*/*"));

        // 2) النموذج
        LinearLayout modelBox = section(root, "2. النموذج");
        modelBtn = button("استيراد النموذج من الهاتف");
        modelBox.addView(modelBtn);
        modelStatus = label("الحالة: غير مستورد", 15, false, GRAY);
        modelBox.addView(modelStatus);
        modelBtn.setOnClickListener(v -> pickFile(REQ_MODEL, "*/*"));

        // 3) الصورة
        LinearLayout imageBox = section(root, "3. الصورة");
        imageBtn = button("اختيار صورة من الهاتف");
        imageBox.addView(imageBtn);
        imageStatus = label("لم تُختر صورة", 15, false, GRAY);
        imageBox.addView(imageStatus);
        imageView = new ImageView(this);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setVisibility(View.GONE);
        imageBox.addView(imageView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        imageBtn.setOnClickListener(v -> pickFile(REQ_IMAGE, "image/*"));

        // التشغيل والنتيجة
        LinearLayout runBox = section(root, "4. التشغيل");
        runBtn = button("تشغيل التحليل");
        runBox.addView(runBtn);
        resultView = label("النتيجة ستظهر هنا", 16, false, Color.BLACK);
        resultView.setTextIsSelectable(true);
        runBox.addView(resultView);
        runBtn.setOnClickListener(v -> runDetection());

        // الحذف
        deleteBtn = button("حذف المحرك والنموذج");
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(16);
        root.addView(deleteBtn, dlp);
        deleteBtn.setOnClickListener(v -> deleteAll());

        extras = new ExtraModels(extraHost());
        extras.build(root, deleteBtn);

        setContentView(scroll);
        updateButtons();
    }

    private void setStatus(TextView view, String text, int color) {
        view.setText(text);
        view.setTextColor(color);
    }

    private void updateButtons() {
        engineBtn.setEnabled(!busy);
        modelBtn.setEnabled(!busy);
        imageBtn.setEnabled(!busy);
        deleteBtn.setEnabled(!busy);
        runBtn.setEnabled(!busy && engineReady && modelReady && sourceBitmap != null);
        if (extras != null) extras.refresh();
    }

    private void setBusy(boolean value) {
        busy = value;
        updateButtons();
    }

    // ------------------------------------------------------------------ اختيار الملفات

    private void pickFile(int code, String mime) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        try {
            startActivityForResult(intent, code);
        } catch (Exception e) {
            String m = "تعذر فتح مستعرض الملفات: " + e.getMessage();
            if (code == REQ_ENGINE) setStatus(engineStatus, "الحالة: فشل — " + m, RED);
            else if (code == REQ_MODEL) setStatus(modelStatus, "الحالة: فشل — " + m, RED);
            else setStatus(imageStatus, "فشل في مرحلة الصورة: " + m, RED);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (extras != null && extras.onResult(requestCode, uri)) return;
        if (requestCode == REQ_ENGINE) importEngine(uri);
        else if (requestCode == REQ_MODEL) importModel(uri);
        else if (requestCode == REQ_IMAGE) importImage(uri);
    }

    // ------------------------------------------------------------------ مسارات التخزين

    private File engineDir() {
        return new File(getFilesDir(), "engine");
    }

    private File modelDir() {
        return new File(getFilesDir(), "model");
    }

    private File modelFile() {
        return new File(modelDir(), "model.onnx");
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteRecursive(c);
        f.delete();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    }

    private static String errText(String r) {
        return r.startsWith("ERR|") ? r.substring(4) : r;
    }

    private static String reason(Throwable t) {
        if (t instanceof OutOfMemoryError) return "الذاكرة لا تكفي";
        String m = t.getMessage();
        return (m == null || m.isEmpty()) ? t.getClass().getSimpleName() : m;
    }

    // ------------------------------------------------------------------ حالات المحرك والنموذج (من أي خيط)

    private void uiEngine(final boolean ready, final String text) {
        runOnUiThread(() -> {
            engineReady = ready;
            setStatus(engineStatus, "الحالة: " + text, ready ? GREEN : RED);
            if (!ready) {
                modelReady = false;
                if (modelFile().exists()) {
                    setStatus(modelStatus, "الحالة: محفوظ، لكنه يحتاج محركاً جاهزاً", GRAY);
                }
            }
            updateButtons();
        });
    }

    private void uiModel(final boolean ready, final String text, final int w, final int h, final int[] idx) {
        runOnUiThread(() -> {
            modelReady = ready;
            fixedW = w;
            fixedH = h;
            outIndex = idx;
            setStatus(modelStatus, "الحالة: " + text, ready ? GREEN : RED);
            updateButtons();
        });
    }

    // ------------------------------------------------------------------ تحميل المحرك والنموذج (على خيط التنفيذ)

    private boolean loadEngineNow(File lib) {
        if (!NativeBridge.loaded) {
            uiEngine(false, "فشل — جسر التشغيل الداخلي لم يُحمَّل: " + NativeBridge.loadError);
            return false;
        }
        String r = NativeBridge.loadEngine(lib.getAbsolutePath());
        if (r.startsWith("OK|")) {
            uiEngine(true, "جاهز — الإصدار " + r.substring(3));
            return true;
        }
        uiEngine(false, "فشل — " + errText(r));
        return false;
    }

    private boolean loadModelNow(File model) {
        String r = NativeBridge.loadModel(model.getAbsolutePath());
        if (!r.startsWith("OK|")) {
            uiModel(false, "فشل — الملف ليس نموذجاً سليماً: " + errText(r), 0, 0, null);
            return false;
        }
        String[] p = r.split("\\|", -1);
        String csv = p.length > 3 ? p[3] : "";
        int count = csv.isEmpty() ? 0 : csv.split(",").length;
        int[] idx = mapOutputs(csv);
        if (idx == null) {
            uiModel(false, "فشل — هذا ليس نموذج YuNet المتوقع (عدد المخرجات: " + count + ")", 0, 0, null);
            return false;
        }
        int w = 0, h = 0;
        try {
            w = Integer.parseInt(p[1]);
            h = Integer.parseInt(p[2]);
        } catch (Exception ignored) {
        }
        if (w <= 0 || h <= 0) {
            w = 0;
            h = 0;
        }
        long kb = model.length() / 1024;
        uiModel(true, "جاهز — " + kb + " كيلوبايت", w, h, idx);
        return true;
    }

    // يرتب المخرجات: cls×3 ثم obj×3 ثم bbox×3 ثم kps×3
    private int[] mapOutputs(String csv) {
        String[] names = csv.isEmpty() ? new String[0] : csv.split(",");
        int[] idx = new int[12];
        boolean byName = true;
        for (int k = 0; k < 4 && byName; k++) {
            for (int s = 0; s < 3; s++) {
                int found = -1;
                String want = KINDS[k] + "_" + STRIDES[s];
                for (int i = 0; i < names.length; i++) {
                    if (names[i].equals(want)) found = i;
                }
                if (found < 0) {
                    byName = false;
                    break;
                }
                idx[k * 3 + s] = found;
            }
        }
        if (byName) return idx;
        if (names.length == 12) {
            for (int i = 0; i < 12; i++) idx[i] = i;
            return idx;
        }
        return null;
    }

    // ------------------------------------------------------------------ عند فتح التطبيق: استرجاع المحفوظ

    private void restoreSaved() {
        final File lib = new File(engineDir(), LIB_NAME);
        if (!lib.exists()) {
            if (modelFile().exists()) {
                setStatus(modelStatus, "الحالة: محفوظ، لكنه يحتاج محركاً جاهزاً", GRAY);
            }
            return;
        }
        setBusy(true);
        setStatus(engineStatus, "الحالة: جارٍ التحميل...", GRAY);
        executor.execute(() -> {
            try {
                if (loadEngineNow(lib)) {
                    File m = modelFile();
                    if (m.exists()) loadModelNow(m);
                }
            } catch (Throwable t) {
                uiEngine(false, "فشل — " + reason(t));
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    // ------------------------------------------------------------------ استيراد المحرك

    private void importEngine(final Uri uri) {
        setBusy(true);
        engineReady = false;
        modelReady = false;
        setStatus(engineStatus, "الحالة: جارٍ الاستيراد...", GRAY);
        if (modelFile().exists()) setStatus(modelStatus, "الحالة: بانتظار المحرك...", GRAY);
        executor.execute(() -> {
            try {
                if (NativeBridge.loaded) NativeBridge.unloadAll();
                File dir = engineDir();
                deleteRecursive(dir);
                if (!dir.mkdirs()) throw new IOException("تعذر إنشاء مجلد التخزين");
                boolean found = extractEngine(uri, dir);
                if (!found) {
                    deleteRecursive(dir);
                    uiEngine(false, "فشل — الملف ليس محركاً صالحاً (لا توجد فيه مكتبة المحرك لمعمارية arm64-v8a)");
                } else if (loadEngineNow(new File(dir, LIB_NAME))) {
                    File m = modelFile();
                    if (m.exists()) loadModelNow(m);
                }
            } catch (Throwable t) {
                uiEngine(false, "فشل — " + reason(t));
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    private boolean extractEngine(Uri uri, File dir) throws IOException {
        boolean found = false;
        try (InputStream raw = getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IOException("تعذر قراءة الملف");
            try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(raw, 64 * 1024))) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    String n = e.getName();
                    String target = null;
                    if (n.endsWith("arm64-v8a/" + LIB_NAME)) target = LIB_NAME;
                    else if (n.endsWith("arm64-v8a/libc++_shared.so")) target = "libc++_shared.so";
                    if (target == null) continue;
                    File out = new File(dir, target);
                    try (FileOutputStream fo = new FileOutputStream(out)) {
                        copy(zin, fo);
                    }
                    out.setReadOnly();
                    if (LIB_NAME.equals(target)) found = true;
                }
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ استيراد النموذج

    private void importModel(final Uri uri) {
        if (!engineReady) {
            setStatus(modelStatus, "الحالة: فشل — استورد المحرك أولاً حتى أتأكد من سلامة النموذج", RED);
            return;
        }
        setBusy(true);
        modelReady = false;
        setStatus(modelStatus, "الحالة: جارٍ الاستيراد...", GRAY);
        executor.execute(() -> {
            File f = modelFile();
            try {
                File dir = modelDir();
                deleteRecursive(dir);
                if (!dir.mkdirs()) throw new IOException("تعذر إنشاء مجلد التخزين");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(f)) {
                    if (in == null) throw new IOException("تعذر قراءة الملف");
                    copy(in, out);
                }
                if (f.length() == 0) throw new IOException("الملف فارغ");
                if (!loadModelNow(f)) deleteRecursive(dir);
            } catch (Throwable t) {
                deleteRecursive(modelDir());
                uiModel(false, "فشل — " + reason(t), 0, 0, null);
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    // ------------------------------------------------------------------ اختيار الصورة

    private void importImage(final Uri uri) {
        setBusy(true);
        setStatus(imageStatus, "جارٍ فتح الصورة...", GRAY);
        executor.execute(() -> {
            try {
                final Bitmap bmp = decodeBitmap(uri);
                runOnUiThread(() -> {
                    sourceBitmap = bmp;
                    imageView.setImageBitmap(bmp);
                    imageView.setVisibility(View.VISIBLE);
                    setStatus(imageStatus, "الصورة: " + bmp.getWidth() + "×" + bmp.getHeight(), GREEN);
                    resultView.setText("النتيجة ستظهر هنا");
                });
            } catch (final Throwable t) {
                runOnUiThread(() -> setStatus(imageStatus, "فشل في مرحلة الصورة: " + reason(t), RED));
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    private Bitmap decodeBitmap(Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("تعذر قراءة الصورة");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("صيغة الصورة غير مدعومة");

        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("تعذر قراءة الصورة");
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new IOException("صيغة الصورة غير مدعومة");

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) {
                orientation = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (Throwable ignored) {
        }
        Matrix m = new Matrix();
        boolean changed = true;
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                m.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                m.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                m.setRotate(180);
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                m.setRotate(90);
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                m.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                m.setRotate(-90);
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                m.setRotate(-90);
                break;
            default:
                changed = false;
        }
        if (changed) {
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (rotated != bmp) bmp.recycle();
            bmp = rotated;
        }
        return bmp;
    }

    // ------------------------------------------------------------------ التشغيل

    private void runDetection() {
        final Bitmap src = sourceBitmap;
        if (src == null || !engineReady || !modelReady) return;
        final int fw = fixedW;
        final int fh = fixedH;
        final int[] idx = outIndex;
        setBusy(true);
        resultView.setText("جارٍ التحليل...");
        resultView.setTextColor(Color.BLACK);
        executor.execute(() -> {
            try {
                long t0 = System.nanoTime();

                // تجهيز الصورة
                int w = src.getWidth();
                int h = src.getHeight();
                int tw, th;
                float scale;
                if (fw > 0 && fh > 0) {
                    tw = fw;
                    th = fh;
                    scale = Math.min(tw / (float) w, th / (float) h);
                } else {
                    scale = DYNAMIC_SIZE / (float) Math.max(w, h);
                    tw = ((Math.round(w * scale) + 31) / 32) * 32;
                    th = ((Math.round(h * scale) + 31) / 32) * 32;
                }
                int rw = Math.max(1, Math.min(tw, Math.round(w * scale)));
                int rh = Math.max(1, Math.min(th, Math.round(h * scale)));
                float[] input;
                try {
                    input = prepareInput(src, rw, rh, tw, th);
                } catch (Throwable t) {
                    throw new StageException("الصورة", reason(t));
                }

                // تشغيل النموذج بالمحرك
                float[][] outs = NativeBridge.runModel(input, tw, th);
                if (outs == null) throw new StageException("النموذج", NativeBridge.lastError());

                // قراءة النتيجة
                List<Face> faces = decode(outs, idx, tw, th, scale, w, h);
                long t1 = System.nanoTime();
                double ms = (t1 - t0) / 1_000_000.0;

                final Bitmap annotated;
                try {
                    annotated = draw(src, faces);
                } catch (Throwable t) {
                    throw new StageException("الصورة", reason(t));
                }
                final int count = faces.size();
                final String text = "عدد الوجوه: " + count + "\nزمن التنفيذ: "
                        + String.format(java.util.Locale.US, "%.1f", ms) + " مللي ثانية";
                runOnUiThread(() -> {
                    imageView.setImageBitmap(annotated);
                    resultView.setText(text);
                    resultView.setTextColor(Color.BLACK);
                });
            } catch (final StageException e) {
                runOnUiThread(() -> {
                    resultView.setText("فشل في مرحلة " + e.stage + ": " + e.getMessage());
                    resultView.setTextColor(RED);
                });
            } catch (final Throwable t) {
                runOnUiThread(() -> {
                    resultView.setText("فشل في مرحلة النموذج: " + reason(t));
                    resultView.setTextColor(RED);
                });
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    // الإدخال: ألوان بترتيب أزرق ثم أخضر ثم أحمر، وقيم من 0 إلى 255، والصورة في الزاوية العلوية اليسرى
    private float[] prepareInput(Bitmap src, int rw, int rh, int tw, int th) {
        Bitmap scaled = Bitmap.createScaledBitmap(src, rw, rh, true);
        int[] px = new int[rw * rh];
        scaled.getPixels(px, 0, rw, 0, 0, rw, rh);
        if (scaled != src) scaled.recycle();
        int plane = tw * th;
        float[] data = new float[3 * plane];
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                int c = px[y * rw + x];
                int o = y * tw + x;
                data[o] = c & 0xFF;
                data[plane + o] = (c >> 8) & 0xFF;
                data[2 * plane + o] = (c >> 16) & 0xFF;
            }
        }
        return data;
    }

    private List<Face> decode(float[][] outs, int[] idx, int tw, int th, float scale, int imgW, int imgH)
            throws StageException {
        List<Face> all = new ArrayList<>();
        for (int s = 0; s < 3; s++) {
            int stride = STRIDES[s];
            int cols = tw / stride;
            int rows = th / stride;
            int cells = rows * cols;
            float[] cls = outs[idx[s]];
            float[] obj = outs[idx[3 + s]];
            float[] bbox = outs[idx[6 + s]];
            float[] kps = outs[idx[9 + s]];
            if (cls.length < cells || obj.length < cells || bbox.length < cells * 4 || kps.length < cells * 10) {
                throw new StageException("النموذج", "مخرجات النموذج لا تطابق حجم الصورة المرسلة");
            }
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int i = r * cols + c;
                    float cs = Math.max(0f, Math.min(1f, cls[i]));
                    float os = Math.max(0f, Math.min(1f, obj[i]));
                    float score = (float) Math.sqrt(cs * os);
                    if (score < SCORE_THRESHOLD) continue;
                    float cx = (c + bbox[i * 4]) * stride;
                    float cy = (r + bbox[i * 4 + 1]) * stride;
                    float bw = (float) Math.exp(bbox[i * 4 + 2]) * stride;
                    float bh = (float) Math.exp(bbox[i * 4 + 3]) * stride;
                    Face f = new Face();
                    f.score = score;
                    f.x1 = clamp((cx - bw / 2f) / scale, 0, imgW);
                    f.y1 = clamp((cy - bh / 2f) / scale, 0, imgH);
                    f.x2 = clamp((cx + bw / 2f) / scale, 0, imgW);
                    f.y2 = clamp((cy + bh / 2f) / scale, 0, imgH);
                    for (int n = 0; n < 5; n++) {
                        f.lm[2 * n] = clamp((kps[i * 10 + 2 * n] + c) * stride / scale, 0, imgW);
                        f.lm[2 * n + 1] = clamp((kps[i * 10 + 2 * n + 1] + r) * stride / scale, 0, imgH);
                    }
                    if (f.x2 - f.x1 > 1 && f.y2 - f.y1 > 1) all.add(f);
                }
            }
        }
        Collections.sort(all, new Comparator<Face>() {
            @Override
            public int compare(Face a, Face b) {
                return Float.compare(b.score, a.score);
            }
        });
        List<Face> kept = new ArrayList<>();
        for (Face f : all) {
            boolean keep = true;
            for (Face k : kept) {
                if (iou(f, k) > NMS_THRESHOLD) {
                    keep = false;
                    break;
                }
            }
            if (keep) kept.add(f);
        }
        return kept;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float iou(Face a, Face b) {
        float ix1 = Math.max(a.x1, b.x1);
        float iy1 = Math.max(a.y1, b.y1);
        float ix2 = Math.min(a.x2, b.x2);
        float iy2 = Math.min(a.y2, b.y2);
        float iw = Math.max(0f, ix2 - ix1);
        float ih = Math.max(0f, iy2 - iy1);
        float inter = iw * ih;
        float areaA = (a.x2 - a.x1) * (a.y2 - a.y1);
        float areaB = (b.x2 - b.x1) * (b.y2 - b.y1);
        float union = areaA + areaB - inter;
        return union <= 0 ? 0f : inter / union;
    }

    // يرسم مربعاً حول كل وجه ونقاطاً على ملامحه فوق نسخة من الصورة الأصلية
    private Bitmap draw(Bitmap src, List<Face> faces) {
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(out);
        float unit = Math.max(out.getWidth(), out.getHeight()) / 250f;
        Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
        box.setStyle(Paint.Style.STROKE);
        box.setColor(Color.rgb(0, 220, 60));
        box.setStrokeWidth(Math.max(3f, unit));
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(Color.RED);
        float radius = Math.max(3f, unit * 1.3f);
        for (Face f : faces) {
            canvas.drawRect(f.x1, f.y1, f.x2, f.y2, box);
            for (int n = 0; n < 5; n++) {
                canvas.drawCircle(f.lm[2 * n], f.lm[2 * n + 1], radius, dot);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ الحذف

    private void deleteAll() {
        setBusy(true);
        executor.execute(() -> {
            try {
                if (NativeBridge.loaded) NativeBridge.unloadAll();
                deleteRecursive(engineDir());
                deleteRecursive(modelDir());
            } catch (Throwable ignored) {
            }
            runOnUiThread(() -> {
                engineReady = false;
                modelReady = false;
                fixedW = 0;
                fixedH = 0;
                outIndex = null;
                setStatus(engineStatus, "الحالة: غير مستورد", GRAY);
                setStatus(modelStatus, "الحالة: غير مستورد", GRAY);
                setBusy(false);
            });
        });
    }

    // ------------------------------------------------------------------ ربط النماذج الإضافية (إضافة جديدة، لا تغيّر ما سبق)

    private ExtraHost extraHost() {
        return new ExtraHost() {
            @Override
            public Activity activity() {
                return MainActivity.this;
            }

            @Override
            public boolean engineReady() {
                return engineReady;
            }

            @Override
            public boolean yunetReady() {
                return modelReady;
            }

            @Override
            public Bitmap image() {
                return sourceBitmap;
            }

            @Override
            public boolean busy() {
                return busy;
            }

            @Override
            public void setBusy(boolean value) {
                MainActivity.this.setBusy(value);
            }

            @Override
            public void execute(Runnable task) {
                executor.execute(task);
            }

            @Override
            public List<float[]> detectFaces(Bitmap image) throws Exception {
                return detectFacesForExtras(image);
            }

            @Override
            public void restoreYuNet() {
                File m = modelFile();
                if (m.isFile()) NativeBridge.loadModel(m.getAbsolutePath());
            }

            @Override
            public File modelsDir() {
                return new File(getFilesDir(), "extra_models");
            }

            @Override
            public void showResult(String text, Bitmap annotated, boolean error) {
                if (annotated != null) imageView.setImageBitmap(annotated);
                resultView.setText(text);
                resultView.setTextColor(error ? RED : Color.BLACK);
            }
        };
    }

    // اكتشاف الوجوه بنموذج YuNet نفسه ودوال تجهيزه وقراءته الموجودة أعلاه، ليستفيد منها SFace وغيره
    private List<float[]> detectFacesForExtras(Bitmap src) throws Exception {
        File yf = modelFile();
        if (!yf.isFile()) throw new IOException("نموذج YuNet غير مستورد");
        String r = NativeBridge.loadModel(yf.getAbsolutePath());
        if (!r.startsWith("OK|")) throw new IOException("YuNet: " + errText(r));
        String[] p = r.split("\\|", -1);
        String csv = p.length > 3 ? p[3] : "";
        int[] idx = mapOutputs(csv);
        if (idx == null) throw new IOException("هذا ليس نموذج YuNet المتوقع");
        int fw = 0, fh = 0;
        try {
            fw = Integer.parseInt(p[1]);
            fh = Integer.parseInt(p[2]);
        } catch (Exception ignored) {
        }
        if (fw <= 0 || fh <= 0) {
            fw = 0;
            fh = 0;
        }

        int w = src.getWidth();
        int h = src.getHeight();
        int tw, th;
        float scale;
        if (fw > 0 && fh > 0) {
            tw = fw;
            th = fh;
            scale = Math.min(tw / (float) w, th / (float) h);
        } else {
            scale = DYNAMIC_SIZE / (float) Math.max(w, h);
            tw = ((Math.round(w * scale) + 31) / 32) * 32;
            th = ((Math.round(h * scale) + 31) / 32) * 32;
        }
        int rw = Math.max(1, Math.min(tw, Math.round(w * scale)));
        int rh = Math.max(1, Math.min(th, Math.round(h * scale)));
        float[] input = prepareInput(src, rw, rh, tw, th);
        float[][] outs = NativeBridge.runModel(input, tw, th);
        if (outs == null) throw new IOException(NativeBridge.lastError());

        List<Face> faces = decode(outs, idx, tw, th, scale, w, h);
        List<float[]> result = new ArrayList<>();
        for (Face f : faces) {
            float[] a = new float[15];
            a[0] = f.x1;
            a[1] = f.y1;
            a[2] = f.x2;
            a[3] = f.y2;
            a[4] = f.score;
            System.arraycopy(f.lm, 0, a, 5, 10);
            result.add(a);
        }
        return result;
    }
}
