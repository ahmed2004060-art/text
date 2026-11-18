package com.local.ortfacetest;

import java.util.ArrayList;
import java.util.List;

// مراسي SSD الخاصة بنماذج MediaPipe (حجم المرساة ثابت، فنحتاج مراكزها فقط)
final class MpAnchors {
    private MpAnchors() {
    }

    // يعيد مصفوفة: x0,y0,x1,y1,... بقيم من 0 إلى 1
    static float[] generate(int input, int[] strides) {
        List<float[]> cells = new ArrayList<>();
        int total = 0;
        int layer = 0;
        while (layer < strides.length) {
            int last = layer;
            int perCell = 0;
            while (last < strides.length && strides[last] == strides[layer]) {
                perCell += 2; // مرساتان لكل طبقة (القياس العادي والقياس المتداخل)
                last++;
            }
            int fm = (int) Math.ceil(input / (double) strides[layer]);
            for (int y = 0; y < fm; y++) {
                for (int x = 0; x < fm; x++) {
                    for (int k = 0; k < perCell; k++) {
                        cells.add(new float[]{(x + 0.5f) / fm, (y + 0.5f) / fm});
                        total++;
                    }
                }
            }
            layer = last;
        }
        float[] out = new float[total * 2];
        for (int i = 0; i < total; i++) {
            out[2 * i] = cells.get(i)[0];
            out[2 * i + 1] = cells.get(i)[1];
        }
        return out;
    }
}
