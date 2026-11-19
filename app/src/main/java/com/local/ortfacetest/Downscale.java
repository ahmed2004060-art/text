package com.local.ortfacetest;

import android.graphics.Bitmap;

// تصغير الصورة على خطوات خفيفة (كل خطوة تُبقي ثمانين بالمئة) ثم الوصول إلى الحجم المطلوب بالضبط
final class Downscale {
    private static final float KEEP = 0.8f;

    private Downscale() {
    }

    // يعيد الصورة نفسها إذا كان حجمها هو المطلوب
    static Bitmap to(Bitmap src, int tw, int th) {
        Bitmap cur = src;
        while (cur.getWidth() > tw || cur.getHeight() > th) {
            int nw = cur.getWidth() > tw ? Math.max(tw, Math.round(cur.getWidth() * KEEP)) : cur.getWidth();
            int nh = cur.getHeight() > th ? Math.max(th, Math.round(cur.getHeight() * KEEP)) : cur.getHeight();
            Bitmap next = Bitmap.createScaledBitmap(cur, nw, nh, true);
            if (cur != src) cur.recycle();
            cur = next;
        }
        if (cur.getWidth() != tw || cur.getHeight() != th) {
            Bitmap next = Bitmap.createScaledBitmap(cur, tw, th, true);
            if (cur != src) cur.recycle();
            cur = next;
        }
        return cur;
    }
}
