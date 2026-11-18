package com.local.ortfacetest;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.File;
import java.util.List;
import java.util.Locale;

// فرع MediaPipe اكتشاف الكفوف: صندوق لكل كف مع سبع نقاط
final class PalmDetModel {
    static final String TITLE = "MediaPipe — اكتشاف الكفوف";
    static final String HINT = "يرسم مربعاً حول كل كف (يستخدمه نموذج وضعية اليد)";
    static final String FILE = "palmdet.onnx";

    private static final int DEFAULT_SIZE = 192;
    private static final int[] STRIDES = {8, 16, 16, 16};
    static final int KEYPOINTS = 7;
    private static final float SCORE = 0.5f;
    private static final float NMS = 0.3f;
    private static final int MAX_PALMS = 4;

    private PalmDetModel() {
    }

    // يكتشف الكفوف بإحداثيات الصورة الأصلية. يستدعيه نموذج وضعية اليد أيضاً
    static List<MpDecode.Det> detect(Bitmap src, File model) throws Exception {
        AutoSession s = AutoSession.load(model, DEFAULT_SIZE);
        // الصورة في الوسط والحشو أسود، ألوان RGB وقيم من 0 إلى 1
        Letterbox lb = Letterbox.make(src, s.w, s.h, true, Color.BLACK);
        float[] input = ImageTensor.build(lb.image, s.nhwc, false, ImageTensor.UNIT);
        float[][] outs = s.run(input);
        float[] anchors = MpAnchors.generate(s.w, STRIDES);
        List<MpDecode.Det> dets = MpDecode.decode(outs, KEYPOINTS, s.w, anchors, SCORE, NMS, lb);
        if (dets.size() > MAX_PALMS) dets = dets.subList(0, MAX_PALMS);
        return dets;
    }

    static ExtraResult run(ExtraHost host, Bitmap src, File model) throws Exception {
        long t0 = System.nanoTime();
        List<MpDecode.Det> dets = detect(src, model);
        double ms = (System.nanoTime() - t0) / 1_000_000.0;

        StringBuilder sb = new StringBuilder();
        sb.append("عدد الكفوف: ").append(dets.size()).append('\n');
        for (int i = 0; i < dets.size(); i++) {
            sb.append("الكف ").append(i + 1).append(": ")
                    .append(Math.round(dets.get(i).score * 100f)).append("%\n");
        }
        sb.append("زمن التنفيذ: ").append(String.format(Locale.US, "%.1f", ms)).append(" مللي ثانية");

        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(out);
        float unit = Math.max(out.getWidth(), out.getHeight()) / 250f;
        Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
        box.setStyle(Paint.Style.STROKE);
        box.setColor(Color.rgb(0, 220, 60));
        box.setStrokeWidth(Math.max(3f, unit));
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(Color.RED);
        float radius = Math.max(3f, unit * 1.3f);
        for (MpDecode.Det d : dets) {
            canvas.drawRect(d.x1, d.y1, d.x2, d.y2, box);
            for (int k = 0; k < KEYPOINTS; k++) canvas.drawCircle(d.kp[2 * k], d.kp[2 * k + 1], radius, dot);
        }
        return new ExtraResult(sb.toString(), out);
    }
}
