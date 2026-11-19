package com.local.ortfacetest;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import java.io.IOException;

// محاذاة الوجه إلى 112×112 بالنقاط الخمس، يستخدمها SFace وMobileFaceNet كلٌّ على حدة
final class FaceAlign {
    static final int SIZE = 112;

    // مواضع الملامح القياسية: العين اليمنى في الصورة، العين اليسرى، الأنف، طرف الفم الأيمن، طرف الفم الأيسر
    private static final float[] STD = {
            38.2946f, 51.6963f,
            73.5318f, 51.5014f,
            56.0252f, 71.7366f,
            41.5493f, 92.3655f,
            70.7299f, 92.2041f
    };

    private FaceAlign() {
    }

    // face: x1,y1,x2,y2,الدرجة، ثم النقاط الخمس ابتداءً من الموضع 5
    static Bitmap align(Bitmap src, float[] face) throws IOException {
        double msx = 0, msy = 0, mdx = 0, mdy = 0;
        for (int i = 0; i < 5; i++) {
            msx += face[5 + 2 * i];
            msy += face[5 + 2 * i + 1];
            mdx += STD[2 * i];
            mdy += STD[2 * i + 1];
        }
        msx /= 5;
        msy /= 5;
        mdx /= 5;
        mdy /= 5;

        double num1 = 0, num2 = 0, den = 0;
        for (int i = 0; i < 5; i++) {
            double sx = face[5 + 2 * i] - msx;
            double sy = face[5 + 2 * i + 1] - msy;
            double dx = STD[2 * i] - mdx;
            double dy = STD[2 * i + 1] - mdy;
            num1 += sx * dx + sy * dy;
            num2 += sx * dy - sy * dx;
            den += sx * sx + sy * sy;
        }
        if (den < 1e-6) throw new IOException("ملامح الوجه غير صالحة للمحاذاة");
        double a = num1 / den;
        double b = num2 / den;
        double tx = mdx - (a * msx - b * msy);
        double ty = mdy - (b * msx + a * msy);

        Matrix m = new Matrix();
        m.setValues(new float[]{
                (float) a, (float) -b, (float) tx,
                (float) b, (float) a, (float) ty,
                0f, 0f, 1f});
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.BLACK);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        float k = (float) Math.hypot(a, b);
        if (k > 0f && k < 1f) {
            int sw = Math.max(1, Math.round(src.getWidth() * k));
            int sh = Math.max(1, Math.round(src.getHeight() * k));
            Bitmap small = Downscale.to(src, sw, sh);
            m.preScale(1f / k, 1f / k);
            canvas.drawBitmap(small, m, paint);
            if (small != src) small.recycle();
        } else {
            canvas.drawBitmap(src, m, paint);
        }
        return out;
    }

    // ثلاث قنوات بترتيب أحمر ثم أخضر ثم أزرق.
    // halfNorm=false: قيم من 0 إلى 255.  halfNorm=true: (القيمة/255 − 0.5) / 0.5
    static float[] toInput(Bitmap aligned, boolean halfNorm) {
        int[] px = new int[SIZE * SIZE];
        aligned.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
        int plane = SIZE * SIZE;
        float[] data = new float[3 * plane];
        for (int i = 0; i < plane; i++) {
            int c = px[i];
            float r = (c >> 16) & 0xFF;
            float g = (c >> 8) & 0xFF;
            float bl = c & 0xFF;
            if (halfNorm) {
                r = (r / 255f - 0.5f) / 0.5f;
                g = (g / 255f - 0.5f) / 0.5f;
                bl = (bl / 255f - 0.5f) / 0.5f;
            }
            data[i] = r;
            data[plane + i] = g;
            data[2 * plane + i] = bl;
        }
        return data;
    }
}
