package com.local.ortfacetest;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// فرع MobileFaceNet: تعبيرات الوجه. سبعة تعبيرات لكل وجه
final class ExpressionModel {
    static final String TITLE = "MobileFaceNet — تعبيرات الوجه";
    static final String HINT = "يُظهر تعبير كل وجه في الصورة (يحتاج نموذج YuNet جاهزاً)";
    static final String FILE = "expression.onnx";

    private static final int CLASSES = 7;
    private static final int MAX_FACES = 8;
    // ترتيب مخرجات النموذج: Angry, Disgust, Fearful, Happy, Neutral, Sad, Surprised
    private static final String[] LABELS = {"غضب", "اشمئزاز", "خوف", "سعادة", "حياد", "حزن", "دهشة"};

    private ExpressionModel() {
    }

    static ExtraResult run(ExtraHost host, Bitmap src, File model) throws Exception {
        long t0 = System.nanoTime();
        List<float[]> faces = host.detectFaces(src);
        if (faces.isEmpty()) return new ExtraResult("لم يُكتشف أي وجه في الصورة", null);
        if (faces.size() > MAX_FACES) faces = new ArrayList<>(faces.subList(0, MAX_FACES));

        String r = NativeBridge.loadModel(model.getAbsolutePath());
        if (!r.startsWith("OK|")) throw new IOException("نموذج التعبيرات غير صالح: " + r.replaceFirst("^ERR\\|", ""));

        int[] best = new int[faces.size()];
        float[] conf = new float[faces.size()];
        for (int i = 0; i < faces.size(); i++) {
            float[] probs = classify(src, faces.get(i));
            int k = 0;
            for (int c = 1; c < CLASSES; c++) if (probs[c] > probs[k]) k = c;
            best[i] = k;
            conf[i] = probs[k];
        }
        double ms = (System.nanoTime() - t0) / 1_000_000.0;

        StringBuilder sb = new StringBuilder();
        sb.append("عدد الوجوه: ").append(faces.size()).append('\n');
        for (int i = 0; i < faces.size(); i++) {
            sb.append("الوجه ").append(i + 1).append(": ").append(LABELS[best[i]])
                    .append(" (").append(Math.round(conf[i] * 100f)).append("%)\n");
        }
        sb.append("زمن التنفيذ: ").append(String.format(Locale.US, "%.1f", ms)).append(" مللي ثانية");
        return new ExtraResult(sb.toString(), draw(src, faces, best, conf));
    }

    // احتمالات التعبيرات السبعة لوجه واحد: ألوان RGB وقيم (القيمة/255 − 0.5) / 0.5
    private static float[] classify(Bitmap src, float[] face) throws Exception {
        Bitmap aligned = FaceAlign.align(src, face);
        float[] input = FaceAlign.toInput(aligned, true);
        aligned.recycle();
        float[][] outs = NativeBridge.runModel(input, FaceAlign.SIZE, FaceAlign.SIZE);
        if (outs == null) throw new IOException(NativeBridge.lastError());
        if (outs.length < 1 || outs[0].length != CLASSES) {
            int len = outs.length < 1 ? 0 : outs[0].length;
            throw new IOException("هذا ليس نموذج التعبيرات المتوقع (حجم المخرج " + len + " بدلاً من " + CLASSES + ")");
        }
        float[] logits = outs[0];
        float max = logits[0];
        for (float v : logits) if (v > max) max = v;
        double sum = 0;
        float[] p = new float[CLASSES];
        for (int i = 0; i < CLASSES; i++) {
            p[i] = (float) Math.exp(logits[i] - max);
            sum += p[i];
        }
        for (int i = 0; i < CLASSES; i++) p[i] = (float) (p[i] / sum);
        return p;
    }

    private static Bitmap draw(Bitmap src, List<float[]> faces, int[] best, float[] conf) {
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(out);
        float unit = Math.max(out.getWidth(), out.getHeight()) / 250f;
        Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
        box.setStyle(Paint.Style.STROKE);
        box.setColor(Color.rgb(0, 220, 60));
        box.setStrokeWidth(Math.max(3f, unit));
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.YELLOW);
        float size = Math.max(24f, unit * 7f);
        text.setTextSize(size);
        text.setShadowLayer(4f, 0f, 0f, Color.BLACK);
        for (int i = 0; i < faces.size(); i++) {
            float[] f = faces.get(i);
            canvas.drawRect(f[0], f[1], f[2], f[3], box);
            String label = LABELS[best[i]] + " " + Math.round(conf[i] * 100f) + "%";
            float y = f[1] - size * 0.3f;
            if (y < size) y = f[1] + size;
            canvas.drawText(label, f[0], y, text);
        }
        return out;
    }
}
