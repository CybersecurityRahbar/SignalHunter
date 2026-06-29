cmake_minimum_required(VERSION 3.22)

# اسم المكتبة التي سنبنيها (يجب أن يتطابق مع System.loadLibrary("signalhunter"))
project("signalhunter")

add_library(
        signalhunter
        SHARED
        native-lib.cpp
)

# إضافة مكتبة السجلات (Log) لإظهار الرسائل في Logcat
find_library(log-lib log)

# ربط المكتبات
target_link_libraries(
        signalhunter
        ${log-lib}
)

# تحديد مواصفات C++ الحديثة
target_compile_features(signalhunter PRIVATE cxx_std_17)
