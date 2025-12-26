package com.zhixu.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.withTimeout
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Immutable
data class RadialFabAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val ringIndex: Int,
    val angleDegrees: Float,
)

@Composable
fun DraggableRadialFab(
    primaryLabel: String,
    onClickPrimary: () -> Unit,
    actions: List<RadialFabAction>,
    onClickAction: (RadialFabAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val currentOnClickPrimary by rememberUpdatedState(onClickPrimary)
        val currentActions by rememberUpdatedState(actions)
        val currentOnClickAction by rememberUpdatedState(onClickAction)

        val density = LocalDensity.current
        val navBarsBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()

        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()

        val fabSizeDp = 56.dp
        val fabSizePx = with(density) { fabSizeDp.toPx() }
        val contentPaddingPx = with(density) { 16.dp.toPx() }
        val actionButtonSizeDp = 44.dp
        val actionButtonSizePx = with(density) { actionButtonSizeDp.toPx() }
        val actionHitRadiusPx = actionButtonSizePx * 0.65f
        val desiredMaxRadiusPx = with(density) { 192.dp.toPx() }

        var fabOffsetPx by remember { mutableStateOf<Offset?>(null) }
        var menuOpen by remember { mutableStateOf(false) }
        var hoveredActionId by remember { mutableStateOf<String?>(null) }
        var fabMeasuredSizePx by remember { mutableStateOf(fabSizePx) }

        fun clampOffsetForDrag(next: Offset): Offset {
            val maxX = (containerWidthPx - fabMeasuredSizePx).coerceAtLeast(0f)
            val maxY = (containerHeightPx - navBarsBottomPx - fabMeasuredSizePx).coerceAtLeast(0f)
            return Offset(
                x = next.x.coerceIn(0f, maxX),
                y = next.y.coerceIn(0f, maxY),
            )
        }

        LaunchedEffect(containerWidthPx, containerHeightPx, navBarsBottomPx, fabMeasuredSizePx) {
            if (containerWidthPx <= 0f || containerHeightPx <= 0f) return@LaunchedEffect

            val current = fabOffsetPx
            fabOffsetPx =
                if (current == null) {
                    val start =
                        Offset(
                            x = containerWidthPx - fabMeasuredSizePx - contentPaddingPx,
                            y = containerHeightPx - fabMeasuredSizePx - contentPaddingPx - navBarsBottomPx,
                        )
                    clampOffsetForDrag(start)
                } else {
                    clampOffsetForDrag(current)
                }
        }

        val resolvedFabOffsetPx =
            fabOffsetPx
                ?: clampOffsetForDrag(
                    Offset(
                        x = containerWidthPx - fabMeasuredSizePx - contentPaddingPx,
                        y = containerHeightPx - fabMeasuredSizePx - contentPaddingPx - navBarsBottomPx,
                    ),
                )
        val fabCenterPx = resolvedFabOffsetPx + Offset(fabMeasuredSizePx / 2, fabMeasuredSizePx / 2)

        fun computeRingRadiiPx(): List<Float> {
            val maxAllowedMenuRadiusPx =
                min(
                    a = fabCenterPx.x - contentPaddingPx - actionButtonSizePx / 2,
                    b =
                        min(
                            a = containerWidthPx - fabCenterPx.x - contentPaddingPx - actionButtonSizePx / 2,
                            b =
                                min(
                                    a = fabCenterPx.y - contentPaddingPx - actionButtonSizePx / 2,
                                    b =
                                        (containerHeightPx - navBarsBottomPx) -
                                            fabCenterPx.y -
                                            contentPaddingPx -
                                            actionButtonSizePx / 2,
                                ),
                        ),
                ).coerceAtLeast(0f)
            val maxMenuRadiusPx = min(desiredMaxRadiusPx, maxAllowedMenuRadiusPx)
            return listOf(
                maxMenuRadiusPx * 0.5f,
                maxMenuRadiusPx * 0.75f,
                maxMenuRadiusPx,
            )
        }

        if (menuOpen) {
            val ringRadiiPx = computeRingRadiiPx()

            fun actionCenterPx(action: RadialFabAction): Offset {
                val ringRadius = ringRadiiPx.getOrElse(action.ringIndex) { ringRadiiPx.lastOrNull() ?: 0f }
                val theta = (action.angleDegrees.toDouble() * PI) / 180.0
                val dx = cos(theta).toFloat() * ringRadius
                val dy = sin(theta).toFloat() * ringRadius
                return fabCenterPx + Offset(dx, dy)
            }

            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                menuOpen = false
                                hoveredActionId = null
                            }
                        },
            )

            Canvas(modifier = Modifier.matchParentSize()) {
                for (radius in ringRadiiPx) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.12f),
                        radius = radius,
                        center = fabCenterPx,
                    )
                }
            }

            for (action in actions) {
                val center = actionCenterPx(action)
                val selected = hoveredActionId == action.id
                val scale by animateFloatAsState(if (selected) 1.12f else 1.0f, label = "radialFabScale")
                val topLeft =
                    IntOffset(
                        x = (center.x - actionButtonSizePx / 2).roundToInt(),
                        y = (center.y - actionButtonSizePx / 2).roundToInt(),
                    )
                Box(
                    modifier =
                        Modifier
                            .offset { topLeft }
                            .size(actionButtonSizeDp)
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                            .background(
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                shape = CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint =
                            if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        val viewConfiguration = LocalViewConfiguration.current
        FloatingActionButton(
            onClick = {},
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier =
                Modifier
                    .offset { IntOffset(resolvedFabOffsetPx.x.roundToInt(), resolvedFabOffsetPx.y.roundToInt()) }
                    .onSizeChanged { fabMeasuredSizePx = it.toSize().width }
                    .pointerInput(containerWidthPx, containerHeightPx, navBarsBottomPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pointerId: PointerId = down.id
                            var totalDrag = Offset.Zero
                            hoveredActionId = null

                            var dragging = false
                            var longPressed = false

                            fun findChange(changes: List<PointerInputChange>): PointerInputChange? =
                                changes.firstOrNull { it.id == pointerId }

                            // Phase 1: wait for (a) long press timeout, (b) drag slop exceeded, or (c) up/cancel.
                            try {
                                withTimeout(viewConfiguration.longPressTimeoutMillis.toLong()) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = findChange(event.changes) ?: return@withTimeout
                                        if (!change.pressed) return@withTimeout

                                        val delta = change.position - change.previousPosition
                                        totalDrag += delta

                                        if (hypot(totalDrag.x, totalDrag.y) > viewConfiguration.touchSlop) {
                                            dragging = true
                                            return@withTimeout
                                        }
                                    }
                                }
                            } catch (_: PointerEventTimeoutCancellationException) {
                                longPressed = true
                                menuOpen = true
                            }

                            if (longPressed) {
                                val ringRadiiPx = computeRingRadiiPx()

                                fun actionCenterPx(action: RadialFabAction): Offset {
                                    val ringRadius = ringRadiiPx.getOrElse(action.ringIndex) { ringRadiiPx.lastOrNull() ?: 0f }
                                    val theta = (action.angleDegrees.toDouble() * PI) / 180.0
                                    val dx = cos(theta).toFloat() * ringRadius
                                    val dy = sin(theta).toFloat() * ringRadius
                                    return fabCenterPx + Offset(dx, dy)
                                }

                                fun findHoveredAction(pointerPosition: Offset): String? {
                                    var bestId: String? = null
                                    var bestDistance = Float.POSITIVE_INFINITY
                                    for (action in currentActions) {
                                        val center = actionCenterPx(action)
                                        val distance = hypot(pointerPosition.x - center.x, pointerPosition.y - center.y)
                                        if (distance <= actionHitRadiusPx && distance < bestDistance) {
                                            bestId = action.id
                                            bestDistance = distance
                                        }
                                    }
                                    return bestId
                                }

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = findChange(event.changes) ?: break
                                    hoveredActionId = findHoveredAction(change.position)
                                    change.consume()
                                    if (!change.pressed) {
                                        val selectedId = hoveredActionId
                                        menuOpen = false
                                        hoveredActionId = null
                                        if (selectedId != null) {
                                            currentActions.firstOrNull { it.id == selectedId }?.let(currentOnClickAction)
                                        }
                                        break
                                    }
                                }
                                return@awaitEachGesture
                            }

                            if (!dragging) {
                                currentOnClickPrimary()
                                return@awaitEachGesture
                            }

                            // Apply the accumulated drag once the gesture is recognized as a drag.
                            fabOffsetPx = clampOffsetForDrag((fabOffsetPx ?: resolvedFabOffsetPx) + totalDrag)

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = findChange(event.changes) ?: break
                                val delta = change.position - change.previousPosition
                                if (!change.pressed) break
                                fabOffsetPx = clampOffsetForDrag((fabOffsetPx ?: resolvedFabOffsetPx) + delta)
                                change.consume()
                            }
                        }
                    },
        ) {
            Text(text = primaryLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}
