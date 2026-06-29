cmake_minimum_required(VERSION 3.22)

project("signalhunter")

add_library(
        signalhunter
        SHARED
        native-lib.cpp
)

# البحث عن مكتبتي السجلات (log) والرياضيات (m)
find_library(log-lib log)
find_library(math-lib m)

# ربط المكتبات (أضفنا math-lib لمنع خطأ sqrtf)
target_link_libraries(
        signalhunter
        ${log-lib}
        ${math-lib}
)

target_compile_features(signalhunter PRIVATE cxx_std_17)
