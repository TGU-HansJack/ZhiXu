#include <jni.h>
#include <android/bitmap.h>

#include <stdio.h>
#include <string>
#include <vector>

#include "paddle_ocr.h"

static jclass g_runtime_exception = nullptr;

static void throw_runtime(JNIEnv* env, const char* msg)
{
    if (!g_runtime_exception)
    {
        jclass ex = env->FindClass("java/lang/RuntimeException");
        g_runtime_exception = (jclass)env->NewGlobalRef(ex);
        env->DeleteLocalRef(ex);
    }
    env->ThrowNew(g_runtime_exception, msg);
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_zhixu_ocr_PaddleOcrNative_nativeInit(
    JNIEnv* env,
    jclass,
    jbyteArray detParam,
    jbyteArray detBin,
    jbyteArray recParam,
    jbyteArray recBin,
    jbyteArray dictTxt,
    jboolean useVulkan)
{
    if (!detParam || !detBin || !recParam || !recBin || !dictTxt)
    {
        throw_runtime(env, "model bytes are required");
        return 0;
    }

    jbyte* det_param_ptr = env->GetByteArrayElements(detParam, nullptr);
    jbyte* det_bin_ptr = env->GetByteArrayElements(detBin, nullptr);
    jbyte* rec_param_ptr = env->GetByteArrayElements(recParam, nullptr);
    jbyte* rec_bin_ptr = env->GetByteArrayElements(recBin, nullptr);
    jbyte* dict_ptr = env->GetByteArrayElements(dictTxt, nullptr);

    const int det_param_len = (int)env->GetArrayLength(detParam);
    const int det_bin_len = (int)env->GetArrayLength(detBin);
    const int rec_param_len = (int)env->GetArrayLength(recParam);
    const int rec_bin_len = (int)env->GetArrayLength(recBin);
    const int dict_len = (int)env->GetArrayLength(dictTxt);

    auto* ocr = new zhixu_ocr::PaddleOcr();
    bool ok = ocr->load(
        (const unsigned char*)det_param_ptr, det_param_len,
        (const unsigned char*)det_bin_ptr, det_bin_len,
        (const unsigned char*)rec_param_ptr, rec_param_len,
        (const unsigned char*)rec_bin_ptr, rec_bin_len,
        (const unsigned char*)dict_ptr, dict_len,
        useVulkan == JNI_TRUE);

    env->ReleaseByteArrayElements(detParam, det_param_ptr, JNI_ABORT);
    env->ReleaseByteArrayElements(detBin, det_bin_ptr, JNI_ABORT);
    env->ReleaseByteArrayElements(recParam, rec_param_ptr, JNI_ABORT);
    env->ReleaseByteArrayElements(recBin, rec_bin_ptr, JNI_ABORT);
    env->ReleaseByteArrayElements(dictTxt, dict_ptr, JNI_ABORT);

    if (!ok)
    {
        delete ocr;
        throw_runtime(env, "failed to load paddleocr models");
        return 0;
    }
    return (jlong)ocr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_zhixu_ocr_PaddleOcrNative_nativeRecognize(
    JNIEnv* env,
    jclass,
    jlong handle,
    jobject bitmap)
{
    if (handle == 0)
    {
        throw_runtime(env, "invalid ocr handle");
        return nullptr;
    }
    if (!bitmap)
    {
        throw_runtime(env, "bitmap is null");
        return nullptr;
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS)
    {
        throw_runtime(env, "AndroidBitmap_getInfo failed");
        return nullptr;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
    {
        throw_runtime(env, "bitmap must be RGBA_8888");
        return nullptr;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || !pixels)
    {
        throw_runtime(env, "AndroidBitmap_lockPixels failed");
        return nullptr;
    }

    auto* ocr = (zhixu_ocr::PaddleOcr*)handle;
    std::vector<zhixu_ocr::OcrLine> lines;
    try
    {
        lines = ocr->recognize_rgba8888((const unsigned char*)pixels, (int)info.width, (int)info.height);
    }
    catch (...)
    {
        AndroidBitmap_unlockPixels(env, bitmap);
        throw_runtime(env, "native recognize failed");
        return nullptr;
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    // Build a compact JSON string to avoid heavy JNI object construction.
    // Schema:
    // { "fullText": "...", "blocks": [ { "text":"...", "l":0, "t":0, "r":0, "b":0, "c":0.9 }, ... ] }
    std::string json;
    json.reserve(64 + lines.size() * 96);
    json += "{";
    json += "\"fullText\":";

    auto append_json_string = [&](const std::string& s)
    {
        json += "\"";
        for (size_t i = 0; i < s.size(); i++)
        {
            const unsigned char ch = (unsigned char)s[i];
            switch (ch)
            {
            case '\\': json += "\\\\"; break;
            case '\"': json += "\\\""; break;
            case '\n': json += "\\n"; break;
            case '\r': json += "\\r"; break;
            case '\t': json += "\\t"; break;
            default:
                if (ch < 0x20)
                {
                    char buf[16];
                    snprintf(buf, sizeof(buf), "\\u%04x", (unsigned int)ch);
                    json += buf;
                }
                else
                {
                    json += (char)ch;
                }
                break;
            }
        }
        json += "\"";
    };

    std::string full;
    for (size_t i = 0; i < lines.size(); i++)
    {
        if (i) full += "\n";
        full += lines[i].text;
    }
    append_json_string(full);
    json += ",\"blocks\":[";
    for (size_t i = 0; i < lines.size(); i++)
    {
        if (i) json += ",";
        json += "{";
        json += "\"text\":";
        append_json_string(lines[i].text);
        json += ",\"l\":";
        json += std::to_string(lines[i].left);
        json += ",\"t\":";
        json += std::to_string(lines[i].top);
        json += ",\"r\":";
        json += std::to_string(lines[i].right);
        json += ",\"b\":";
        json += std::to_string(lines[i].bottom);
        json += ",\"c\":";
        json += std::to_string(lines[i].confidence);
        json += "}";
    }
    json += "]}";

    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_app_zhixu_ocr_PaddleOcrNative_nativeRelease(
    JNIEnv*,
    jclass,
    jlong handle)
{
    if (handle == 0) return;
    auto* ocr = (zhixu_ocr::PaddleOcr*)handle;
    delete ocr;
}
