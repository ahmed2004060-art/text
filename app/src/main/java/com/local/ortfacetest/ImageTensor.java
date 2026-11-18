package com.local.ortfacetest;

import android.graphics.Bitmap;

// يحوّل صورة بحجم الإدخال تماماً إلى مصفوفة أرقام بترتيب القنوات المطلوب
final class ImageTensor {
    static final int RAW = 0;     // من 0 إلى 255
    static final int UNIT = 1;    // من 0 إلى 1
    static final int SIGNED = 2;  // من -1 إلى 1

    private ImageTensor() {
    }

    static float[] build(Bitmap img, boolean nhwc, boolean bgr, int mode) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] px = new int[w * h];
        img.getPixels(px, 0, w, 0, 0, w, h);
        int plane = w * h;
        float[] data = new float[3 * plane];
        for (int i = 0; i < plane; i++) {
            int c = px[i];
            float r = (c >> 16) & 0xFF;
            float g = (c >> 8) & 0xFF;
            float b = c & 0xFF;
            if (mode == UNIT) {
                r /= 255f;
                g /= 255f;
                b /= 255f;
            } else if (mode == SIGNED) {
                r = r / 127.5f - 1f;
                g = g / 127.5f - 1f;
                b = b / 127.5f - 1f;
            }
            float c0 = bgr ? b : r;
            float c2 = bgr ? r : b;
            if (nhwc) {
                data[3 * i] = c0;
                data[3 * i + 1] = g;
                data[3 * i + 2] = c2;
            } else {
                data[i] = c0;
                data[plane + i] = g;
                data[2 * plane + i] = c2;
            }
        }
        return data;
    }
}
