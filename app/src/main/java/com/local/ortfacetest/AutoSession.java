package com.local.ortfacetest;

import java.io.File;
import java.io.IOException;

// جلسة نموذج بترتيب قنوات NCHW أو NHWC، تُستخدم في النماذج الإضافية الستة
final class AutoSession {
    final boolean nhwc;
    final int w;
    final int h;
    final int outputs;

    private AutoSession(boolean nhwc, int w, int h, int outputs) {
        this.nhwc = nhwc;
        this.w = w;
        this.h = h;
        this.outputs = outputs;
    }

    // defaultSize: يُستخدم إذا كان حجم مدخل النموذج غير ثابت
    static AutoSession load(File file, int defaultSize) throws IOException {
        String r = NativeBridge.loadModelAuto(file.getAbsolutePath());
        if (!r.startsWith("OK|")) {
            throw new IOException("النموذج غير صالح: " + (r.startsWith("ERR|") ? r.substring(4) : r));
        }
        String[] p = r.split("\\|", -1);
        boolean nhwc = p.length > 1 && "NHWC".equals(p[1]);
        int w = 0, h = 0;
        try {
            w = Integer.parseInt(p[2]);
            h = Integer.parseInt(p[3]);
        } catch (Exception ignored) {
        }
        if (w <= 0 || h <= 0) {
            w = defaultSize;
            h = defaultSize;
        }
        String csv = p.length > 4 ? p[4] : "";
        int outputs = csv.isEmpty() ? 0 : csv.split(",").length;
        return new AutoSession(nhwc, w, h, outputs);
    }

    float[][] run(float[] input) throws IOException {
        float[][] outs = NativeBridge.runModelAuto(input, w, h);
        if (outs == null) throw new IOException(NativeBridge.lastError());
        return outs;
    }
}
