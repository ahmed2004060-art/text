package com.local.ortfacetest;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

// فرع PPHumanSeg: تقسيم الأشخاص (قناع لكل بكسل: شخص أو خلفية)
final class PpHumanSegModel {
    static final String TITLE = "PPHumanSeg — تقسيم الأشخاص";
    static final String HINT = "يلوّن مساحة الأشخاص في الصورة";
    static final String FILE = "pphumanseg.onnx";
    private static final int DEFAULT_SIZE = 192;

    private PpHumanSegModel() {
    }

    static ExtraResult run(ExtraHost host, Bitmap src, File model) throws Exception {
        long t0 = System.nanoTime();
        AutoSession s = AutoSession.load(model, DEFAULT_SIZE);
        Bitmap small = Downscale.to(src, s.w, s.h);
        // ألوان RGB وقيم (القيمة/255 − 0.5) / 0.5
        float[] input = ImageTensor.build(small, s.nhwc, false, ImageTensor.SIGNED);
        if (small != src) small.recycle();
        float[][] outs = s.run(input);

        int plane = s.w * s.h;
        float[] o = outs.length > 0 ? outs[0] : new float[0];
        if (o.length != 2 * plane) {
            throw new IOException("هذا ليس نموذج PPHumanSeg المتوقع (حجم المخرج " + o.length + " بدلاً من " + (2 * plane) + ")");
        }
        int[] px = new int[plane];
        int count = 0;
        int person = Color.argb(120, 0, 220, 60);
        for (int i = 0; i < plane; i++) {
            if (o[plane + i] > o[i]) {
                px[i] = person;
                count++;
            }
        }
        Bitmap mask = Bitmap.createBitmap(px, s.w, s.h, Bitmap.Config.ARGB_8888);
        double ms = (System.nanoTime() - t0) / 1_000_000.0;

        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(mask, null, new Rect(0, 0, out.getWidth(), out.getHeight()), paint);
        mask.recycle();

        float percent = 100f * count / plane;
        String text = "مساحة الأشخاص: " + String.format(Locale.US, "%.1f", percent) + "% من الصورة"
                + "\nزمن التنفيذ: " + String.format(Locale.US, "%.1f", ms) + " مللي ثانية";
        return new ExtraResult(text, out);
    }
}
