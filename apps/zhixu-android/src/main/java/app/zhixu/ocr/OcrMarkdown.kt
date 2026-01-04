package app.zhixu.ocr

object OcrMarkdown {
    fun toMarkdown(
        title: String,
        imageVaultRelativePath: String?,
        result: OcrResult,
    ): String {
        val safeTitle = title.trim().ifBlank { "OCR" }
        val sb = StringBuilder()
        sb.append("# ").append(safeTitle).append("\n\n")
        if (!imageVaultRelativePath.isNullOrBlank()) {
            sb.append("![](").append(imageVaultRelativePath.trim()).append(")\n\n")
        }
        sb.append("## OCR\n\n")
        val text = result.text
        if (text.isBlank()) {
            sb.append("_（无识别结果）_\n")
        } else {
            sb.append("```\n")
            sb.append(text).append("\n")
            sb.append("```\n")
        }
        if (result.engine.isNotBlank() || result.elapsedMs != null) {
            sb.append("\n---\n")
            sb.append("- engine: ").append(if (result.engine.isBlank()) "-" else result.engine).append("\n")
            sb.append("- elapsedMs: ").append(result.elapsedMs?.toString() ?: "-").append("\n")
            sb.append("- blocks: ").append(result.blocks.size).append("\n")
        }
        val err = result.error?.trim().orEmpty()
        if (err.isNotBlank()) {
            if (!sb.endsWith("\n")) sb.append("\n")
            if (!sb.contains("\n---\n")) sb.append("\n---\n")
            sb.append("- error: ").append(err.replace("\n", " ")).append("\n")
        }
        return sb.toString()
    }
}
