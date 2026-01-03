package app.zhixu.ocr

data class OcrBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float,
)

data class OcrResult(
    val blocks: List<OcrBlock>,
    val fullText: String,
)

