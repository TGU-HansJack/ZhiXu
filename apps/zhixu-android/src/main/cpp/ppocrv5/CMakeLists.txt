cmake_minimum_required(VERSION 3.10)
project(ppocrv5ncnn)

set(_OPENCV_PACKAGE_DIR ${CMAKE_SOURCE_DIR}/third_party/opencv-mobile-4.12.0-android)
set(_OPENCV_PACKAGE_ZIP ${CMAKE_SOURCE_DIR}/third_party/opencv-mobile-4.12.0-android.zip)
if(EXISTS "${_OPENCV_PACKAGE_ZIP}" AND NOT EXISTS "${_OPENCV_PACKAGE_DIR}/sdk/native/staticlibs/${ANDROID_ABI}/libopencv_core.a")
    message(STATUS "OpenCV prebuilt libs missing; extracting ${_OPENCV_PACKAGE_ZIP}")
    execute_process(
            COMMAND ${CMAKE_COMMAND} -E tar xvf "${_OPENCV_PACKAGE_ZIP}"
            WORKING_DIRECTORY "${CMAKE_SOURCE_DIR}/third_party"
            RESULT_VARIABLE _opencv_extract_result
    )
    if(NOT _opencv_extract_result EQUAL 0)
        message(FATAL_ERROR "Failed to extract OpenCV prebuilt package (exit ${_opencv_extract_result})")
    endif()
endif()

set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/third_party/opencv-mobile-4.12.0-android/sdk/native/jni)
find_package(OpenCV REQUIRED core imgproc)

set(_NCNN_PACKAGE_DIR ${CMAKE_SOURCE_DIR}/third_party/ncnn-20250916-android-vulkan)
set(_NCNN_PACKAGE_ZIP ${CMAKE_SOURCE_DIR}/third_party/ncnn-20250916-android-vulkan.zip)
if(EXISTS "${_NCNN_PACKAGE_ZIP}" AND NOT EXISTS "${_NCNN_PACKAGE_DIR}/${ANDROID_ABI}/lib/libncnn.a")
    message(STATUS "ncnn prebuilt libs missing; extracting ${_NCNN_PACKAGE_ZIP}")
    execute_process(
            COMMAND ${CMAKE_COMMAND} -E tar xvf "${_NCNN_PACKAGE_ZIP}"
            WORKING_DIRECTORY "${CMAKE_SOURCE_DIR}/third_party"
            RESULT_VARIABLE _ncnn_extract_result
    )
    if(NOT _ncnn_extract_result EQUAL 0)
        message(FATAL_ERROR "Failed to extract ncnn prebuilt package (exit ${_ncnn_extract_result})")
    endif()
endif()

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
