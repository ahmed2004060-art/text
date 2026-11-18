package com.local.ortfacetest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// قراءة مخرجات كاشفات MediaPipe (الكفوف والأشخاص): صناديق ونقاط بالنسبة للمراسي
final class MpDecode {
    static final class Det {
        float x1, y1, x2, y2, score;
        float[] kp; // x,y لكل نقطة، بإحداثيات الصورة الأصلية
    }

    private MpDecode() {
    }

    static List<Det> decode(float[][] outs, int kpCount, int input, float[] anchors,
                            float scoreThr, float nmsThr, Letterbox lb) throws IOException {
        int n = anchors.length / 2;
        int stride = 4 + 2 * kpCount;
        float[] boxes = null;
        float[] scores = null;
        for (float[] o : outs) {
            if (o.length == n * stride) boxes = o;
            else if (o.length == n) scores = o;
        }
        if (boxes == null || scores == null) {
            throw new IOException("مخرجات النموذج لا تطابق المتوقع (" + n + " مرساة)");
        }
        List<Det> all = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            float raw = Math.max(-100f, Math.min(100f, scores[i]));
            float s = 1f / (1f + (float) Math.exp(-raw));
            if (s < scoreThr) continue;
            int b = i * stride;
            float ax = anchors[2 * i];
            float ay = anchors[2 * i + 1];
            float cx = boxes[b] / input + ax;
            float cy = boxes[b + 1] / input + ay;
            float w = boxes[b + 2] / input;
            float h = boxes[b + 3] / input;
            Det d = new Det();
            d.score = s;
            d.x1 = lb.toSrcX((cx - w / 2f) * input);
            d.y1 = lb.toSrcY((cy - h / 2f) * input);
            d.x2 = lb.toSrcX((cx + w / 2f) * input);
            d.y2 = lb.toSrcY((cy + h / 2f) * input);
            d.kp = new float[2 * kpCount];
            for (int k = 0; k < kpCount; k++) {
                d.kp[2 * k] = lb.toSrcX((boxes[b + 4 + 2 * k] / input + ax) * input);
                d.kp[2 * k + 1] = lb.toSrcY((boxes[b + 5 + 2 * k] / input + ay) * input);
            }
            if (d.x2 - d.x1 > 1 && d.y2 - d.y1 > 1) all.add(d);
        }
        Collections.sort(all, new Comparator<Det>() {
            @Override
            public int compare(Det a, Det b) {
                return Float.compare(b.score, a.score);
            }
        });
        List<Det> kept = new ArrayList<>();
        for (Det d : all) {
            boolean keep = true;
            for (Det k : kept) {
                if (iou(d, k) > nmsThr) {
                    keep = false;
                    break;
                }
            }
            if (keep) kept.add(d);
        }
        return kept;
    }

    private static float iou(Det a, Det b) {
        float iw = Math.max(0f, Math.min(a.x2, b.x2) - Math.max(a.x1, b.x1));
        float ih = Math.max(0f, Math.min(a.y2, b.y2) - Math.max(a.y1, b.y1));
        float inter = iw * ih;
        float union = (a.x2 - a.x1) * (a.y2 - a.y1) + (b.x2 - b.x1) * (b.y2 - b.y1) - inter;
        return union <= 0 ? 0f : inter / union;
    }
}
