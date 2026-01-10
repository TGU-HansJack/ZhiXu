package app.zhixu.draw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import app.zhixu.draw.ZhixuDrawElement
import app.zhixu.draw.ZhixuDrawPage
import app.zhixu.draw.ZhixuDrawShape
import app.zhixu.draw.ZhixuDrawShapeElement
import app.zhixu.draw.ZhixuDrawStroke

@Composable
fun DrawDocumentPreviewRow(
    pages: List<ZhixuDrawPage>?,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cellWidth = maxWidth / 4
        val paddedPages = padToFour(pages.orEmpty())

        val rowHeight =
            paddedPages
                .map { page ->
                    val w = page?.width?.takeIf { it > 1f } ?: DefaultPageWidth
                    val h = page?.height?.takeIf { it > 1f } ?: DefaultPageHeight
                    cellWidth * (h / w)
                }
                .maxOrNull()
                ?.coerceAtMost(maxHeight)
                ?: maxHeight

        Row(modifier = Modifier.fillMaxWidth().height(rowHeight)) {
            for (page in paddedPages) {
                DrawPageThumbnail(
                    page = page,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DrawPageThumbnail(
    page: ZhixuDrawPage?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxHeight().clipToBounds()) {
        val w = page?.width?.takeIf { it > 1f } ?: DefaultPageWidth
        val h = page?.height?.takeIf { it > 1f } ?: DefaultPageHeight

        val scale = (size.width / w).coerceAtLeast(0.0001f)
        val naturalHeight = h * scale
        val yOffset = (size.height - naturalHeight) / 2f

        withTransform(
            transformBlock = {
                translate(0f, yOffset)
                scale(scale, scale, pivot = Offset.Zero)
            },
        ) {
            drawRect(
                color = Color.White,
                topLeft = Offset.Zero,
                size = Size(w, h),
            )
            drawRect(
                color = Color(0xFFDDDDDD),
                topLeft = Offset.Zero,
                size = Size(w, h),
                style = Stroke(width = 1f / scale),
            )
            page?.elements?.forEach { el ->
                drawElement(el)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawElement(el: ZhixuDrawElement) {
    when (el) {
        is ZhixuDrawStroke -> {
            val pts = el.points
            if (pts.isEmpty()) return
            val path = buildPath(pts)
            drawPath(
                path = path,
                color = Color(el.colorArgb).copy(alpha = el.alpha.coerceIn(0f, 1f)),
                style = Stroke(width = el.width.coerceAtLeast(0.2f), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        is ZhixuDrawShapeElement -> {
            val color = Color(el.colorArgb).copy(alpha = el.alpha.coerceIn(0f, 1f))
            val w = el.width.coerceAtLeast(0.2f)
            val rect = rectFromPoints(el.start, el.end)
            when (el.shape) {
                ZhixuDrawShape.Line -> drawLine(color = color, start = el.start, end = el.end, strokeWidth = w, cap = StrokeCap.Round)
                ZhixuDrawShape.Rectangle -> drawRect(color = color, topLeft = rect.topLeft, size = rect.size, style = Stroke(width = w))
                ZhixuDrawShape.Ellipse -> drawOval(color = color, topLeft = rect.topLeft, size = rect.size, style = Stroke(width = w))
            }
        }
    }
}

private fun buildPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        path.lineTo(points[i].x, points[i].y)
    }
    return path
}

private fun rectFromPoints(a: Offset, b: Offset): Rect {
    val left = minOf(a.x, b.x)
    val right = maxOf(a.x, b.x)
    val top = minOf(a.y, b.y)
    val bottom = maxOf(a.y, b.y)
    return Rect(left, top, right, bottom)
}

private fun padToFour(pages: List<ZhixuDrawPage>): List<ZhixuDrawPage?> {
    val out = ArrayList<ZhixuDrawPage?>(4)
    for (p in pages.take(4)) out += p
    while (out.size < 4) out += null
    return out
}

private const val DefaultPageWidth: Float = 595f
private const val DefaultPageHeight: Float = 842f
