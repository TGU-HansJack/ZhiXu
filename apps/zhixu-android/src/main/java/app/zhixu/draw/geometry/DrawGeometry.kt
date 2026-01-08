package app.zhixu.draw.geometry

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

fun distanceSquared(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}

fun pointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        val intersects =
            ((pi.y > point.y) != (pj.y > point.y)) &&
                (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y + 0.0000001f) + pi.x)
        if (intersects) inside = !inside
        j = i
    }
    return inside
}

fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

fun lerp(a: Offset, b: Offset, t: Float): Offset = Offset(lerp(a.x, b.x, t), lerp(a.y, b.y, t))

fun approximatelyZero(value: Float): Boolean = abs(value) < 0.0001f

