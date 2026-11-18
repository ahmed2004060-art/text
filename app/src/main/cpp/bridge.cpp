#include <jni.h>
#include <dlfcn.h>
#include <onnxruntime_c_api.h>
#include <cstdint>
#include <string>
#include <vector>

namespace {

void* g_lib = nullptr;
void* g_cxx = nullptr;
const OrtApi* g_api = nullptr;
OrtEnv* g_env = nullptr;
OrtSession* g_session = nullptr;
std::string g_inputName;
std::vector<std::string> g_outputNames;
std::string g_lastError;

std::string toStd(JNIEnv* env, jstring s) {
    if (!s) return std::string();
    const char* c = env->GetStringUTFChars(s, nullptr);
    if (!c) return std::string();
    std::string r(c);
    env->ReleaseStringUTFChars(s, c);
    return r;
}

jstring toJava(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

// يحوّل الحالة إلى نص ثم يحرّرها
std::string errorOf(OrtStatus* st) {
    std::string m = "خطأ غير معروف";
    if (st) {
        const char* c = g_api ? g_api->GetErrorMessage(st) : nullptr;
        if (c) m = c;
        if (g_api) g_api->ReleaseStatus(st);
    }
    return m;
}

void ignoreStatus(OrtStatus* st) {
    if (st && g_api) g_api->ReleaseStatus(st);
}

void releaseSession() {
    if (g_session && g_api) g_api->ReleaseSession(g_session);
    g_session = nullptr;
    g_inputName.clear();
    g_outputNames.clear();
}

void releaseEngine() {
    releaseSession();
    if (g_env && g_api) g_api->ReleaseEnv(g_env);
    g_env = nullptr;
    g_api = nullptr;
    if (g_lib) dlclose(g_lib);
    g_lib = nullptr;
    if (g_cxx) dlclose(g_cxx);
    g_cxx = nullptr;
}

// يقرأ معلومات المدخل والمخرجات من الجلسة
bool describeSession(OrtSession* s, std::string& inName, std::vector<int64_t>& dims,
                     ONNXTensorElementDataType& inType, std::vector<std::string>& outNames,
                     std::string& err) {
    OrtAllocator* alloc = nullptr;
    OrtStatus* st = g_api->GetAllocatorWithDefaultOptions(&alloc);
    if (st) { err = errorOf(st); return false; }

    size_t inCount = 0;
    st = g_api->SessionGetInputCount(s, &inCount);
    if (st) { err = errorOf(st); return false; }
    if (inCount != 1) { err = "عدد مدخلات النموذج لا يساوي واحداً"; return false; }

    char* name = nullptr;
    st = g_api->SessionGetInputName(s, 0, alloc, &name);
    if (st) { err = errorOf(st); return false; }
    inName = name ? name : "input";
    if (name) alloc->Free(alloc, name);

    OrtTypeInfo* ti = nullptr;
    st = g_api->SessionGetInputTypeInfo(s, 0, &ti);
    if (st) { err = errorOf(st); return false; }
    const OrtTensorTypeAndShapeInfo* info = nullptr;
    st = g_api->CastTypeInfoToTensorInfo(ti, &info);
    if (st || !info) {
        err = st ? errorOf(st) : "مدخل النموذج ليس صورة";
        g_api->ReleaseTypeInfo(ti);
        return false;
    }
    size_t rank = 0;
    st = g_api->GetDimensionsCount(info, &rank);
    if (st) { err = errorOf(st); g_api->ReleaseTypeInfo(ti); return false; }
    dims.assign(rank, 0);
    if (rank > 0) {
        st = g_api->GetDimensions(info, dims.data(), rank);
        if (st) { err = errorOf(st); g_api->ReleaseTypeInfo(ti); return false; }
    }
    st = g_api->GetTensorElementType(info, &inType);
    g_api->ReleaseTypeInfo(ti);
    if (st) { err = errorOf(st); return false; }

    size_t outCount = 0;
    st = g_api->SessionGetOutputCount(s, &outCount);
    if (st) { err = errorOf(st); return false; }
    outNames.clear();
    for (size_t i = 0; i < outCount; ++i) {
        char* n = nullptr;
        st = g_api->SessionGetOutputName(s, i, alloc, &n);
        if (st) { err = errorOf(st); return false; }
        outNames.push_back(n ? n : "");
        if (n) alloc->Free(alloc, n);
    }
    return true;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_local_ortfacetest_NativeBridge_loadEngine(JNIEnv* env, jclass, jstring jpath) {
    releaseEngine();
    std::string path = toStd(env, jpath);
    if (path.empty()) return toJava(env, "ERR|مسار المحرك فارغ");

    size_t slash = path.find_last_of('/');
    std::string dir = slash == std::string::npos ? std::string(".") : path.substr(0, slash);

    // إن كانت مكتبة المشاركة موجودة مع المحرك نحمّلها أولاً (اختياري)
    g_cxx = dlopen((dir + "/libc++_shared.so").c_str(), RTLD_NOW | RTLD_GLOBAL);

    g_lib = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!g_lib) {
        const char* e = dlerror();
        std::string m = std::string("ERR|تعذر تحميل مكتبة المحرك: ") + (e ? e : "");
        releaseEngine();
        return toJava(env, m);
    }

    typedef const OrtApiBase* (*GetBaseFn)();
    GetBaseFn getBase = reinterpret_cast<GetBaseFn>(dlsym(g_lib, "OrtGetApiBase"));
    if (!getBase) {
        releaseEngine();
        return toJava(env, "ERR|الملف لا يحتوي على واجهة المحرك المطلوبة");
    }
    const OrtApiBase* base = getBase();
    if (!base) {
        releaseEngine();
        return toJava(env, "ERR|تعذر قراءة واجهة المحرك");
    }

    // نجرب إصدار الواجهة الأحدث ثم الأقدم حتى يقبله المحرك
    const OrtApi* api = nullptr;
    for (int v = ORT_API_VERSION; v >= 13 && !api; --v) {
        api = base->GetApi(static_cast<uint32_t>(v));
    }
    if (!api) {
        releaseEngine();
        return toJava(env, "ERR|إصدار المحرك غير متوافق مع التطبيق");
    }
    g_api = api;

    OrtStatus* st = g_api->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "ortface", &g_env);
    if (st) {
        std::string m = "ERR|" + errorOf(st);
        releaseEngine();
        return toJava(env, m);
    }

    const char* version = base->GetVersionString ? base->GetVersionString() : "";
    return toJava(env, std::string("OK|") + (version ? version : ""));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_local_ortfacetest_NativeBridge_loadModel(JNIEnv* env, jclass, jstring jpath) {
    releaseSession();
    if (!g_api || !g_env) return toJava(env, "ERR|المحرك غير محمّل");
    std::string path = toStd(env, jpath);

    OrtSessionOptions* opts = nullptr;
    OrtStatus* st = g_api->CreateSessionOptions(&opts);
    if (st) return toJava(env, "ERR|" + errorOf(st));
    ignoreStatus(g_api->SetIntraOpNumThreads(opts, 4));
    ignoreStatus(g_api->SetSessionGraphOptimizationLevel(opts, ORT_ENABLE_ALL));

    OrtSession* session = nullptr;
    st = g_api->CreateSession(g_env, path.c_str(), opts, &session);
    g_api->ReleaseSessionOptions(opts);
    if (st) return toJava(env, "ERR|" + errorOf(st));

    std::string inName, err;
    std::vector<int64_t> dims;
    ONNXTensorElementDataType inType = ONNX_TENSOR_ELEMENT_DATA_TYPE_UNDEFINED;
    std::vector<std::string> outNames;
    if (!describeSession(session, inName, dims, inType, outNames, err)) {
        g_api->ReleaseSession(session);
        return toJava(env, "ERR|" + err);
    }
    if (inType != ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT) {
        g_api->ReleaseSession(session);
        return toJava(env, "ERR|نوع مدخل النموذج غير مدعوم (المطلوب أرقام عشرية)");
    }
    if (dims.size() != 4 || dims[1] != 3) {
        g_api->ReleaseSession(session);
        return toJava(env, "ERR|مدخل النموذج ليس صورة بثلاث قنوات");
    }

    g_session = session;
    g_inputName = inName;
    g_outputNames = outNames;

    long long w = dims[3] > 0 ? dims[3] : 0;
    long long h = dims[2] > 0 ? dims[2] : 0;
    std::string r = "OK|" + std::to_string(w) + "|" + std::to_string(h) + "|";
    for (size_t i = 0; i < outNames.size(); ++i) {
        if (i) r += ",";
        r += outNames[i];
    }
    return toJava(env, r);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_local_ortfacetest_NativeBridge_runModel(JNIEnv* env, jclass, jfloatArray input,
                                                  jint w, jint h) {
    if (!g_api || !g_session) {
        g_lastError = "النموذج غير محمّل";
        return nullptr;
    }
    if (!input || w <= 0 || h <= 0) {
        g_lastError = "بيانات الصورة غير صحيحة";
        return nullptr;
    }
    jsize len = env->GetArrayLength(input);
    if (static_cast<long long>(len) != 3LL * w * h) {
        g_lastError = "حجم بيانات الصورة لا يطابق أبعاد الإدخال";
        return nullptr;
    }

    jfloat* data = env->GetFloatArrayElements(input, nullptr);
    if (!data) {
        g_lastError = "تعذر قراءة بيانات الصورة";
        return nullptr;
    }

    const size_t outCount = g_outputNames.size();
    OrtValue* in = nullptr;
    std::vector<OrtValue*> outs(outCount, nullptr);
    jobjectArray result = nullptr;

    do {
        int64_t shape[4] = {1, 3, static_cast<int64_t>(h), static_cast<int64_t>(w)};
        OrtMemoryInfo* mem = nullptr;
        OrtStatus* st = g_api->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &mem);
        if (st) { g_lastError = errorOf(st); break; }
        st = g_api->CreateTensorWithDataAsOrtValue(mem, data, static_cast<size_t>(len) * sizeof(float),
                                                    shape, 4, ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &in);
        g_api->ReleaseMemoryInfo(mem);
        if (st) { g_lastError = errorOf(st); break; }

        std::vector<const char*> outNames;
        for (size_t i = 0; i < outCount; ++i) outNames.push_back(g_outputNames[i].c_str());
        const char* inNames[1] = {g_inputName.c_str()};
        const OrtValue* inValues[1] = {in};

        st = g_api->Run(g_session, nullptr, inNames, inValues, 1, outNames.data(), outCount, outs.data());
        if (st) { g_lastError = errorOf(st); break; }

        jclass floatArrayClass = env->FindClass("[F");
        jobjectArray arr = env->NewObjectArray(static_cast<jsize>(outCount), floatArrayClass, nullptr);
        if (!arr) { g_lastError = "الذاكرة لا تكفي لنتائج النموذج"; break; }

        bool ok = true;
        for (size_t i = 0; i < outCount && ok; ++i) {
            OrtTensorTypeAndShapeInfo* info = nullptr;
            st = g_api->GetTensorTypeAndShape(outs[i], &info);
            if (st) { g_lastError = errorOf(st); ok = false; break; }
            ONNXTensorElementDataType t = ONNX_TENSOR_ELEMENT_DATA_TYPE_UNDEFINED;
            size_t count = 0;
            ignoreStatus(g_api->GetTensorElementType(info, &t));
            ignoreStatus(g_api->GetTensorShapeElementCount(info, &count));
            g_api->ReleaseTensorTypeAndShapeInfo(info);
            if (t != ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT) {
                g_lastError = "نوع مخرجات النموذج غير مدعوم";
                ok = false;
                break;
            }
            float* values = nullptr;
            st = g_api->GetTensorMutableData(outs[i], reinterpret_cast<void**>(&values));
            if (st || !values) {
                g_lastError = st ? errorOf(st) : "تعذر قراءة مخرجات النموذج";
                ok = false;
                break;
            }
            jfloatArray one = env->NewFloatArray(static_cast<jsize>(count));
            if (!one) { g_lastError = "الذاكرة لا تكفي لنتائج النموذج"; ok = false; break; }
            env->SetFloatArrayRegion(one, 0, static_cast<jsize>(count), values);
            env->SetObjectArrayElement(arr, static_cast<jsize>(i), one);
            env->DeleteLocalRef(one);
        }
        if (ok) result = arr;
    } while (false);

    for (OrtValue* v : outs) {
        if (v) g_api->ReleaseValue(v);
    }
    if (in) g_api->ReleaseValue(in);
    env->ReleaseFloatArrayElements(input, data, JNI_ABORT);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_local_ortfacetest_NativeBridge_lastError(JNIEnv* env, jclass) {
    return toJava(env, g_lastError);
}

extern "C" JNIEXPORT void JNICALL
Java_com_local_ortfacetest_NativeBridge_unloadAll(JNIEnv*, jclass) {
    releaseEngine();
}
