package com.local.ortfacetest;

import android.graphics.Bitmap;

final class ExtraResult {
    final String text;
    final Bitmap image; // قد تكون null

    ExtraResult(String text, Bitmap image) {
        this.text = text;
        this.image = image;
    }
}
