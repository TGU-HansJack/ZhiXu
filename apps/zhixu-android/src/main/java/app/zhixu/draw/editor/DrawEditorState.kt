package app.zhixu.draw.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import app.zhixu.core.tasks.Ulid
import app.zhixu.draw.ZhixuDrawBasicPoint
import app.zhixu.draw.ZhixuDrawDocument
import app.zhixu.draw.ZhixuDrawElement
import app.zhixu.draw.ZhixuDrawFlatPoint
import app.zhixu.draw.ZhixuDrawMeta
import app.zhixu.draw.ZhixuDrawPage
import app.zhixu.draw.ZhixuDrawRoundPoint
import app.zhixu.draw.ZhixuDrawShape
import app.zhixu.draw.ZhixuDrawShapeElement
import app.zhixu.draw.ZhixuDrawStroke
import app.zhixu.draw.ZhixuDrawStrokePoint
import app.zhixu.draw.ZhixuDrawTool
import kotlin.math.PI

enum class DrawToolId {
    Pen,
    Highlighter,
    Shape,
    Lasso,
    Eraser,
    Pan,
}

enum class DrawPenStyle {
    FountainPen,
    BallpointPen,
}

enum class DrawShapeMode {
    Line,
    Rectangle,
    Ellipse,
}

enum class DrawPressureMapping {
    Width,
    Opacity,
    Both,
}

enum class DrawPressureCurve {
    Linear,
    Soft,
    Hard,
    Custom,
}

enum class DrawTiltMapping {
    Width,
    Angle,
    Shading,
}

sealed interface DrawElementState {
    val id: String
}

class DrawStrokeState(
    override val id: String,
    val tool: ZhixuDrawTool,
    var colorArgb: Int,
    var width: Float,
    var alpha: Float,
    val points: SnapshotStateList<ZhixuDrawStrokePoint>,
    var isComplete: Boolean,
) : DrawElementState

class DrawShapeState(
    override val id: String,
    var shape: DrawShapeMode,
    var colorArgb: Int,
    var width: Float,
    var alpha: Float,
    var start: Offset,
    var end: Offset,
) : DrawElementState

class DrawPageState(
    val id: String,
    var width: Float,
    var height: Float,
    var backgroundColorArgb: Int,
    val elements: SnapshotStateList<DrawElementState>,
)

data class PreviewShape(
    val mode: DrawShapeMode,
    val start: Offset,
    val end: Offset,
)

class DrawViewportState {
    var viewportSize by mutableStateOf(IntSize.Zero)
    var scale by mutableFloatStateOf(1f)
    var translation by mutableStateOf(Offset.Zero)

    fun viewToPage(pointInView: Offset): Offset {
        val s = scale.coerceAtLeast(0.0001f)
        return (pointInView - translation) / s
    }
}

class DrawEditorState private constructor(
    formatVersion: Int,
    createdAtMs: Long,
    modifiedAtMs: Long,
    pages: List<DrawPageState>,
) {
    var formatVersion by mutableIntStateOf(formatVersion.coerceAtLeast(1))
    var createdAtMs by mutableStateOf(createdAtMs)
    var modifiedAtMs by mutableStateOf(modifiedAtMs)

    val pages: SnapshotStateList<DrawPageState> = mutableStateListOf<DrawPageState>().also { it.addAll(pages) }

    var currentPageIndex by mutableIntStateOf(0)

    var toolId by mutableStateOf(DrawToolId.Pen)
    var penStyle by mutableStateOf(DrawPenStyle.FountainPen)
    var shapeMode by mutableStateOf(DrawShapeMode.Line)

    var fountainPenColorArgb by mutableIntStateOf(0xFF000000.toInt())
    var fountainPenWidth by mutableFloatStateOf(3f)

    var ballpointPenColorArgb by mutableIntStateOf(0xFF000000.toInt())
    var ballpointPenWidth by mutableFloatStateOf(3f)

    var highlighterColorArgb by mutableIntStateOf(0xFF000000.toInt())
    var highlighterWidth by mutableFloatStateOf(18f)
    var highlighterAlpha by mutableFloatStateOf(0.35f)

    var pressureEnabled by mutableStateOf(true)
    var pressureMapping by mutableStateOf(DrawPressureMapping.Both)
    var pressureCurve by mutableStateOf(DrawPressureCurve.Linear)
    var pressureCurveGamma by mutableFloatStateOf(1f)

    var tiltEnabled by mutableStateOf(false)
    var tiltMapping by mutableStateOf(DrawTiltMapping.Shading)

    var shapeColorArgb by mutableIntStateOf(0xFF000000.toInt())
    var shapeWidth by mutableFloatStateOf(3f)

    var eraserRadius by mutableFloatStateOf(14f)

    var currentPenColorArgb: Int
        get() =
            when (penStyle) {
                DrawPenStyle.FountainPen -> fountainPenColorArgb
                DrawPenStyle.BallpointPen -> ballpointPenColorArgb
            }
        set(value) {
            when (penStyle) {
                DrawPenStyle.FountainPen -> fountainPenColorArgb = value
                DrawPenStyle.BallpointPen -> ballpointPenColorArgb = value
            }
        }

    var currentPenWidth: Float
        get() =
            when (penStyle) {
                DrawPenStyle.FountainPen -> fountainPenWidth
                DrawPenStyle.BallpointPen -> ballpointPenWidth
            }
        set(value) {
            when (penStyle) {
                DrawPenStyle.FountainPen -> fountainPenWidth = value
                DrawPenStyle.BallpointPen -> ballpointPenWidth = value
            }
        }

    fun colorForTool(id: DrawToolId): Int? =
        when (id) {
            DrawToolId.Pen -> currentPenColorArgb
            DrawToolId.Highlighter -> highlighterColorArgb
            DrawToolId.Shape -> shapeColorArgb
            else -> null
        }

    var selectedElementIds by mutableStateOf<Set<String>>(emptySet())

    val lassoPathPoints: SnapshotStateList<Offset> = mutableStateListOf()
    var previewShape by mutableStateOf<PreviewShape?>(null)
    var eraserCursor by mutableStateOf<Offset?>(null)
    var previewStroke by mutableStateOf<DrawStrokeState?>(null)

    val viewport: DrawViewportState = DrawViewportState()

    fun currentPageOrNull(): DrawPageState? = pages.getOrNull(currentPageIndex)

    fun ensureHasAtLeastOnePage(defaultWidth: Float = 595f, defaultHeight: Float = 842f) {
        if (pages.isNotEmpty()) return
        pages.add(
            DrawPageState(
                id = "page_001",
                width = defaultWidth,
                height = defaultHeight,
                backgroundColorArgb = 0xFFFFFFFF.toInt(),
                elements = mutableStateListOf(),
            ),
        )
        currentPageIndex = 0
    }

    fun addPageLikeCurrent() {
        val current = currentPageOrNull()
        val w = current?.width ?: 595f
        val h = current?.height ?: 842f
        val bg = current?.backgroundColorArgb ?: 0xFFFFFFFF.toInt()
        val id = "page_${(pages.size + 1).toString().padStart(3, '0')}"
        pages.add(DrawPageState(id = id, width = w, height = h, backgroundColorArgb = bg, elements = mutableStateListOf()))
        currentPageIndex = pages.lastIndex
        clearOverlaysAndSelection()
    }

    fun insertPageAfterCurrent() {
        val current = currentPageOrNull()
        val w = current?.width ?: 595f
        val h = current?.height ?: 842f
        val bg = current?.backgroundColorArgb ?: 0xFFFFFFFF.toInt()
        val id = "page_${(pages.size + 1).toString().padStart(3, '0')}"
        val insertAt = (currentPageIndex + 1).coerceIn(0, pages.size)
        pages.add(insertAt, DrawPageState(id = id, width = w, height = h, backgroundColorArgb = bg, elements = mutableStateListOf()))
        currentPageIndex = insertAt
        clearOverlaysAndSelection()
    }

    fun rotateCurrentPage90Degrees() {
        val page = currentPageOrNull() ?: return
        val oldW = page.width
        val oldH = page.height
        if (oldW <= 1f || oldH <= 1f) return

        fun rotatePoint(p: ZhixuDrawStrokePoint): ZhixuDrawStrokePoint {
            val x = oldH - p.y
            val y = p.x
            return when (p) {
                is ZhixuDrawFlatPoint -> p.copy(x = x, y = y, angle = p.angle + (PI.toFloat() / 2f))
                is ZhixuDrawRoundPoint -> p.copy(x = x, y = y)
                is ZhixuDrawBasicPoint -> p.copy(x = x, y = y)
                else -> ZhixuDrawBasicPoint(x = x, y = y)
            }
        }

        for (el in page.elements) {
            when (el) {
                is DrawStrokeState -> {
                    for (i in 0 until el.points.size) {
                        el.points[i] = rotatePoint(el.points[i])
                    }
                }

                is DrawShapeState -> {
                    el.start = Offset(oldH - el.start.y, el.start.x)
                    el.end = Offset(oldH - el.end.y, el.end.x)
                }
            }
        }

        page.width = oldH
        page.height = oldW
        clearOverlaysAndSelection()
    }

    fun goToPreviousPage() {
        if (pages.isEmpty()) return
        currentPageIndex = (currentPageIndex - 1).coerceAtLeast(0)
        clearOverlaysAndSelection()
    }

    fun goToNextPage() {
        if (pages.isEmpty()) return
        currentPageIndex = (currentPageIndex + 1).coerceAtMost(pages.lastIndex)
        clearOverlaysAndSelection()
    }

    fun clearOverlaysAndSelection() {
        lassoPathPoints.clear()
        previewShape = null
        eraserCursor = null
        previewStroke = null
        selectedElementIds = emptySet()
    }

    fun snapshotPageElements(pageIndex: Int = currentPageIndex): List<ZhixuDrawElement> {
        val page = pages.getOrNull(pageIndex) ?: return emptyList()
        return page.elements.mapNotNull { el ->
            when (el) {
                is DrawStrokeState ->
                    ZhixuDrawStroke(
                        id = el.id,
                        tool = el.tool,
                        colorArgb = el.colorArgb,
                        width = el.width,
                        alpha = el.alpha,
                        points = el.points.toList(),
                    )
                is DrawShapeState ->
                    ZhixuDrawShapeElement(
                        id = el.id,
                        shape =
                            when (el.shape) {
                                DrawShapeMode.Line -> ZhixuDrawShape.Line
                                DrawShapeMode.Rectangle -> ZhixuDrawShape.Rectangle
                                DrawShapeMode.Ellipse -> ZhixuDrawShape.Ellipse
                            },
                        colorArgb = el.colorArgb,
                        width = el.width,
                        alpha = el.alpha,
                        start = el.start,
                        end = el.end,
                    )
                else -> null
            }
        }
    }

    fun restorePageElements(
        elements: List<ZhixuDrawElement>,
        pageIndex: Int = currentPageIndex,
    ) {
        val page = pages.getOrNull(pageIndex) ?: return
        page.elements.clear()
        page.elements.addAll(elements.map { it.toState() })
        clearOverlaysAndSelection()
    }

    fun toDocument(): ZhixuDrawDocument {
        val pageFiles = pages.mapIndexed { index, _ -> app.zhixu.draw.ZhixuDrawFormat.pageFileName(index) }
        val inferredVersion =
            when {
                formatVersion >= 2 -> formatVersion
                pages.any { page -> page.elements.any { el -> el is DrawStrokeState && el.points.any { it !is ZhixuDrawBasicPoint } } } -> 2
                else -> 1
            }
        val meta =
            ZhixuDrawMeta(
                formatVersion = inferredVersion,
                createdAtMs = createdAtMs,
                modifiedAtMs = modifiedAtMs,
                pageOrder = pageFiles,
            )
        val pagesModel =
            pages.map { page ->
                ZhixuDrawPage(
                    id = page.id,
                    width = page.width,
                    height = page.height,
                    backgroundColorArgb = page.backgroundColorArgb,
                    elements =
                        page.elements.mapNotNull { el ->
                            when (el) {
                                is DrawStrokeState ->
                                    ZhixuDrawStroke(
                                        id = el.id,
                                        tool = el.tool,
                                        colorArgb = el.colorArgb,
                                        width = el.width,
                                        alpha = el.alpha,
                                        points = el.points.toList(),
                                    )
                                is DrawShapeState ->
                                    ZhixuDrawShapeElement(
                                        id = el.id,
                                        shape =
                                            when (el.shape) {
                                                DrawShapeMode.Line -> ZhixuDrawShape.Line
                                                DrawShapeMode.Rectangle -> ZhixuDrawShape.Rectangle
                                                DrawShapeMode.Ellipse -> ZhixuDrawShape.Ellipse
                                            },
                                        colorArgb = el.colorArgb,
                                        width = el.width,
                                        alpha = el.alpha,
                                        start = el.start,
                                        end = el.end,
                                    )
                                else -> null
                            }
                        },
                )
            }
        return ZhixuDrawDocument(meta = meta, pages = pagesModel)
    }

    companion object {
        fun newDocument(nowMs: Long = System.currentTimeMillis()): DrawEditorState =
            fromDocument(
                ZhixuDrawDocument(
                    meta = ZhixuDrawMeta(formatVersion = 1, createdAtMs = nowMs, modifiedAtMs = nowMs, pageOrder = emptyList()),
                    pages =
                        listOf(
                            ZhixuDrawPage(
                                id = "page_001",
                                width = 595f,
                                height = 842f,
                                elements = emptyList(),
                            ),
                        ),
                ),
            )

        fun fromDocument(document: ZhixuDrawDocument): DrawEditorState {
            val pages =
                document.pages.map { p ->
                    DrawPageState(
                        id = p.id,
                        width = p.width,
                        height = p.height,
                        backgroundColorArgb = p.backgroundColorArgb,
                        elements =
                            mutableStateListOf<DrawElementState>().also { list ->
                                list.addAll(p.elements.map { it.toState() })
                            },
                    )
                }
            val state = DrawEditorState(document.meta.formatVersion, document.meta.createdAtMs, document.meta.modifiedAtMs, pages)
            state.ensureHasAtLeastOnePage()
            return state
        }
    }
}

private fun ZhixuDrawElement.toState(): DrawElementState =
    when (this) {
        is ZhixuDrawStroke ->
            DrawStrokeState(
                id = id,
                tool = tool,
                colorArgb = colorArgb,
                width = width,
                alpha = alpha,
                points = mutableStateListOf<ZhixuDrawStrokePoint>().also { it.addAll(points) },
                isComplete = true,
            )
        is ZhixuDrawShapeElement ->
            DrawShapeState(
                id = id,
                shape =
                    when (shape) {
                        ZhixuDrawShape.Line -> DrawShapeMode.Line
                        ZhixuDrawShape.Rectangle -> DrawShapeMode.Rectangle
                        ZhixuDrawShape.Ellipse -> DrawShapeMode.Ellipse
                    },
                colorArgb = colorArgb,
                width = width,
                alpha = alpha,
                start = start,
                end = end,
            )
        else -> error("Unknown element type")
    }

internal fun newElementId(): String = Ulid.next()
