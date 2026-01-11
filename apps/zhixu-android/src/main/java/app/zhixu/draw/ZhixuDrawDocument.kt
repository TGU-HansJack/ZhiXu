package app.zhixu.draw

import androidx.compose.ui.geometry.Offset

data class ZhixuDrawDocument(
    val meta: ZhixuDrawMeta,
    val pages: List<ZhixuDrawPage>,
)

data class ZhixuDrawMeta(
    val formatVersion: Int = 1,
    val createdAtMs: Long,
    val modifiedAtMs: Long,
    val pageOrder: List<String>,
)

data class ZhixuDrawPage(
    val id: String,
    val width: Float,
    val height: Float,
    val backgroundColorArgb: Int = 0xFFFFFFFF.toInt(),
    val elements: List<ZhixuDrawElement>,
)

enum class ZhixuDrawTool {
    Pen,
    Highlighter,
    Shape,
}

enum class ZhixuDrawShape {
    Line,
    Rectangle,
    Ellipse,
}

sealed interface ZhixuDrawElement {
    val id: String
}

data class ZhixuDrawStroke(
    override val id: String,
    val tool: ZhixuDrawTool,
    val colorArgb: Int,
    val width: Float,
    val alpha: Float,
    val points: List<Offset>,
) : ZhixuDrawElement

data class ZhixuDrawShapeElement(
    override val id: String,
    val shape: ZhixuDrawShape,
    val colorArgb: Int,
    val width: Float,
    val alpha: Float,
    val start: Offset,
    val end: Offset,
) : ZhixuDrawElement
