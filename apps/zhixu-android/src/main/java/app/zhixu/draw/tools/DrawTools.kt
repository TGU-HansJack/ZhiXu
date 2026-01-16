package app.zhixu.draw.tools

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import app.zhixu.draw.ZhixuDrawBasicPoint
import app.zhixu.draw.ZhixuDrawFlatPoint
import app.zhixu.draw.ZhixuDrawRoundPoint
import app.zhixu.draw.ZhixuDrawStrokePoint
import app.zhixu.draw.ZhixuDrawTool
import app.zhixu.draw.editor.DrawEditorState
import app.zhixu.draw.editor.DrawPageState
import app.zhixu.draw.editor.DrawPressureCurve
import app.zhixu.draw.editor.DrawPressureMapping
import app.zhixu.draw.editor.DrawShapeMode
import app.zhixu.draw.editor.DrawShapeState
import app.zhixu.draw.editor.DrawStrokeState
import app.zhixu.draw.editor.DrawToolId
import app.zhixu.draw.editor.DrawTiltMapping
import app.zhixu.draw.editor.PreviewShape
import app.zhixu.draw.editor.newElementId
import app.zhixu.draw.geometry.distanceSquared
import app.zhixu.draw.geometry.lerp
import app.zhixu.draw.geometry.pointInPolygon
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

data class ToolPointerEvent(
    val viewPosition: Offset,
    val viewDelta: Offset,
    val pagePosition: Offset,
    val pageDelta: Offset,
    val pointerType: String = "touch",
    val pressure: Float = 1f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val twist: Float = 0f,
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
    private val pendingStrokeIds: ArrayList<String> = ArrayList()

    override fun onDown(editor: DrawEditorState, e: ToolPointerEvent) {
        pendingStrokeIds.clear()
        activeStrokeId = null
        handlePointer(editor, e)
    }

    override fun onMove(editor: DrawEditorState, e: ToolPointerEvent) {
        handlePointer(editor, e)
    }

    override fun onUp(editor: DrawEditorState, e: ToolPointerEvent) {
        editor.previewStroke = null
        markPendingStrokesComplete(editor)
        pendingStrokeIds.clear()
        activeStrokeId = null
    }

    override fun onCancel(editor: DrawEditorState) {
        editor.previewStroke = null
        removePendingStrokes(editor)
        pendingStrokeIds.clear()
        activeStrokeId = null
    }

    private fun handlePointer(editor: DrawEditorState, e: ToolPointerEvent) {
        val page = editor.currentPageOrNull() ?: return
        val rawPagePos = editor.viewport.viewToPage(e.viewPosition)
        val isPen = e.pointerType == "pen"

        if (isInPage(page, rawPagePos)) {
            editor.previewStroke = null
            val strokeId = activeStrokeId
            if (strokeId == null) {
                val newStrokeId = newElementId()
                val firstPoint = computePenStrokePoint(editor, clampToPage(page, rawPagePos), e, isPen)
                if (firstPoint !is ZhixuDrawBasicPoint) editor.formatVersion = max(2, editor.formatVersion)
                page.elements.add(
                    DrawStrokeState(
                        id = newStrokeId,
                        tool = ZhixuDrawTool.Pen,
                        colorArgb = editor.currentPenColorArgb,
                        width = editor.currentPenWidth,
                        alpha = 1f,
                        points = mutableStateListOf(firstPoint),
                        isComplete = false,
                    ),
                )
                pendingStrokeIds.add(newStrokeId)
                activeStrokeId = newStrokeId
                return
            }

            val stroke = page.elements.lastOrNull { it is DrawStrokeState && it.id == strokeId } as? DrawStrokeState ?: return
            val nextPoint = computePenStrokePoint(editor, clampToPage(page, rawPagePos), e, isPen)
            if (nextPoint !is ZhixuDrawBasicPoint) editor.formatVersion = max(2, editor.formatVersion)
            stroke.points.add(nextPoint)
            return
        }

        // Outside page: show a live preview path, but don't persist it to the document.
        activeStrokeId = null
        pushPreviewPoint(
            editor = editor,
            tool = ZhixuDrawTool.Pen,
            colorArgb = editor.currentPenColorArgb,
            width = editor.currentPenWidth,
            alpha = 1f,
            point = computePenStrokePoint(editor, rawPagePos, e, isPen),
        )
    }

    private fun markPendingStrokesComplete(editor: DrawEditorState) {
        val page = editor.currentPageOrNull() ?: return
        if (pendingStrokeIds.isEmpty()) return
        val pending = pendingStrokeIds.toHashSet()
        for (el in page.elements) {
            val stroke = el as? DrawStrokeState ?: continue
            if (stroke.id in pending) stroke.isComplete = true
        }
    }

    private fun removePendingStrokes(editor: DrawEditorState) {
        val page = editor.currentPageOrNull() ?: return
        if (pendingStrokeIds.isEmpty()) return
        val pending = pendingStrokeIds.toHashSet()
        page.elements.removeAll { it is DrawStrokeState && it.id in pending }
    }
}

class HighlighterToolMachine : DrawToolMachine {
    override val id: DrawToolId = DrawToolId.Highlighter
    private var activeStrokeId: String? = null
    private val pendingStrokeIds: ArrayList<String> = ArrayList()

    override fun onDown(editor: DrawEditorState, e: ToolPointerEvent) {
        pendingStrokeIds.clear()
        activeStrokeId = null
        handlePointer(editor, e)
    }

    override fun onMove(editor: DrawEditorState, e: ToolPointerEvent) {
        handlePointer(editor, e)
    }

    override fun onUp(editor: DrawEditorState, e: ToolPointerEvent) {
        editor.previewStroke = null
        markPendingStrokesComplete(editor)
        pendingStrokeIds.clear()
        activeStrokeId = null
    }

    override fun onCancel(editor: DrawEditorState) {
        editor.previewStroke = null
        removePendingStrokes(editor)
        pendingStrokeIds.clear()
        activeStrokeId = null
    }

    private fun handlePointer(editor: DrawEditorState, e: ToolPointerEvent) {
        val page = editor.currentPageOrNull() ?: return
        val rawPagePos = editor.viewport.viewToPage(e.viewPosition)

        if (isInPage(page, rawPagePos)) {
            editor.previewStroke = null
            val strokeId = activeStrokeId
            if (strokeId == null) {
                val newStrokeId = newElementId()
                page.elements.add(
                    DrawStrokeState(
                        id = newStrokeId,
                        tool = ZhixuDrawTool.Highlighter,
                        colorArgb = editor.highlighterColorArgb,
                        width = editor.highlighterWidth,
                        alpha = editor.highlighterAlpha,
                        points = mutableStateListOf(basicPoint(clampToPage(page, rawPagePos))),
                        isComplete = false,
                    ),
                )
                pendingStrokeIds.add(newStrokeId)
                activeStrokeId = newStrokeId
                return
            }

            val stroke = page.elements.lastOrNull { it is DrawStrokeState && it.id == strokeId } as? DrawStrokeState ?: return
            stroke.points.add(basicPoint(clampToPage(page, rawPagePos)))
            return
        }

        // Outside page: show a live preview path, but don't persist it to the document.
        activeStrokeId = null
        pushPreviewPoint(
            editor = editor,
            tool = ZhixuDrawTool.Highlighter,
            colorArgb = editor.highlighterColorArgb,
            width = editor.highlighterWidth,
            alpha = editor.highlighterAlpha,
            point = basicPoint(rawPagePos),
        )
    }

    private fun markPendingStrokesComplete(editor: DrawEditorState) {
        val page = editor.currentPageOrNull() ?: return
        if (pendingStrokeIds.isEmpty()) return
        val pending = pendingStrokeIds.toHashSet()
        for (el in page.elements) {
            val stroke = el as? DrawStrokeState ?: continue
            if (stroke.id in pending) stroke.isComplete = true
        }
    }

    private fun removePendingStrokes(editor: DrawEditorState) {
        val page = editor.currentPageOrNull() ?: return
        if (pendingStrokeIds.isEmpty()) return
        val pending = pendingStrokeIds.toHashSet()
        page.elements.removeAll { it is DrawStrokeState && it.id in pending }
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
                    for (p in pts) if (pointInPolygon(Offset(p.x, p.y), polygon)) inside++
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
                                points = mutableStateListOf<ZhixuDrawStrokePoint>().also { it.addAll(seg) },
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

    private fun clipStrokePoints(
        points: List<ZhixuDrawStrokePoint>,
        eraser: Offset,
        radiusSquared: Float,
    ): List<List<ZhixuDrawStrokePoint>> {
        if (points.isEmpty()) return emptyList()
        val out = ArrayList<ArrayList<ZhixuDrawStrokePoint>>()
        var current: ArrayList<ZhixuDrawStrokePoint>? = null

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

private fun isInPage(page: DrawPageState, point: Offset): Boolean {
    val w = page.width.coerceAtLeast(0f)
    val h = page.height.coerceAtLeast(0f)
    return point.x >= 0f && point.x <= w && point.y >= 0f && point.y <= h
}

private fun clampToPage(page: DrawPageState, point: Offset): Offset {
    val w = page.width.coerceAtLeast(0f)
    val h = page.height.coerceAtLeast(0f)
    return Offset(point.x.coerceIn(0f, w), point.y.coerceIn(0f, h))
}

private fun pushPreviewPoint(
    editor: DrawEditorState,
    tool: ZhixuDrawTool,
    colorArgb: Int,
    width: Float,
    alpha: Float,
    point: ZhixuDrawStrokePoint,
) {
    val preview = editor.previewStroke
    val canReuse =
        preview != null &&
            preview.tool == tool &&
            preview.colorArgb == colorArgb &&
            preview.width == width &&
            preview.alpha == alpha

    if (!canReuse) {
        editor.previewStroke =
            DrawStrokeState(
                id = "preview",
                tool = tool,
                colorArgb = colorArgb,
                width = width,
                alpha = alpha,
                points = mutableStateListOf(point),
                isComplete = false,
            )
        return
    }

    preview.points.add(point)
}

private fun basicPoint(pos: Offset): ZhixuDrawStrokePoint = ZhixuDrawBasicPoint(x = pos.x, y = pos.y)

private fun distanceSquared(a: ZhixuDrawStrokePoint, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}

private fun clamp01(v: Float): Float = v.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

private fun pressureStrength(editor: DrawEditorState, pressure: Float): Float {
    val p = clamp01(pressure)
    return when (editor.pressureCurve) {
        DrawPressureCurve.Linear -> p
        DrawPressureCurve.Soft -> p.pow(0.6f)
        DrawPressureCurve.Hard -> p.pow(1.8f)
        DrawPressureCurve.Custom -> p.pow(editor.pressureCurveGamma.coerceIn(0.2f, 3f))
    }
}

private fun tiltStrength(tiltX: Float, tiltY: Float): Float {
    val tx = if (tiltX.isFinite()) tiltX else 0f
    val ty = if (tiltY.isFinite()) tiltY else 0f
    return clamp01(hypot(tx.toDouble(), ty.toDouble()).toFloat() / 90f)
}

private fun computePenStrokePoint(
    editor: DrawEditorState,
    posInPage: Offset,
    e: ToolPointerEvent,
    isPen: Boolean,
): ZhixuDrawStrokePoint {
    val baseWidth = editor.currentPenWidth.coerceAtLeast(0.2f)
    val baseAlpha = 1f

    val applyPressure = isPen && editor.pressureEnabled
    val applyTilt = isPen && editor.tiltEnabled

    val strength = if (applyPressure) pressureStrength(editor, e.pressure) else 1f
    val mapping = editor.pressureMapping
    val minWidthFactor = 0.2f
    val minAlphaFactor = 0.15f

    var width = baseWidth
    var alpha = baseAlpha

    if (applyPressure) {
        if (mapping == DrawPressureMapping.Width || mapping == DrawPressureMapping.Both) {
            width = baseWidth * (minWidthFactor + (1f - minWidthFactor) * strength)
        }
        if (mapping == DrawPressureMapping.Opacity || mapping == DrawPressureMapping.Both) {
            alpha = baseAlpha * (minAlphaFactor + (1f - minAlphaFactor) * strength)
        }
    }

    val t = if (applyTilt) tiltStrength(e.tiltX, e.tiltY) else 0f
    if (applyTilt && editor.tiltMapping == DrawTiltMapping.Width) {
        width *= 1f + t * 1.2f
    }

    val canUseFlatBrush = applyTilt && (editor.tiltMapping == DrawTiltMapping.Angle || editor.tiltMapping == DrawTiltMapping.Shading) && t >= 0.12f
    if (canUseFlatBrush) {
        val angle = atan2(e.tiltY.toDouble(), e.tiltX.toDouble()).toFloat()
        val r = max(0.05f, width / 2f)
        val baseRatio = 0.35f
        var rx = r
        var ry = r * baseRatio
        if (editor.tiltMapping == DrawTiltMapping.Shading) {
            rx = r * (1f + t * 1.5f)
            ry = max(0.03f, r * (1f - t * 0.7f))
        }
        return ZhixuDrawFlatPoint(
            x = posInPage.x,
            y = posInPage.y,
            rx = rx,
            ry = ry,
            angle = angle,
            alpha = clamp01(alpha),
        )
    }

    if (applyPressure || (applyTilt && editor.tiltMapping == DrawTiltMapping.Width)) {
        return ZhixuDrawRoundPoint(
            x = posInPage.x,
            y = posInPage.y,
            width = width.coerceAtLeast(0.2f),
            alpha = clamp01(alpha),
        )
    }

    return ZhixuDrawBasicPoint(x = posInPage.x, y = posInPage.y)
}
