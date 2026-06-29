#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <vector>
#include <algorithm>

#define TAG "SignalHunter"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" {

// دالة تحسب مستوى التهديد وتميز نوعه
// ترجع قيمة (Float):
// 0 - 10  : بيئة آمنة (لا يوجد نشاط)
// 10 - 30 : نشاط كهربائي عادي (أجهزة منزلية)
// 30 - 60 : نشاط بلوتوث أو واي فاي (إشارات متقطعة)
// 60 - 100: نشاط كاميرا أو ميكروفون (تيار مستمر عالي)
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

    // 1. استقبال البيانات من Kotlin
    jfloat *x_vals = env->GetFloatArrayElements(x_arr, nullptr);
    jfloat *y_vals = env->GetFloatArrayElements(y_arr, nullptr);
    jfloat *z_vals = env->GetFloatArrayElements(z_arr, nullptr);

    float x = x_vals[0];
    float y = y_vals[0];
    float z = z_vals[0];

    // 2. تصفية المجال المغناطيسي الثابت للأرض (إزالة Baseline)
    float filteredX = x - baselineX;
    float filteredY = y - baselineY;
    float filteredZ = z - baselineZ;

    // 3. حساب مقدار المتجه الكلي (المجال المغناطيسي الناتج عن الجهاز فقط)
    float magnitude = sqrtf(filteredX*filteredX + filteredY*filteredY + filteredZ*filteredZ);

    // 4. تحليل "التغير اللحظي" (الاشتقاق الزمني) باستخدام متغيرات ثابتة (Static) لحساب الفرق بين القراءة الحالية والسابقة
    static float previousMagnitude = 0.0f;
    float delta = fabs(magnitude - previousMagnitude);
    previousMagnitude = magnitude;

    // 5. المنطق الخوارزمي لتمييز التهديد:
    float threatLevel = 0.0f;

    // إذا كانت قيمة المتجه ضعيفة جداً، فهي مجرد ضوضاء بيئية
    if (magnitude < 1.5f) {
        threatLevel = 0.0f; // آمن جداً
    }
    // إذا كان التغير السريع (Delta) عالياً جداً، فهذا يعني وجود وميض كهرومغناطيسي (بلوتوث/واي فاي)
    else if (delta > 8.0f) {
        threatLevel = 70.0f; // تهديد عالي (اتصالات لاسلكية نشطة)
    }
    // إذا كانت قيمة المتجه عالية ولكن التغير بطيء (ثابت)، فهذا يعني تشغيل كاميرا أو شاشة (تيار مستمر)
    else if (magnitude > 8.0f && delta < 3.0f) {
        threatLevel = 90.0f; // تهديد شديد (كاميرا أو ميكروفون يعملان)
    }
    // إذا كانت القيم متوسطة، فهي أجهزة كهربائية عادية (شاحن، محول)
    else if (magnitude > 4.0f) {
        threatLevel = 40.0f; // نشاط متوسط (شاحن أو محول كهربائي)
    } else {
        threatLevel = magnitude * 3.0f; // تدرج بسيط للقيم المنخفضة
    }

    // تسجيل النتائج في Logcat لمساعدتك في التصحيح (Debugging)
    LOGI("Magnitude: %.2f, Delta: %.2f, Threat: %.2f", magnitude, delta, threatLevel);

    // تحرير الذاكرة
    env->ReleaseFloatArrayElements(x_arr, x_vals, JNI_ABORT);
    env->ReleaseFloatArrayElements(y_arr, y_vals, JNI_ABORT);
    env->ReleaseFloatArrayElements(z_arr, z_vals, JNI_ABORT);

    // إرجاع قيمة التهديد إلى Kotlin (ستظهر على الشاشة)
    return threatLevel;
}

}
