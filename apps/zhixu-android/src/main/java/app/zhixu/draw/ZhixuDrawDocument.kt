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

sealed interface ZhixuDrawStrokePoint {
    val x: Float
    val y: Float
}

data class ZhixuDrawBasicPoint(
    override val x: Float,
    override val y: Float,
) : ZhixuDrawStrokePoint

data class ZhixuDrawRoundPoint(
    override val x: Float,
    override val y: Float,
    val width: Float,
    val alpha: Float,
) : ZhixuDrawStrokePoint

data class ZhixuDrawFlatPoint(
    override val x: Float,
    override val y: Float,
    val rx: Float,
    val ry: Float,
    val angle: Float,
    val alpha: Float,
) : ZhixuDrawStrokePoint

data class ZhixuDrawStroke(
    override val id: String,
    val tool: ZhixuDrawTool,
    val colorArgb: Int,
    val width: Float,
    val alpha: Float,
    val points: List<ZhixuDrawStrokePoint>,
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
