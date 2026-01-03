package app.zhixu.ocr

import android.graphics.Bitmap

internal object PaddleOcrNative {
    init {
        // Ensure dependent shared libraries are loaded first when present.
        runCatching { System.loadLibrary("opencv_core") }
        runCatching { System.loadLibrary("opencv_imgproc") }
        runCatching { System.loadLibrary("ncnn") }
        System.loadLibrary("paddleocr_jni")
    }

    external fun nativeInit(
        detParam: ByteArray,
        detBin: ByteArray,
        recParam: ByteArray,
        recBin: ByteArray,
        dictTxt: ByteArray,
        useVulkan: Boolean,
    ): Long

    external fun nativeRecognize(handle: Long, bitmap: Bitmap): String

    external fun nativeRelease(handle: Long)
}

