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

// فرع MediaPipe وضعية اليد: 21 نقطة لكل يد. يحتاج نموذج اكتشاف الكفوف أولاً
final class HandPoseModel {
    static final String TITLE = "MediaPipe — وضعية اليد";
    static final String HINT = "يرسم هيكل اليد بـ 21 نقطة (يحتاج نموذج اكتشاف الكفوف مستورداً)";
    static final String FILE = "handpose.onnx";

    private static final int DEFAULT_SIZE = 224;
    private static final int LANDMARKS = 21;
    private static final float PRESENCE = 0.5f;

    private static final int[][] BONES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 4},
            {0, 5}, {5, 6}, {6, 7}, {7, 8},
            {5, 9}, {9, 10}, {10, 11}, {11, 12},
            {9, 13}, {13, 14}, {14, 15}, {15, 16},
            {13, 17}, {17, 18}, {18, 19}, {19, 20},
            {0, 17}
    };

    private HandPoseModel() {
    }

    static ExtraResult run(ExtraHost host, Bitmap src, File model) throws Exception {
        long t0 = System.nanoTime();
        File palmFile = new File(host.modelsDir(), PalmDetModel.FILE);
        if (!palmFile.isFile()) throw new IOException("استورد نموذج اكتشاف الكفوف أولاً");

        // 1) اكتشاف الكفوف
        List<MpDecode.Det> palms = PalmDetModel.detect(src, palmFile);
        if (palms.isEmpty()) return new ExtraResult("لم يُكتشف أي كف في الصورة", null);

        // 2) تحليل كل كف
        AutoSession s = AutoSession.load(model, DEFAULT_SIZE);
        List<float[][]> hands = new ArrayList<>();
        for (MpDecode.Det palm : palms) {
            float[][] pts = analyze(src, s, palm);
            if (pts != null) hands.add(pts);
        }
        double ms = (System.nanoTime() - t0) / 1_000_000.0;

        StringBuilder sb = new StringBuilder();
        sb.append("عدد الأيدي: ").append(hands.size()).append('\n');
        sb.append("زمن التنفيذ: ").append(String.format(Locale.US, "%.1f", ms)).append(" مللي ثانية");
        return new ExtraResult(sb.toString(), draw(src, hands));
    }

    // نقاط اليد الـ21 بإحداثيات الصورة الأصلية، أو null إذا لم تؤكد النموذج وجود يد
    private static float[][] analyze(Bitmap src, AutoSession s, MpDecode.Det palm) throws Exception {
        // الدوران من المعصم (النقطة 0) إلى قاعدة الإصبع الوسطى (النقطة 2)
        float rot = RotatedCrop.rotationFor(palm.kp[0], palm.kp[1], palm.kp[4], palm.kp[5]);
        float bw = palm.x2 - palm.x1;
        float bh = palm.y2 - palm.y1;
        float cx = (palm.x1 + palm.x2) / 2f;
        float cy = (palm.y1 + palm.y2) / 2f;
        // إزاحة المركز نصف الارتفاع للأعلى (باتجاه الأصابع) ثم تكبير الضلع الأطول 2.6 مرة
        float shiftY = -0.5f;
        float ccx = cx - bh * shiftY * (float) Math.sin(rot);
        float ccy = cy + bh * shiftY * (float) Math.cos(rot);
        float side = Math.max(bw, bh) * 2.6f;

        RotatedCrop crop = RotatedCrop.make(src, ccx, ccy, side, rot, s.w);
        float[] input = ImageTensor.build(crop.image, s.nhwc, false, ImageTensor.UNIT);
        crop.image.recycle();
        float[][] outs = s.run(input);

        float[] landmarks = null;
        float conf = -1f;
        for (float[] o : outs) {
            if (landmarks == null && o.length == LANDMARKS * 3) landmarks = o;
            else if (conf < 0f && o.length == 1) conf = o[0];
        }
        if (landmarks == null) throw new IOException("هذا ليس نموذج وضعية اليد المتوقع (لا يوجد مخرج بـ 63 رقماً)");
        if (conf >= 0f && conf < PRESENCE) return null;

        float[][] pts = new float[LANDMARKS][];
        for (int i = 0; i < LANDMARKS; i++) {
            pts[i] = crop.map(landmarks[3 * i], landmarks[3 * i + 1]);
        }
        return pts;
    }

    private static Bitmap draw(Bitmap src, List<float[][]> hands) {
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(out);
        float unit = Math.max(out.getWidth(), out.getHeight()) / 250f;
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(Color.rgb(0, 220, 60));
        line.setStrokeWidth(Math.max(3f, unit));
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(Color.RED);
        float radius = Math.max(3f, unit * 1.4f);
        for (float[][] p : hands) {
            for (int[] b : BONES) canvas.drawLine(p[b[0]][0], p[b[0]][1], p[b[1]][0], p[b[1]][1], line);
            for (float[] q : p) canvas.drawCircle(q[0], q[1], radius, dot);
        }
        return out;
    }
}
