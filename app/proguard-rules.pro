# الحفاظ على دوال الـ JNI (حتى لا يحذفها ProGuard)
-keep class com.example.signalhunter.MainActivity {
    native <methods>;
}

# منع تحذيرات الـ JNI
-dontwarn java.lang.**
-keepattributes *Annotation*
-keepattributes Signature
