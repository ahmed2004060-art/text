package com.local.ortfacetest;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

// قصّ مربع مُدار من الصورة (لنماذج وضعية اليد والجسم) مع إمكانية إعادة النقاط إلى الصورة الأصلية
final class RotatedCrop {
    final Bitmap image;
    private final Matrix toImage;
    private final float[] pt = new float[2];

    private RotatedCrop(Bitmap image, Matrix toImage) {
        this.image = image;
        this.toImage = toImage;
    }

    // rotRad: الدوران بالراديان باتجاه عقارب الساعة. side: طول ضلع المربع بوحدة بكسلات الصورة الأصلية
    static RotatedCrop make(Bitmap src, float cx, float cy, float side, float rotRad, int size) {
        Matrix m = new Matrix();
        m.postTranslate(-size / 2f, -size / 2f);
        m.postScale(side / size, side / size);
        m.postRotate((float) Math.toDegrees(rotRad));
        m.postTranslate(cx, cy);
        Matrix inv = new Matrix();
        if (!m.invert(inv)) inv.reset();
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.BLACK);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        float k = size / side;
        if (k < 1f) {
            int sw = Math.max(1, Math.round(src.getWidth() * k));
            int sh = Math.max(1, Math.round(src.getHeight() * k));
            Bitmap small = Downscale.to(src, sw, sh);
            inv.preScale(1f / k, 1f / k);
            canvas.drawBitmap(small, inv, paint);
            if (small != src) small.recycle();
        } else {
            canvas.drawBitmap(src, inv, paint);
        }
        return new RotatedCrop(out, m);
    }

    // من إحداثيات القصّ (بالبكسل) إلى إحداثيات الصورة الأصلية
    float[] map(float x, float y) {
        pt[0] = x;
        pt[1] = y;
        toImage.mapPoints(pt);
        return new float[]{pt[0], pt[1]};
    }

    // زاوية الدوران بحيث يصبح الخط من نقطة البداية إلى النهاية عمودياً (للأعلى)، مطبَّعة إلى [-π, π]
    static float rotationFor(float sx, float sy, float ex, float ey) {
        double rot = Math.PI / 2.0 - Math.atan2(-(ey - sy), ex - sx);
        rot = rot - 2.0 * Math.PI * Math.floor((rot + Math.PI) / (2.0 * Math.PI));
        return (float) rot;
    }
}
