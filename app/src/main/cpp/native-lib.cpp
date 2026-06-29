#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <cstring>

#define TAG "SignalHunter"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// دالة يتم استدعاؤها عند تحميل المكتبة (للتأكد من نجاح التحميل)
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("✅ تم تحميل مكتبة SignalHunter بنجاح!");
    return JNI_VERSION_1_6;
}

extern "C" {

JNIEXPORT jfloat JNICALL
Java_com_example_signalhunter_MainActivity_calculateThreatLevel(
        JNIEnv *env,
        jobject thiz,
        jfloatArray x_arr,
        jfloatArray y_arr,
        jfloatArray z_arr,
        jfloat baselineX,
        jfloat baselineY,
        jfloat baselineZ) {

    // التحقق من أن المصفوفات ليست فارغة (لمنع الانهيار)
    if (x_arr == nullptr || y_arr == nullptr || z_arr == nullptr) {
        LOGE("❌ خطأ: مصفوفة JNI فارغة!");
        return 0.0f;
    }

    // استقبال البيانات مع التحقق من الأخطاء
    jfloat *x_vals = env->GetFloatArrayElements(x_arr, nullptr);
    jfloat *y_vals = env->GetFloatArrayElements(y_arr, nullptr);
    jfloat *z_vals = env->GetFloatArrayElements(z_arr, nullptr);

    if (x_vals == nullptr || y_vals == nullptr || z_vals == nullptr) {
        LOGE("❌ خطأ: فشل في قراءة عناصر المصفوفة!");
        return 0.0f;
    }

    float x = x_vals[0];
    float y = y_vals[0];
    float z = z_vals[0];

    // تصفية المجال المغناطيسي الثابت للأرض
    float filteredX = x - baselineX;
    float filteredY = y - baselineY;
    float filteredZ = z - baselineZ;

    // حساب مقدار المتجه الكلي (مع حماية من الأرقام غير الصالحة)
    float magnitude = sqrtf(filteredX*filteredX + filteredY*filteredY + filteredZ*filteredZ);
    if (std::isnan(magnitude) || std::isinf(magnitude)) {
        magnitude = 0.0f;
    }

    // حساب التغير اللحظي
    static float previousMagnitude = 0.0f;
    float delta = fabs(magnitude - previousMagnitude);
    previousMagnitude = magnitude;

    // منطق التهديد (مع جعل القيم أكثر استقراراً)
    float threatLevel = 0.0f;
    if (magnitude < 1.5f) {
        threatLevel = 0.0f;
    } else if (delta > 8.0f) {
        threatLevel = 70.0f;
    } else if (magnitude > 8.0f && delta < 3.0f) {
        threatLevel = 90.0f;
    } else if (magnitude > 4.0f) {
        threatLevel = 40.0f;
    } else {
        threatLevel = magnitude * 3.0f;
    }

    // تسجيل النتائج في Logcat (للتصحيح لاحقاً)
    LOGI("📊 المجال: %.2f, التغير: %.2f, التهديد: %.2f", magnitude, delta, threatLevel);

    // تحرير الذاكرة بأمان
    env->ReleaseFloatArrayElements(x_arr, x_vals, JNI_ABORT);
    env->ReleaseFloatArrayElements(y_arr, y_vals, JNI_ABORT);
    env->ReleaseFloatArrayElements(z_arr, z_vals, JNI_ABORT);

    return threatLevel;
}

}
