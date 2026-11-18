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

// فرع MediaPipe وضعية الجسم: 33 نقطة لكل شخص. يحتاج نموذج اكتشاف الأشخاص أولاً
final class PoseModel {
    static final String TITLE = "MediaPipe — وضعية الجسم";
    static final String HINT = "يرسم هيكل الجسم بـ 33 نقطة (يحتاج نموذج اكتشاف الأشخاص مستورداً)";
    static final String FILE = "pose.onnx";

    private static final int DEFAULT_SIZE = 256;
    private static final int LANDMARKS = 33;
    private static final int VALUES = 5; // x, y, z, الظهور، الوجود
    private static final int LANDMARK_OUTPUT = 39 * VALUES;
    private static final float PRESENCE = 0.5f;
    private static final float VISIBLE = 0.5f;

    private static final int[][] BONES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 7}, {0, 4}, {4, 5}, {5, 6}, {6, 8}, {9, 10},
            {11, 12}, {11, 13}, {13, 15}, {15, 17}, {15, 19}, {15, 21}, {17, 19},
            {12, 14}, {14, 16}, {16, 18}, {16, 20}, {16, 22}, {18, 20},
            {11, 23}, {12, 24}, {23, 24}, {23, 25}, {24, 26}, {25, 27}, {26, 28},
            {27, 29}, {28, 30}, {29, 31}, {30, 32}, {27, 31}, {28, 32}
    };

    private static final class Body {
        final float[][] pts = new float[LANDMARKS][];
        final boolean[] visible = new boolean[LANDMARKS];
    }

    private PoseModel() {
    }

    static ExtraResult run(ExtraHost host, Bitmap src, File model) throws Exception {
        long t0 = System.nanoTime();
        File personFile = new File(host.modelsDir(), PersonDetModel.FILE);
        if (!personFile.isFile()) throw new IOException("استورد نموذج اكتشاف الأشخاص أولاً");

        // 1) اكتشاف الأشخاص
        List<MpDecode.Det> persons = PersonDetModel.detect(src, personFile);
        if (persons.isEmpty()) return new ExtraResult("لم يُكتشف أي شخص في الصورة", null);

        // 2) تحليل كل شخص
        AutoSession s = AutoSession.load(model, DEFAULT_SIZE);
        List<Body> bodies = new ArrayList<>();
        for (MpDecode.Det person : persons) {
            Body b = analyze(src, s, person);
            if (b != null) bodies.add(b);
        }
        double ms = (System.nanoTime() - t0) / 1_000_000.0;

        StringBuilder sb = new StringBuilder();
        sb.append("عدد الأجسام: ").append(bodies.size()).append('\n');
        sb.append("زمن التنفيذ: ").append(String.format(Locale.US, "%.1f", ms)).append(" مللي ثانية");
        return new ExtraResult(sb.toString(), draw(src, bodies));
    }

    private static Body analyze(Bitmap src, AutoSession s, MpDecode.Det person) throws Exception {
        // النقطة 0: منتصف الحوض (مركز المنطقة)، والنقطة 1: تحدد حجم الجسم كله
        float hx = person.kp[0];
        float hy = person.kp[1];
        float fx = person.kp[2];
        float fy = person.kp[3];
        float radius = (float) Math.hypot(fx - hx, fy - hy);
        if (radius < 1f) return null;
        float rot = RotatedCrop.rotationFor(hx, hy, fx, fy);
        float side = 2f * radius * 1.25f;

        RotatedCrop crop = RotatedCrop.make(src, hx, hy, side, rot, s.w);
        float[] input = ImageTensor.build(crop.image, s.nhwc, false, ImageTensor.UNIT);
        crop.image.recycle();
        float[][] outs = s.run(input);

        float[] landmarks = null;
        float conf = -1f;
        for (float[] o : outs) {
            if (landmarks == null && o.length == LANDMARK_OUTPUT) landmarks = o;
            else if (conf < 0f && o.length == 1) conf = o[0];
        }
        if (landmarks == null) throw new IOException("هذا ليس نموذج وضعية الجسم المتوقع (لا يوجد مخرج بـ " + LANDMARK_OUTPUT + " رقماً)");
        if (conf >= 0f && conf < PRESENCE) return null;

        Body body = new Body();
        for (int i = 0; i < LANDMARKS; i++) {
            body.pts[i] = crop.map(landmarks[VALUES * i], landmarks[VALUES * i + 1]);
            float vis = landmarks[VALUES * i + 3];
            body.visible[i] = 1f / (1f + (float) Math.exp(-vis)) >= VISIBLE;
        }
        return body;
    }

    private static Bitmap draw(Bitmap src, List<Body> bodies) {
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(out);
        float unit = Math.max(out.getWidth(), out.getHeight()) / 250f;
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(Color.rgb(0, 220, 60));
        line.setStrokeWidth(Math.max(3f, unit));
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(Color.RED);
        float radius = Math.max(3f, unit * 1.4f);
        for (Body b : bodies) {
            for (int[] e : BONES) {
                if (b.visible[e[0]] && b.visible[e[1]]) {
                    canvas.drawLine(b.pts[e[0]][0], b.pts[e[0]][1], b.pts[e[1]][0], b.pts[e[1]][1], line);
                }
            }
            for (int i = 0; i < LANDMARKS; i++) {
                if (b.visible[i]) canvas.drawCircle(b.pts[i][0], b.pts[i][1], radius, dot);
            }
        }
        return out;
    }
}
