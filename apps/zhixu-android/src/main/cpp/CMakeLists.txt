cmake_minimum_required(VERSION 3.22.1)

project(paddleocr_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Provided by Gradle task `prepareOcrNativeDeps`
set(OCR_DEPS_DIR "" CACHE PATH "Path to extracted native deps (ncnn/opencv) for OCR")
if (OCR_DEPS_DIR STREQUAL "")
    message(FATAL_ERROR "OCR_DEPS_DIR is required (set by Gradle)")
endif()

set(OCR_INCLUDE_DIR "${OCR_DEPS_DIR}/include")
set(OCR_JNILIBS_DIR "${OCR_DEPS_DIR}/jniLibs/${ANDROID_ABI}")

add_library(ncnn SHARED IMPORTED)
set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION "${OCR_JNILIBS_DIR}/libncnn.so")

add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES IMPORTED_LOCATION "${OCR_JNILIBS_DIR}/libopencv_java4.so")

add_library(paddleocr_jni SHARED
        paddle_ocr.cpp
        paddle_ocr_jni.cpp
)

target_include_directories(paddleocr_jni PRIVATE
        "${OCR_INCLUDE_DIR}"
        "${CMAKE_CURRENT_LIST_DIR}"
)

target_compile_definitions(paddleocr_jni PRIVATE
        NCNN_BUILD_WITH_STATIC_CRT=0
)

target_link_libraries(paddleocr_jni PRIVATE
        ncnn
        opencv_java4
        log
        android
        jnigraphics
)
