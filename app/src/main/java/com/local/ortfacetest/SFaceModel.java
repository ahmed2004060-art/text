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

// فرع SFace: التعرف على الوجوه. يستخرج بصمة من 128 رقماً لكل وجه ويقارن الوجوه داخل الصورة
final class SFaceModel {
    static final String TITLE = "SFace — التعرف على الوجوه";
    static final String HINT = "يقارن الوجوه الموجودة في الصورة ببعضها (يحتاج نموذج YuNet جاهزاً)";
    static final String FILE = "sface.onnx";

    private static final int EMBEDDING_SIZE = 128;
    private static final int MAX_FACES = 8;
    private static final float SAME_PERSON = 0.363f; // حد التشابه بالجيب التمامي

    private SFaceModel() {
    }

    static ExtraResult run(ExtraHost host, Bitmap src, File model) throws Exception {
        long t0 = System.nanoTime();
        List<float[]> faces = host.detectFaces(src);
        if (faces.isEmpty()) return new ExtraResult("لم يُكتشف أي وجه في الصورة", null);
        if (faces.size() > MAX_FACES) faces = new ArrayList<>(faces.subList(0, MAX_FACES));

        String r = NativeBridge.loadModel(model.getAbsolutePath());
        if (!r.startsWith("OK|")) throw new IOException("نموذج SFace غير صالح: " + r.replaceFirst("^ERR\\|", ""));

        List<float[]> embeddings = new ArrayList<>();
        for (float[] face : faces) {
            embeddings.add(embed(src, face));
        }
        double ms = (System.nanoTime() - t0) / 1_000_000.0;

        StringBuilder sb = new StringBuilder();
        sb.append("عدد الوجوه: ").append(faces.size()).append('\n');
        if (faces.size() == 1) {
            sb.append("وجه واحد فقط، لا توجد مقارنة. استُخرجت بصمة من ")
                    .append(EMBEDDING_SIZE).append(" رقماً بنجاح.\n");
        } else {
            for (int i = 0; i < embeddings.size(); i++) {
                for (int j = i + 1; j < embeddings.size(); j++) {
                    float sim = dot(embeddings.get(i), embeddings.get(j));
                    sb.append("الوجه ").append(i + 1).append(" مع الوجه ").append(j + 1)
                            .append(": ").append(String.format(Locale.US, "%.2f", sim))
                            .append(sim >= SAME_PERSON ? " — على الأرجح نفس الشخص" : " — شخصان مختلفان")
                            .append('\n');
                }
            }
        }
        sb.append("زمن التنفيذ: ").append(String.format(Locale.US, "%.1f", ms)).append(" مللي ثانية");
        return new ExtraResult(sb.toString(), draw(src, faces));
    }

    // بصمة واحدة مطبَّعة الطول: ألوان RGB وقيم من 0 إلى 255
    private static float[] embed(Bitmap src, float[] face) throws Exception {
        Bitmap aligned = FaceAlign.align(src, face);
        float[] input = FaceAlign.toInput(aligned, false);
        aligned.recycle();
        float[][] outs = NativeBridge.runModel(input, FaceAlign.SIZE, FaceAlign.SIZE);
        if (outs == null) throw new IOException(NativeBridge.lastError());
        if (outs.length < 1 || outs[0].length != EMBEDDING_SIZE) {
            int len = outs.length < 1 ? 0 : outs[0].length;
            throw new IOException("هذا ليس نموذج SFace المتوقع (حجم المخرج " + len + " بدلاً من " + EMBEDDING_SIZE + ")");
        }
        float[] e = outs[0].clone();
        double norm = 0;
        for (float v : e) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm < 1e-9) throw new IOException("بصمة الوجه فارغة");
        for (int i = 0; i < e.length; i++) e[i] = (float) (e[i] / norm);
        return e;
    }

    private static float dot(float[] a, float[] b) {
        float s = 0f;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private static Bitmap draw(Bitmap src, List<float[]> faces) {
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(out);
        float unit = Math.max(out.getWidth(), out.getHeight()) / 250f;
        Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
        box.setStyle(Paint.Style.STROKE);
        box.setColor(Color.rgb(0, 220, 60));
        box.setStrokeWidth(Math.max(3f, unit));
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.YELLOW);
        text.setTextSize(Math.max(24f, unit * 7f));
        text.setShadowLayer(4f, 0f, 0f, Color.BLACK);
        for (int i = 0; i < faces.size(); i++) {
            float[] f = faces.get(i);
            canvas.drawRect(f[0], f[1], f[2], f[3], box);
            canvas.drawText(String.valueOf(i + 1), f[0] + unit, f[1] + Math.max(24f, unit * 7f), text);
        }
        return out;
    }
}
