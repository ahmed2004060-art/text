package com.local.ortfacetest;

import android.app.Activity;
import android.graphics.Bitmap;

import java.io.File;
import java.util.List;

// الواجهة التي يقدمها MainActivity للنماذج الإضافية (SFace وغيره)، دون أن تلمس النماذج كود YuNet
interface ExtraHost {
    Activity activity();

    boolean engineReady();

    boolean yunetReady();

    Bitmap image();

    boolean busy();

    // من خيط الشاشة فقط
    void setBusy(boolean value);

    void execute(Runnable task);

    // كل وجه: x1,y1,x2,y2,الدرجة، ثم عشر قيم لخمس نقاط (العينان، الأنف، طرفا الفم)
    List<float[]> detectFaces(Bitmap image) throws Exception;

    // يعيد تحميل YuNet حتى يعمل زر التشغيل الأصلي بعد انتهاء النموذج الإضافي
    void restoreYuNet();

    File modelsDir();

    // من خيط الشاشة فقط
    void showResult(String text, Bitmap annotated, boolean error);
}
