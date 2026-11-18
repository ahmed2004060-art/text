package com.local.ortfacetest;

final class NativeBridge {
    static boolean loaded;
    static String loadError = "";

    static {
        try {
            System.loadLibrary("ortbridge");
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
            loadError = String.valueOf(t.getMessage());
        }
    }

    private NativeBridge() {}

    // "OK|الإصدار" أو "ERR|السبب"
    static native String loadEngine(String libraryPath);

    // "OK|العرض|الارتفاع|أسماء المخرجات" أو "ERR|السبب"
    static native String loadModel(String modelPath);

    // يعيد مخرجات النموذج بالترتيب، أو null عند الفشل (والسبب في lastError)
    static native float[][] runModel(float[] input, int width, int height);

    static native String lastError();

    static native void unloadAll();
}
