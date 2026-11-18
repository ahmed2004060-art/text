package com.local.ortfacetest;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// فرع YOLOX: اكتشاف الأجسام (80 صنفاً من COCO)
final class YoloxModel {
    static final String TITLE = "YOLOX — اكتشاف الأجسام";
    static final String HINT = "يرسم مربعاً واسماً لكل جسم يتعرف عليه";
    static final String FILE = "yolox.onnx";

    private static final int DEFAULT_SIZE = 640;
    private static final int[] STRIDES = {8, 16, 32};
    private static final int CLASSES = 80;
    private static final float CONF = 0.35f;
    private static final float NMS = 0.5f;
    private static final int MAX_SHOWN = 30;

    private static final String[] NAMES = {
            "شخص", "دراجة", "سيارة", "دراجة نارية", "طائرة", "حافلة", "قطار", "شاحنة", "قارب", "إشارة مرور",
            "صنبور إطفاء", "علامة توقف", "عداد وقوف", "مقعد", "طائر", "قطة", "كلب", "حصان", "خروف", "بقرة",
            "فيل", "دب", "حمار وحشي", "زرافة", "حقيبة ظهر", "مظلة", "حقيبة يد", "ربطة عنق", "حقيبة سفر", "قرص طائر",
            "زلاجات", "لوح تزلج ثلجي", "كرة رياضية", "طائرة ورقية", "مضرب بيسبول", "قفاز بيسبول", "لوح تزلج", "لوح أمواج", "مضرب تنس", "زجاجة",
            "كأس نبيذ", "كوب", "شوكة", "سكين", "ملعقة", "وعاء", "موزة", "تفاحة", "شطيرة", "برتقالة",
            "بروكلي", "جزرة", "هوت دوج", "بيتزا", "دونات", "كعكة", "كرسي", "أريكة", "نبتة", "سرير",
            "طاولة طعام", "مرحاض", "تلفاز", "حاسوب محمول", "فأرة", "جهاز تحكم", "لوحة مفاتيح", "هاتف", "ميكروويف", "فرن",
            "محمصة", "حوض", "ثلاجة", "كتاب", "ساعة", "مزهرية", "مقص", "دمية دب", "مجفف شعر", "فرشاة أسنان"
    };

    private static final class Box {
        float x1, y1, x2, y2, score;
        int cls;
    }

    private YoloxModel() {
    }

    static ExtraResult run(ExtraHost host, Bitmap src, File model) throws Exception {
        long t0 = System.nanoTime();
        AutoSession s = AutoSession.load(model, DEFAULT_SIZE);
        if (s.nhwc) throw new IOException("ترتيب مدخل هذا النموذج غير متوقع لـ YOLOX");

        // الصورة في الزاوية العلوية اليسرى والحشو رمادي 114، ألوان BGR وقيم من 0 إلى 255
        Letterbox lb = Letterbox.make(src, s.w, s.h, false, Color.rgb(114, 114, 114));
        float[] input = ImageTensor.build(lb.image, false, true, ImageTensor.RAW);
        float[][] outs = s.run(input);

        int cells = 0;
        for (int st : STRIDES) cells += (s.w / st) * (s.h / st);
        int width = 5 + CLASSES;
        float[] o = outs.length > 0 ? outs[0] : new float[0];
        if (o.length != cells * width) {
            throw new IOException("هذا ليس نموذج YOLOX المتوقع (حجم المخرج " + o.length + " بدلاً من " + (cells * width) + ")");
        }

        List<Box> all = new ArrayList<>();
        int i = 0;
        for (int st : STRIDES) {
            int cols = s.w / st;
            int rows = s.h / st;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++, i++) {
                    int b = i * width;
                    float obj = o[b + 4];
                    int best = 0;
                    float bestScore = 0f;
                    for (int k = 0; k < CLASSES; k++) {
                        float sc = obj * o[b + 5 + k];
                        if (sc > bestScore) {
                            bestScore = sc;
                            best = k;
                        }
                    }
                    if (bestScore < CONF) continue;
                    float cx = (o[b] + c) * st;
                    float cy = (o[b + 1] + r) * st;
                    float bw = (float) Math.exp(o[b + 2]) * st;
                    float bh = (float) Math.exp(o[b + 3]) * st;
                    Box box = new Box();
                    box.score = bestScore;
                    box.cls = best;
                    box.x1 = clamp((cx - bw / 2f) / lb.ratio, 0, src.getWidth());
                    box.y1 = clamp((cy - bh / 2f) / lb.ratio, 0, src.getHeight());
                    box.x2 = clamp((cx + bw / 2f) / lb.ratio, 0, src.getWidth());
                    box.y2 = clamp((cy + bh / 2f) / lb.ratio, 0, src.getHeight());
                    if (box.x2 - box.x1 > 1 && box.y2 - box.y1 > 1) all.add(box);
                }
            }
        }
        Collections.sort(all, new Comparator<Box>() {
            @Override
            public int compare(Box a, Box b) {
                return Float.compare(b.score, a.score);
            }
        });
        List<Box> kept = new ArrayList<>();
        for (Box b : all) {
            boolean keep = true;
            for (Box k : kept) {
                if (k.cls == b.cls && iou(b, k) > NMS) {
                    keep = false;
                    break;
                }
            }
            if (keep) kept.add(b);
            if (kept.size() >= MAX_SHOWN) break;
        }
        double ms = (System.nanoTime() - t0) / 1_000_000.0;

        StringBuilder sb = new StringBuilder();
        sb.append("عدد الأجسام: ").append(kept.size()).append('\n');
        for (Box b : kept) {
            sb.append(NAMES[b.cls]).append(" (").append(Math.round(b.score * 100f)).append("%)\n");
        }
        sb.append("زمن التنفيذ: ").append(String.format(Locale.US, "%.1f", ms)).append(" مللي ثانية");
        return new ExtraResult(sb.toString(), draw(src, kept));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float iou(Box a, Box b) {
        float iw = Math.max(0f, Math.min(a.x2, b.x2) - Math.max(a.x1, b.x1));
        float ih = Math.max(0f, Math.min(a.y2, b.y2) - Math.max(a.y1, b.y1));
        float inter = iw * ih;
        float union = (a.x2 - a.x1) * (a.y2 - a.y1) + (b.x2 - b.x1) * (b.y2 - b.y1) - inter;
        return union <= 0 ? 0f : inter / union;
    }

    private static Bitmap draw(Bitmap src, List<Box> boxes) {
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(out);
        float unit = Math.max(out.getWidth(), out.getHeight()) / 250f;
        float size = Math.max(22f, unit * 6f);
        Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
        box.setStyle(Paint.Style.STROKE);
        box.setStrokeWidth(Math.max(3f, unit));
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTextSize(size);
        text.setColor(Color.YELLOW);
        text.setShadowLayer(4f, 0f, 0f, Color.BLACK);
        for (Box b : boxes) {
            box.setColor(Color.HSVToColor(new float[]{(b.cls * 47) % 360, 0.9f, 1f}));
            canvas.drawRect(b.x1, b.y1, b.x2, b.y2, box);
            float y = b.y1 - size * 0.3f;
            if (y < size) y = b.y1 + size;
            canvas.drawText(NAMES[b.cls] + " " + Math.round(b.score * 100f) + "%", b.x1, y, text);
        }
        return out;
    }
}
