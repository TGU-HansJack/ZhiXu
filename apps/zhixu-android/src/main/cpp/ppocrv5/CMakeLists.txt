cmake_minimum_required(VERSION 3.10)
project(ppocrv5ncnn)

set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/third_party/opencv-mobile-4.12.0-android/sdk/native/jni)
find_package(OpenCV REQUIRED core imgproc)

set(ncnn_DIR ${CMAKE_SOURCE_DIR}/third_party/ncnn-20250916-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

add_library(ppocrv5ncnn SHARED
        ppocrv5_jni.cpp
        ppocrv5.cpp
)

target_compile_features(ppocrv5ncnn PRIVATE cxx_std_17)
target_include_directories(ppocrv5ncnn PRIVATE
        ${CMAKE_SOURCE_DIR}
)

target_link_libraries(ppocrv5ncnn
        ncnn
        ${OpenCV_LIBS}
        jnigraphics
        log
)
