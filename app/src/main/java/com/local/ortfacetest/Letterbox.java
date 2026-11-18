package com.local.ortfacetest;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

// تصغير الصورة مع حفظ نسبتها ووضعها داخل مربع الإدخال مع حشو بلون ثابت
final class Letterbox {
    final Bitmap image;
    final float ratio;
    final float padX;
    final float padY;

    private Letterbox(Bitmap image, float ratio, float padX, float padY) {
        this.image = image;
        this.ratio = ratio;
        this.padX = padX;
        this.padY = padY;
    }

    static Letterbox make(Bitmap src, int tw, int th, boolean center, int padColor) {
        float ratio = Math.min(tw / (float) src.getWidth(), th / (float) src.getHeight());
        int rw = Math.max(1, Math.min(tw, Math.round(src.getWidth() * ratio)));
        int rh = Math.max(1, Math.min(th, Math.round(src.getHeight() * ratio)));
        int left = center ? (tw - rw) / 2 : 0;
        int top = center ? (th - rh) / 2 : 0;
        Bitmap out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(padColor);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        canvas.drawBitmap(src, null, new Rect(left, top, left + rw, top + rh), paint);
        return new Letterbox(out, ratio, left, top);
    }

    // من إحداثيات مربع الإدخال إلى إحداثيات الصورة الأصلية
    float toSrcX(float x) {
        return (x - padX) / ratio;
    }

    float toSrcY(float y) {
        return (y - padY) / ratio;
    }
}
