package app.zhixu.draw.tools

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import app.zhixu.draw.ZhixuDrawTool
import app.zhixu.draw.editor.DrawEditorState
import app.zhixu.draw.editor.DrawShapeMode
import app.zhixu.draw.editor.DrawShapeState
import app.zhixu.draw.editor.DrawStrokeState
import app.zhixu.draw.editor.DrawToolId
import app.zhixu.draw.editor.PreviewShape
import app.zhixu.draw.editor.newElementId
import app.zhixu.draw.geometry.distanceSquared
import app.zhixu.draw.geometry.lerp
import app.zhixu.draw.geometry.pointInPolygon
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

data class ToolPointerEvent(
    val viewPosition: Offset,
    val viewDelta: Offset,
    val pagePosition: Offset,
    val pageDelta: Offset,
)

interface DrawToolMachine {
    val id: DrawToolId
    fun onDown(editor: DrawEditorState, e: ToolPointerEvent)
    fun onMove(editor: DrawEditorState, e: ToolPointerEvent)
    fun onUp(editor: DrawEditorState, e: ToolPointerEvent)
    fun onCancel(editor: DrawEditorState)
}

class PenToolMachine : DrawToolMachine {
    override val id: DrawToolId = DrawToolId.Pen
    private var activeStrokeId: String? = null

    override fun onDown(editor: DrawEditorState, e: ToolPointerEvent) {
        val page = editor.currentPageOrNull() ?: return
        val strokeId = newElementId()
        val stroke =
            DrawStrokeState(
                id = strokeId,
                tool = ZhixuDrawTool.Pen,
                colorArgb = editor.currentPenColorArgb,
                width = editor.currentPenWidth,
                alpha = 1f,
                points = mutableStateListOf(e.pagePosition),
                isComplete = false,
            )
        page.elements.add(stroke)
        activeStrokeId = strokeId
    }

    override fun onMove(editor: DrawEditorState, e: ToolPointerEvent) {
        val page = editor.currentPageOrNull() ?: return
        val strokeId = activeStrokeId ?: return
        val stroke = page.elements.lastOrNull { it is DrawStrokeState && it.id == strokeId } as? DrawStrokeState ?: return
        stroke.points.add(e.pagePosition)
    }

    override fun onUp(editor: DrawEditorState, e: ToolPointerEvent) {
        val page = editor.currentPageOrNull() ?: return
        val strokeId = activeStrokeId ?: return
        val stroke = page.elements.lastOrNull { it is DrawStrokeState && it.id == strokeId } as? DrawStrokeState
        stroke?.isComplete = true
        activeStrokeId = null
    }

    override fun onCancel(editor: DrawEditorState) {
        val page = editor.currentPageOrNull() ?: return
        val strokeId = activeStrokeId ?: return
        page.elements.removeAll { it.id == strokeId }
        activeStrokeId = null
    }
}

class HighlighterToolMachine : DrawToolMachine {
    override val id: DrawToolId = DrawToolId.Highlighter
    private var activeStrokeId: String? = null

    override fun onDown(editor: DrawEditorState, e: ToolPointerEvent) {
        val page = editor.currentPageOrNull() ?: return
        val strokeId = newElementId()
        val stroke =
            DrawStrokeState(
                id = strokeId,
                tool = ZhixuDrawTool.Highlighter,
                colorArgb = editor.highlighterColorArgb,
                width = editor.highlighterWidth,
                alpha = editor.highlighterAlpha,
                points = mutableStateListOf(e.pagePosition),
                isComplete = false,
            )
        page.elements.add(stroke)
        activeStrokeId = strokeId
    }

    override fun onMove(editor: DrawEditorState, e: ToolPointerEvent) {
        val page = editor.currentPageOrNull() ?: return
        val strokeId = activeStrokeId ?: return
        val stroke = page.elements.lastOrNull { it is DrawStrokeState && it.id == strokeId } as? DrawStrokeState ?: return
        stroke.points.add(e.pagePosition)
    }

    override fun onUp(editor: DrawEditorState, e: ToolPointerEvent) {
        val page = editor.currentPageOrNull() ?: return
        val strokeId = activeStrokeId ?: return
        val stroke = page.elements.lastOrNull { it is DrawStrokeState && it.id == strokeId } as? DrawStrokeState
        stroke?.isComplete = true
        activeStrokeId = null
    }

    override fun onCancel(editor: DrawEditorState) {
        val page = editor.currentPageOrNull() ?: return
        val strokeId = activeStrokeId ?: return
        page.elements.removeAll { it.id == strokeId }
        activeStrokeId = null
    }
}

class ShapeToolMachine : DrawToolMachine {
    override val id: DrawToolId = DrawToolId.Shape
    private var start: Offset? = null

    override fun onDown(editor: DrawEditorState, e: ToolPointerEvent) {
        start = e.pagePosition
        editor.previewShape = PreviewShape(editor.shapeMode, start = e.pagePosition, end = e.pagePosition)
    }

    override fun onMove(editor: DrawEditorState, e: ToolPointerEvent) {
        val s = start ?: return
        editor.previewShape = PreviewShape(editor.shapeMode, start = s, end = e.pagePosition)
    }

    override fun onUp(editor: DrawEditorState, e: ToolPointerEvent) {
        val page = editor.currentPageOrNull() ?: return
        val s = start ?: return
        val shapeId = newElementId()
        page.elements.add(
            DrawShapeState(
                id = shapeId,
                shape = editor.shapeMode,
                colorArgb = editor.shapeColorArgb,
                width = editor.shapeWidth,
                alpha = 1f,
                start = s,
                end = e.pagePosition,
            ),
        )
        editor.previewShape = null
        start = null
    }

    override fun onCancel(editor: DrawEditorState) {
        editor.previewShape = null
        start = null
    }
}

class LassoToolMachine(
    private val minPercentInside: Float = 0.7f,
) : DrawToolMachine {
    override val id: DrawToolId = DrawToolId.Lasso

    override fun onDown(editor: DrawEditorState, e: ToolPointerEvent) {
        editor.lassoPathPoints.clear()
        editor.lassoPathPoints.add(e.pagePosition)
        editor.selectedElementIds = emptySet()
    }

    override fun onMove(editor: DrawEditorState, e: ToolPointerEvent) {
        editor.lassoPathPoints.add(e.pagePosition)
    }

    override fun onUp(editor: DrawEditorState, e: ToolPointerEvent) {
        editor.lassoPathPoints.add(e.pagePosition)
        val polygon = editor.lassoPathPoints.toList()
        val page = editor.currentPageOrNull() ?: run {
            editor.lassoPathPoints.clear()
            return
        }
        if (polygon.size < 3) {
            editor.lassoPathPoints.clear()
            return
        }

        val selected = HashSet<String>()
        for (el in page.elements) {
            when (el) {
                is DrawStrokeState -> {
                    val pts = el.points
                    if (pts.isEmpty()) continue
                    var inside = 0
                    for (p in pts) if (pointInPolygon(p, polygon)) inside++
                    val ratio = inside.toFloat() / max(pts.size, 1).toFloat()
                    if (ratio >= minPercentInside) selected.add(el.id)
                }

                is DrawShapeState -> {
                    val candidates =
                        listOf(
                            el.start,
                            el.end,
                            Offset((el.start.x + el.end.x) / 2f, (el.start.y + el.end.y) / 2f),
                        )
                    val inside = candidates.count { p -> pointInPolygon(p, polygon) }
                    if (inside >= 2) selected.add(el.id)
                }
            }
        }

        editor.selectedElementIds = selected
        editor.lassoPathPoints.clear()
    }

    override fun onCancel(editor: DrawEditorState) {
        editor.lassoPathPoints.clear()
    }
}

class EraserToolMachine : DrawToolMachine {
    override val id: DrawToolId = DrawToolId.Eraser
    private var lastPoint: Offset? = null

    override fun onDown(editor: DrawEditorState, e: ToolPointerEvent) {
        lastPoint = e.pagePosition
        editor.eraserCursor = e.pagePosition
        eraseAt(editor, e.pagePosition)
    }

    override fun onMove(editor: DrawEditorState, e: ToolPointerEvent) {
        val prev = lastPoint ?: e.pagePosition
        val next = e.pagePosition
        editor.eraserCursor = next

        val r = editor.eraserRadius.coerceAtLeast(1f)
        val step = max(2f, r * 0.6f)
        val dist = sqrt(distanceSquared(prev, next)).coerceAtLeast(0f)
        val n = ceil(dist / step).toInt().coerceAtLeast(1)
        for (i in 1..n) {
            val t = i.toFloat() / n.toFloat()
            eraseAt(editor, lerp(prev, next, t))
        }
        lastPoint = next
    }

    override fun onUp(editor: DrawEditorState, e: ToolPointerEvent) {
        editor.eraserCursor = null
        lastPoint = null
    }

    override fun onCancel(editor: DrawEditorState) {
        editor.eraserCursor = null
        lastPoint = null
    }

    private fun eraseAt(editor: DrawEditorState, at: Offset) {
        val page = editor.currentPageOrNull() ?: return
        val r = editor.eraserRadius.coerceAtLeast(1f)
        val r2 = r * r

        var index = 0
        while (index < page.elements.size) {
            val el = page.elements[index]
            when (el) {
                is DrawStrokeState -> {
                    val pts = el.points
                    if (pts.isEmpty()) {
                        index += 1
                        continue
                    }
                    val segments = clipStrokePoints(pts, at, r2)
                    if (segments.size == 1 && segments.first().size == pts.size) {
                        index += 1
                        continue
                    }
                    // Replace original with remaining segments.
                    page.elements.removeAt(index)
                    for ((segIndex, seg) in segments.withIndex()) {
                        if (seg.isEmpty()) continue
                        page.elements.add(
                            index + segIndex,
                            DrawStrokeState(
                                id = newElementId(),
                                tool = el.tool,
                                colorArgb = el.colorArgb,
                                width = el.width,
                                alpha = el.alpha,
                                points = mutableStateListOf<Offset>().also { it.addAll(seg) },
                                isComplete = true,
                            ),
                        )
                    }
                    index += segments.size.coerceAtLeast(0)
                }

                is DrawShapeState -> {
                    val minX = minOf(el.start.x, el.end.x)
                    val maxX = maxOf(el.start.x, el.end.x)
                    val minY = minOf(el.start.y, el.end.y)
                    val maxY = maxOf(el.start.y, el.end.y)
                    val cx = at.x.coerceIn(minX, maxX)
                    val cy = at.y.coerceIn(minY, maxY)
                    if (distanceSquared(Offset(cx, cy), at) <= r2) {
                        page.elements.removeAt(index)
                    } else {
                        index += 1
                    }
                }
            }
        }
    }

    private fun clipStrokePoints(points: List<Offset>, eraser: Offset, radiusSquared: Float): List<List<Offset>> {
        if (points.isEmpty()) return emptyList()
        val out = ArrayList<ArrayList<Offset>>()
        var current: ArrayList<Offset>? = null

        fun flush() {
            val seg = current
            if (seg != null && seg.isNotEmpty()) out.add(seg)
            current = null
        }

        for (p in points) {
            val keep = distanceSquared(p, eraser) > radiusSquared
            if (keep) {
                if (current == null) current = ArrayList()
                current?.add(p)
            } else {
                flush()
            }
        }
        flush()

        // Keep single-point segments too (dot).
        return out
    }
}

class PanToolMachine : DrawToolMachine {
    override val id: DrawToolId = DrawToolId.Pan
    override fun onDown(editor: DrawEditorState, e: ToolPointerEvent) = Unit
    override fun onMove(editor: DrawEditorState, e: ToolPointerEvent) {
        editor.viewport.translation += e.viewDelta
    }
    override fun onUp(editor: DrawEditorState, e: ToolPointerEvent) = Unit
    override fun onCancel(editor: DrawEditorState) = Unit
}
