package app.zhixu.ocr

import java.io.File

interface OcrEngine {
    suspend fun recognize(image: File): OcrResult
}

