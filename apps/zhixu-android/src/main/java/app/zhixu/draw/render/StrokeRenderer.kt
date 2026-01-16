package app.zhixu.draw.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.zhixu.draw.ZhixuDrawBasicPoint
import app.zhixu.draw.ZhixuDrawFlatPoint
import app.zhixu.draw.ZhixuDrawRoundPoint
import app.zhixu.draw.ZhixuDrawStrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

private fun clamp01(v: Float): Float = v.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

internal fun argbWithAlpha(colorArgb: Int, alpha: Float): Int {
    val a = (clamp01(alpha) * 255f).roundToInt().coerceIn(0, 255)
    return (colorArgb and 0x00FFFFFF) or (a shl 24)
}

private data class FlatPointStyle(
    val x: Float,
    val y: Float,
    val rx: Float,
    val ry: Float,
    val rotRad: Float,
    val alpha: Float,
)

internal fun drawStrokePointsToCanvas(
    canvas: Canvas,
    paint: Paint,
    points: List<ZhixuDrawStrokePoint>,
    colorArgb: Int,
    fallbackWidth: Float,
    fallbackAlpha: Float,
    scale: Float = 1f,
) {
    if (points.isEmpty()) return

    val baseWidth = fallbackWidth.coerceAtLeast(0.2f)
    val baseAlpha = clamp01(fallbackAlpha)

    var hasRoundStyle = false
    var hasFlatStyle = false
    for (p in points) {
        when (p) {
            is ZhixuDrawFlatPoint -> {
                hasFlatStyle = true
                break
            }
            is ZhixuDrawRoundPoint -> hasRoundStyle = true
            else -> Unit
        }
    }

    if (!hasRoundStyle && !hasFlatStyle) {
        paint.color = argbWithAlpha(colorArgb, baseAlpha)
        if (points.size == 1) {
            val p = points.first()
            paint.style = Paint.Style.FILL
            canvas.drawCircle(p.x * scale, p.y * scale, (baseWidth / 2f) * scale, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (baseWidth * scale).coerceAtLeast(0.2f)
            val path = Path()
            path.moveTo(points[0].x * scale, points[0].y * scale)
            for (i in 1 until points.size) {
                val p = points[i]
                path.lineTo(p.x * scale, p.y * scale)
            }
            canvas.drawPath(path, paint)
        }
        return
    }

    if (hasFlatStyle) {
        fun pointStyle(p: ZhixuDrawStrokePoint): FlatPointStyle {
            val x = p.x
            val y = p.y
            return when (p) {
                is ZhixuDrawFlatPoint -> {
                    val rx = p.rx.takeIf { it.isFinite() }?.let { max(0.03f, abs(it)) } ?: (baseWidth / 2f)
                    val ry = p.ry.takeIf { it.isFinite() }?.let { max(0.03f, abs(it)) } ?: (baseWidth / 2f)
                    val rot = p.angle.takeIf { it.isFinite() } ?: 0f
                    val a = clamp01(p.alpha)
                    FlatPointStyle(x = x, y = y, rx = rx, ry = ry, rotRad = rot, alpha = a)
                }
                is ZhixuDrawRoundPoint -> {
                    val w = p.width.takeIf { it.isFinite() }?.coerceAtLeast(0.2f) ?: baseWidth
                    val a = clamp01(p.alpha)
                    FlatPointStyle(x = x, y = y, rx = w / 2f, ry = w / 2f, rotRad = 0f, alpha = a)
                }
                is ZhixuDrawBasicPoint -> FlatPointStyle(x = x, y = y, rx = baseWidth / 2f, ry = baseWidth / 2f, rotRad = 0f, alpha = baseAlpha)
                else -> FlatPointStyle(x = x, y = y, rx = baseWidth / 2f, ry = baseWidth / 2f, rotRad = 0f, alpha = baseAlpha)
            }
        }

        val oval = RectF()

        fun stamp(style: FlatPointStyle) {
            val rx = max(0.03f, abs(style.rx)) * scale
            val ry = max(0.03f, abs(style.ry)) * scale
            paint.style = Paint.Style.FILL
            paint.color = argbWithAlpha(colorArgb, style.alpha)
            canvas.save()
            canvas.translate(style.x * scale, style.y * scale)
            val deg = style.rotRad * 180f / PI.toFloat()
            if (deg != 0f) canvas.rotate(deg)
            oval.set(-rx, -ry, rx, ry)
            canvas.drawOval(oval, paint)
            canvas.restore()
        }

        if (points.size == 1) {
            stamp(pointStyle(points[0]))
            return
        }

        var prev = pointStyle(points[0])
        stamp(prev)
        for (i in 1 until points.size) {
            val next = pointStyle(points[i])
            val dx = next.x - prev.x
            val dy = next.y - prev.y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0f)
            val step = max(0.5f, minOf(prev.rx, prev.ry, next.rx, next.ry) * 0.6f)
            val n = max(1, ceil((dist / step).toDouble()).toInt())
            for (j in 1..n) {
                val t = j.toFloat() / n.toFloat()
                stamp(
                    FlatPointStyle(
                        x = prev.x + dx * t,
                        y = prev.y + dy * t,
                        rx = prev.rx + (next.rx - prev.rx) * t,
                        ry = prev.ry + (next.ry - prev.ry) * t,
                        rotRad = prev.rotRad + (next.rotRad - prev.rotRad) * t,
                        alpha = prev.alpha + (next.alpha - prev.alpha) * t,
                    ),
                )
            }
            prev = next
        }
        return
    }

    fun pointStyle(p: ZhixuDrawStrokePoint): Pair<Float, Float> {
        return when (p) {
            is ZhixuDrawRoundPoint -> (p.width.takeIf { it.isFinite() }?.coerceAtLeast(0.2f) ?: baseWidth) to clamp01(p.alpha)
            else -> baseWidth to baseAlpha
        }
    }

    if (points.size == 1) {
        val p = points[0]
        val (w, a) = pointStyle(p)
        paint.style = Paint.Style.FILL
        paint.color = argbWithAlpha(colorArgb, a)
        canvas.drawCircle(p.x * scale, p.y * scale, (w / 2f) * scale, paint)
        return
    }

    paint.style = Paint.Style.STROKE
    var prev = points[0]
    var prevStyle = pointStyle(prev)
    for (i in 1 until points.size) {
        val next = points[i]
        val nextStyle = pointStyle(next)
        val w = (prevStyle.first + nextStyle.first) / 2f
        val a = (prevStyle.second + nextStyle.second) / 2f
        paint.color = argbWithAlpha(colorArgb, a)
        paint.strokeWidth = (w * scale).coerceAtLeast(0.2f)
        canvas.drawLine(prev.x * scale, prev.y * scale, next.x * scale, next.y * scale, paint)
        prev = next
        prevStyle = nextStyle
    }
}
