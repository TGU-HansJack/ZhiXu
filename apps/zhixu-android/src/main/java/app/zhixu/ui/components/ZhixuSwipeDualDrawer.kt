package app.zhixu.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private enum class ZhixuDrawerSide { Left, Right }

@Composable
fun ZhixuSwipeDualDrawer(
    enabled: Boolean,
    openGestureEnabled: Boolean = true,
    resetKey: Any?,
    openLeftToken: Long = 0L,
    openRightToken: Long = 0L,
    threshold: Dp = 96.dp,
    fullScreenSwipeToOpen: Boolean = true,
    edgeSwipeWidth: Dp = 24.dp,
    gestureExcludeBottomHeight: Dp = 0.dp,
    drawerScrimMaxAlpha: Float = 0.36f,
    leftDrawerContent: @Composable (modifier: Modifier, closeDrawer: () -> Unit, isOpen: Boolean) -> Unit,
    rightDrawerContent: @Composable (modifier: Modifier, closeDrawer: () -> Unit, isOpen: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val thresholdPx = with(density) { threshold.toPx() }
    val edgeSwipeWidthPx = with(density) { edgeSwipeWidth.toPx() }
    val gestureExcludeBottomHeightPx = with(density) { gestureExcludeBottomHeight.toPx() }

    var leftWidthPx by remember { mutableFloatStateOf(0f) }
    var rightWidthPx by remember { mutableFloatStateOf(0f) }

    var dragging by remember { mutableStateOf(false) }
    var leftTargetPx by remember { mutableFloatStateOf(0f) }
    var rightTargetPx by remember { mutableFloatStateOf(0f) }

    var pendingOpenSide by remember { mutableStateOf<ZhixuDrawerSide?>(null) }
    var lastOpenLeftToken by remember { mutableLongStateOf(0L) }
    var lastOpenRightToken by remember { mutableLongStateOf(0L) }

    val leftOffsetPx by animateFloatAsState(
        targetValue = leftTargetPx,
        animationSpec = if (dragging) snap() else tween(durationMillis = 220, easing = LinearOutSlowInEasing),
        label = "leftDrawerOffsetPx",
    )
    val rightOffsetPx by animateFloatAsState(
        targetValue = rightTargetPx,
        animationSpec = if (dragging) snap() else tween(durationMillis = 220, easing = LinearOutSlowInEasing),
        label = "rightDrawerOffsetPx",
    )

    fun closeBoth() {
        dragging = false
        pendingOpenSide = null
        leftTargetPx = 0f
        rightTargetPx = 0f
    }

    fun closeLeft() {
        dragging = false
        pendingOpenSide = null
        leftTargetPx = 0f
    }

    fun closeRight() {
        dragging = false
        pendingOpenSide = null
        rightTargetPx = 0f
    }

    fun requestOpen(side: ZhixuDrawerSide) {
        dragging = false
        if (side == ZhixuDrawerSide.Left) {
            rightTargetPx = 0f
            if (leftWidthPx > 0f) {
                leftTargetPx = leftWidthPx
                pendingOpenSide = null
            } else {
                pendingOpenSide = ZhixuDrawerSide.Left
            }
        } else {
            leftTargetPx = 0f
            if (rightWidthPx > 0f) {
                rightTargetPx = rightWidthPx
                pendingOpenSide = null
            } else {
                pendingOpenSide = ZhixuDrawerSide.Right
            }
        }
    }

    LaunchedEffect(enabled, resetKey) { closeBoth() }

    LaunchedEffect(openLeftToken) {
        if (openLeftToken != lastOpenLeftToken) {
            lastOpenLeftToken = openLeftToken
            requestOpen(ZhixuDrawerSide.Left)
        }
    }

    LaunchedEffect(openRightToken) {
        if (openRightToken != lastOpenRightToken) {
            lastOpenRightToken = openRightToken
            requestOpen(ZhixuDrawerSide.Right)
        }
    }

    LaunchedEffect(leftWidthPx, rightWidthPx, pendingOpenSide) {
        val side = pendingOpenSide ?: return@LaunchedEffect
        if (side == ZhixuDrawerSide.Left && leftWidthPx > 0f) requestOpen(ZhixuDrawerSide.Left)
        if (side == ZhixuDrawerSide.Right && rightWidthPx > 0f) requestOpen(ZhixuDrawerSide.Right)
    }

    val leftProgress = if (leftWidthPx <= 0f) 0f else (leftOffsetPx / leftWidthPx).coerceIn(0f, 1f)
    val rightProgress = if (rightWidthPx <= 0f) 0f else (rightOffsetPx / rightWidthPx).coerceIn(0f, 1f)
    val scrimProgress = max(leftProgress, rightProgress)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(enabled, openGestureEnabled, fullScreenSwipeToOpen, leftWidthPx, rightWidthPx, gestureExcludeBottomHeightPx) {
                    if (!enabled) return@pointerInput
                    if (leftWidthPx <= 0f && rightWidthPx <= 0f) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y

                        var activeSide: ZhixuDrawerSide? = null
                        var gestureDragging = false
                        var cancelled = false

                        val startLeft = leftOffsetPx
                        val startRight = rightOffsetPx
                        val slop = viewConfig.touchSlop

                        leftTargetPx = startLeft
                        rightTargetPx = startRight

                        val startedFromOpenDrawer: ZhixuDrawerSide? =
                            when {
                                startLeft > 1f -> ZhixuDrawerSide.Left
                                startRight > 1f -> ZhixuDrawerSide.Right
                                else -> null
                            }

                        if (startedFromOpenDrawer == null) {
                            if (!openGestureEnabled) return@awaitEachGesture
                            if (gestureExcludeBottomHeightPx > 0f && startY >= (size.height - gestureExcludeBottomHeightPx)) {
                                return@awaitEachGesture
                            }
                            if (!fullScreenSwipeToOpen) {
                                val edgeSide: ZhixuDrawerSide? =
                                    when {
                                        startX <= edgeSwipeWidthPx -> ZhixuDrawerSide.Left
                                        startX >= (size.width - edgeSwipeWidthPx) -> ZhixuDrawerSide.Right
                                        else -> null
                                    }
                                if (edgeSide == null) return@awaitEachGesture
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            if (!change.pressed) break

                            val dx = change.position.x - startX
                            val dy = change.position.y - startY

                            if (!gestureDragging) {
                                val absDx = abs(dx)
                                val absDy = abs(dy)

                                if (absDy > slop && absDy > absDx) {
                                    cancelled = true
                                    break
                                }

                                if (absDx > slop && absDx > absDy * 1.15f) {
                                    val chosen =
                                        when {
                                            startedFromOpenDrawer != null -> startedFromOpenDrawer
                                            fullScreenSwipeToOpen -> if (dx >= 0f) ZhixuDrawerSide.Left else ZhixuDrawerSide.Right
                                            startX <= edgeSwipeWidthPx -> ZhixuDrawerSide.Left
                                            startX >= (size.width - edgeSwipeWidthPx) -> ZhixuDrawerSide.Right
                                            else -> null
                                        }
                                    if (chosen == null) {
                                        cancelled = true
                                        break
                                    }

                                    val available =
                                        when (chosen) {
                                            ZhixuDrawerSide.Left -> leftWidthPx > 0f
                                            ZhixuDrawerSide.Right -> rightWidthPx > 0f
                                        }

                                    if (!available) {
                                        cancelled = true
                                        break
                                    }

                                    activeSide = chosen
                                    gestureDragging = true
                                    dragging = true

                                    if (activeSide == ZhixuDrawerSide.Left) rightTargetPx = 0f
                                    if (activeSide == ZhixuDrawerSide.Right) leftTargetPx = 0f
                                } else {
                                    continue
                                }
                            }

                            when (activeSide) {
                                ZhixuDrawerSide.Left -> {
                                    val newLeft = (startLeft + dx).coerceIn(0f, leftWidthPx)
                                    leftTargetPx = newLeft
                                    rightTargetPx = 0f
                                    change.consume()
                                }
                                ZhixuDrawerSide.Right -> {
                                    val newRight = (startRight - dx).coerceIn(0f, rightWidthPx)
                                    rightTargetPx = newRight
                                    leftTargetPx = 0f
                                    change.consume()
                                }
                                null -> Unit
                            }
                        }

                        if (!gestureDragging || cancelled) return@awaitEachGesture

                        dragging = false

                        when (activeSide) {
                            ZhixuDrawerSide.Left -> {
                                val current = leftTargetPx
                                val shouldOpen =
                                    when {
                                        startLeft <= 1f -> current >= thresholdPx
                                        startLeft >= leftWidthPx - 1f -> (leftWidthPx - current) < thresholdPx
                                        else -> current >= leftWidthPx / 2f
                                    }
                                if (shouldOpen) {
                                    leftTargetPx = leftWidthPx
                                } else {
                                    leftTargetPx = 0f
                                }
                                rightTargetPx = 0f
                            }
                            ZhixuDrawerSide.Right -> {
                                val current = rightTargetPx
                                val shouldOpen =
                                    when {
                                        startRight <= 1f -> current >= thresholdPx
                                        startRight >= rightWidthPx - 1f -> (rightWidthPx - current) < thresholdPx
                                        else -> current >= rightWidthPx / 2f
                                    }
                                if (shouldOpen) {
                                    rightTargetPx = rightWidthPx
                                } else {
                                    rightTargetPx = 0f
                                }
                                leftTargetPx = 0f
                            }
                            null -> Unit
                        }
                    }
                },
    ) {
        content()

        if (scrimProgress > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = drawerScrimMaxAlpha * scrimProgress))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startX = down.position.x
                                val startY = down.position.y
                                val slop = viewConfig.touchSlop

                                var moved = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                    if (!change.pressed) break
                                    val dx = change.position.x - startX
                                    val dy = change.position.y - startY
                                    if (abs(dx) > slop || abs(dy) > slop) {
                                        moved = true
                                        break
                                    }
                                }
                                if (!moved) closeBoth()
                            }
                        },
            )
        }

        val leftOffsetX = if (leftWidthPx <= 0f) -100000 else (-leftWidthPx + leftOffsetPx).roundToInt()
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .offset { IntOffset(leftOffsetX, 0) }
                    .align(Alignment.CenterStart),
        ) {
            leftDrawerContent(
                Modifier.onSizeChanged { leftWidthPx = it.width.toFloat() },
                ::closeLeft,
                leftProgress >= 0.999f,
            )
        }

        val rightOffsetX = if (rightWidthPx <= 0f) 100000 else (rightWidthPx - rightOffsetPx).roundToInt()
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .offset { IntOffset(rightOffsetX, 0) }
                    .align(Alignment.CenterEnd),
        ) {
            rightDrawerContent(
                Modifier.onSizeChanged { rightWidthPx = it.width.toFloat() },
                ::closeRight,
                rightProgress >= 0.999f,
            )
        }
    }
}
