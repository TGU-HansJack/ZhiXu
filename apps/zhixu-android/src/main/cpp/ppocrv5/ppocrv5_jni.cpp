#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <mutex>
#include <string>
#include <vector>

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#include "gpu.h"
#include "ppocrv5.h"

static const char* kTag = "ZhixuOcr";

// Reuse upstream dict to decode tokens.
#include "ppocrv5_dict.h"

static std::mutex g_mutex;
static PPOCRv5* g_ppocrv5 = nullptr;
static bool g_gpu_enabled = false;

static std::string decode_text(const Object& obj)
{
    std::string text;
    for (size_t j = 0; j < obj.text.size(); j++)
    {
        const Character& ch = obj.text[j];
        if (ch.id < 0 || ch.id >= (int)character_dict_size)
        {
            if (!text.empty() && text.back() != ' ')
                text += " ";
            continue;
        }

        if (obj.orientation == 0)
        {
            text += character_dict[ch.id];
        }
        else
        {
            text += character_dict[ch.id];
            if (j + 1 < obj.text.size())
                text += "\n";
        }
    }
    return text;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_zhixu_ocr_ppocrv5_PpOcrV5Ncnn_nativeLoadModel(
        JNIEnv* env,
        jobject /*thiz*/,
        jstring detParamPath,
        jstring detBinPath,
        jstring recParamPath,
        jstring recBinPath,
        jboolean useFp16,
        jboolean useGpu)
{
    const char* det_param = env->GetStringUTFChars(detParamPath, 0);
    const char* det_bin = env->GetStringUTFChars(detBinPath, 0);
    const char* rec_param = env->GetStringUTFChars(recParamPath, 0);
    const char* rec_bin = env->GetStringUTFChars(recBinPath, 0);

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_ppocrv5)
    {
        delete g_ppocrv5;
        g_ppocrv5 = nullptr;
    }

    if (g_gpu_enabled)
    {
        ncnn::destroy_gpu_instance();
        g_gpu_enabled = false;
    }

    if (useGpu)
    {
        ncnn::create_gpu_instance();
        g_gpu_enabled = true;
    }

    g_ppocrv5 = new PPOCRv5();
    g_ppocrv5->load(det_param, det_bin, rec_param, rec_bin, useFp16, useGpu);
    g_ppocrv5->set_target_size(320);

    __android_log_print(ANDROID_LOG_INFO, kTag, "nativeLoadModel ok useFp16=%d useGpu=%d", (int)useFp16, (int)useGpu);

    env->ReleaseStringUTFChars(detParamPath, det_param);
    env->ReleaseStringUTFChars(detBinPath, det_bin);
    env->ReleaseStringUTFChars(recParamPath, rec_param);
    env->ReleaseStringUTFChars(recBinPath, rec_bin);

    return JNI_TRUE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_zhixu_ocr_ppocrv5_PpOcrV5Ncnn_nativeRecognizeBitmap(
        JNIEnv* env,
        jobject /*thiz*/,
        jobject bitmap)
{
    if (bitmap == nullptr) return nullptr;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS)
        return nullptr;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return nullptr;

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS)
        return nullptr;

    cv::Mat rgba((int)info.height, (int)info.width, CV_8UC4, pixels);
    cv::Mat rgb;
    cv::cvtColor(rgba, rgb, cv::COLOR_RGBA2RGB);

    AndroidBitmap_unlockPixels(env, bitmap);

    std::vector<Object> objects;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (!g_ppocrv5) return nullptr;
        g_ppocrv5->detect_and_recognize(rgb, objects);
    }

    __android_log_print(ANDROID_LOG_INFO, kTag, "nativeRecognizeBitmap objects=%d w=%d h=%d", (int)objects.size(), rgb.cols, rgb.rows);

    jclass blockClass = env->FindClass("app/zhixu/ocr/ppocrv5/NativeOcrBlock");
    if (!blockClass) return nullptr;
    jmethodID ctor = env->GetMethodID(blockClass, "<init>", "(Ljava/lang/String;F[F)V");
    if (!ctor) return nullptr;

    jobjectArray result = env->NewObjectArray((jsize)objects.size(), blockClass, nullptr);
    for (jsize i = 0; i < (jsize)objects.size(); i++)
    {
        const Object& obj = objects[(size_t)i];
        std::string text = decode_text(obj);

        cv::Point2f corners[4];
        obj.rrect.points(corners);
        float pts[8] = {
                corners[0].x, corners[0].y,
                corners[1].x, corners[1].y,
                corners[2].x, corners[2].y,
                corners[3].x, corners[3].y
        };

        jfloatArray pointsArray = env->NewFloatArray(8);
        env->SetFloatArrayRegion(pointsArray, 0, 8, pts);

        jstring jtext = env->NewStringUTF(text.c_str());
        jobject block = env->NewObject(blockClass, ctor, jtext, (jfloat)obj.prob, pointsArray);
        env->SetObjectArrayElement(result, i, block);

        env->DeleteLocalRef(pointsArray);
        env->DeleteLocalRef(jtext);
        env->DeleteLocalRef(block);
    }

    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_app_zhixu_ocr_ppocrv5_PpOcrV5Ncnn_nativeRelease(JNIEnv* /*env*/, jobject /*thiz*/)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ppocrv5)
    {
        delete g_ppocrv5;
        g_ppocrv5 = nullptr;
    }
    if (g_gpu_enabled)
    {
        ncnn::destroy_gpu_instance();
        g_gpu_enabled = false;
    }
}
