package app.zhixu.ocr

data class OcrResult(
    val blocks: List<OcrBlock> = emptyList(),
    val engine: String = "",
    val elapsedMs: Long? = null,
    val error: String? = null,
) {
    val text: String = blocks.joinToString(separator = "\n") { it.text.trim() }.trim()
}

data class OcrBlock(
    val text: String,
    val confidence: Float? = null,
    val box: OcrBox? = null,
)

data class OcrBox(
    val points: List<OcrPoint>,
) {
    init {
        require(points.size == 4) { "OcrBox requires 4 points" }
    }
}

data class OcrPoint(
    val x: Float,
    val y: Float,
)
