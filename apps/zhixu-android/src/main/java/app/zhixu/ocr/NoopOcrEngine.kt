package app.zhixu.ocr

import java.io.File

class NoopOcrEngine(
    private val engineName: String = "noop",
) : OcrEngine {
    override suspend fun recognize(image: File): OcrResult = OcrResult(engine = engineName)
}

