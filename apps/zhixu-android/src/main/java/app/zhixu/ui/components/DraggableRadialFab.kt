package app.zhixu.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import app.zhixu.data.dataStore
import app.zhixu.ui.Ionicons
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
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
@OptIn(FlowPreview::class)
fun DraggableRadialFab(
    primaryLabel: String,
    onClickPrimary: () -> Unit,
    actions: List<RadialFabAction>,
    onClickAction: (RadialFabAction) -> Unit,
    modifier: Modifier = Modifier,
    persistenceKey: String = "draggable_radial_fab",
    adaptiveMenuLayout: Boolean = true,
    edgeHideEnabled: Boolean = true,
    edgePeekFraction: Float = 0.25f,
    autoHideDelayMs: Long = 1400,
    edgePadding: Dp = 16.dp,
) {
    BoxWithConstraints(modifier = modifier) {
        val currentOnClickPrimary by rememberUpdatedState(onClickPrimary)
        val currentActions by rememberUpdatedState(actions)
        val currentOnClickAction by rememberUpdatedState(onClickAction)
        val context = LocalContext.current

        val density = LocalDensity.current
        val navBarsBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()

        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()

        val fabSizeDp = 56.dp
        val fabSizePx = with(density) { fabSizeDp.toPx() }
        val contentPaddingPx = with(density) { edgePadding.toPx() }
        val actionButtonSizeDp = 48.dp
        val actionButtonSizePx = with(density) { actionButtonSizeDp.toPx() }
        val actionHitRadiusPx = actionButtonSizePx * 0.95f
        val desiredMaxRadiusPx = with(density) { 192.dp.toPx() }

        var fabOffsetPx by remember { mutableStateOf<Offset?>(null) }
        var menuOpen by remember { mutableStateOf(false) }
        var menuVisible by remember { mutableStateOf(false) }
        var hoveredActionId by remember { mutableStateOf<String?>(null) }
        var fabMeasuredSizePx by remember { mutableStateOf(fabSizePx) }
        var persistedFracOffset by remember(persistenceKey) { mutableStateOf<Offset?>(null) }
        var persistedOffsetLoaded by remember(persistenceKey) { mutableStateOf(false) }
        var initialOffsetApplied by remember(persistenceKey) { mutableStateOf(false) }
        var dockEdge by remember(persistenceKey) { mutableStateOf<DockEdge?>(null) }
        var edgeCollapsed by remember(persistenceKey) { mutableStateOf(false) }
        var interactionTick by remember(persistenceKey) { mutableStateOf(0) }

        fun clampOffsetForDrag(next: Offset): Offset {
            val maxX = (containerWidthPx - fabMeasuredSizePx).coerceAtLeast(0f)
            val maxY = (containerHeightPx - navBarsBottomPx - fabMeasuredSizePx).coerceAtLeast(0f)
            return Offset(
                x = next.x.coerceIn(0f, maxX),
                y = next.y.coerceIn(0f, maxY),
            )
        }

        val offsetFracXKey = remember(persistenceKey) { floatPreferencesKey("${persistenceKey}_offset_frac_x") }
        val offsetFracYKey = remember(persistenceKey) { floatPreferencesKey("${persistenceKey}_offset_frac_y") }

        LaunchedEffect(persistenceKey) {
            val loaded =
                context.dataStore.data
                    .map { prefs ->
                        val x = prefs[offsetFracXKey]
                        val y = prefs[offsetFracYKey]
                        if (x == null || y == null) null else Offset(x, y)
                    }
                    .first()
            persistedFracOffset = loaded
            persistedOffsetLoaded = true
        }

        LaunchedEffect(
            containerWidthPx,
            containerHeightPx,
            navBarsBottomPx,
            fabMeasuredSizePx,
            persistedOffsetLoaded,
            persistedFracOffset,
        ) {
            if (containerWidthPx <= 0f || containerHeightPx <= 0f) return@LaunchedEffect

            val current = fabOffsetPx
            if (current != null) {
                fabOffsetPx = clampOffsetForDrag(current)
                if (!initialOffsetApplied) initialOffsetApplied = true
                return@LaunchedEffect
            }

            if (initialOffsetApplied) return@LaunchedEffect
            if (!persistedOffsetLoaded) return@LaunchedEffect

            val start =
                persistedFracOffset?.let { frac ->
                    val maxX = (containerWidthPx - fabMeasuredSizePx).coerceAtLeast(0f)
                    val maxY = (containerHeightPx - navBarsBottomPx - fabMeasuredSizePx).coerceAtLeast(0f)
                    Offset(
                        x = (frac.x.coerceIn(0f, 1f) * maxX),
                        y = (frac.y.coerceIn(0f, 1f) * maxY),
                    )
                }
                    ?: Offset(
                        x = containerWidthPx - fabMeasuredSizePx - contentPaddingPx,
                        y = containerHeightPx - fabMeasuredSizePx - contentPaddingPx - navBarsBottomPx,
                    )

            fabOffsetPx = clampOffsetForDrag(start)
            initialOffsetApplied = true
        }

        val resolvedFabOffsetPx =
            fabOffsetPx
                ?: clampOffsetForDrag(
                    Offset(
                        x = containerWidthPx - fabMeasuredSizePx - contentPaddingPx,
                        y = containerHeightPx - fabMeasuredSizePx - contentPaddingPx - navBarsBottomPx,
                    ),
                )
        val maxX = (containerWidthPx - fabMeasuredSizePx).coerceAtLeast(0f)
        val maxY = (containerHeightPx - navBarsBottomPx - fabMeasuredSizePx).coerceAtLeast(0f)

        val edgeDockThresholdPx = with(density) { 24.dp.toPx() }
        fun computeDockEdge(offsetPx: Offset): DockEdge? =
            when {
                offsetPx.x <= edgeDockThresholdPx -> DockEdge.Left
                offsetPx.x >= (maxX - edgeDockThresholdPx) -> DockEdge.Right
                else -> null
            }

        LaunchedEffect(edgeHideEnabled, dockEdge, resolvedFabOffsetPx, maxX) {
            if (!edgeHideEnabled) return@LaunchedEffect
            val docked = computeDockEdge(resolvedFabOffsetPx) != null
            if (!docked) {
                edgeCollapsed = false
            }
        }

        LaunchedEffect(edgeHideEnabled, initialOffsetApplied, maxX, maxY) {
            if (!edgeHideEnabled) return@LaunchedEffect
            if (!initialOffsetApplied) return@LaunchedEffect
            if (dockEdge != null) return@LaunchedEffect
            val base = fabOffsetPx ?: return@LaunchedEffect
            dockEdge = computeDockEdge(base)
        }

        LaunchedEffect(edgeHideEnabled, dockEdge, interactionTick, menuOpen, menuVisible, maxX, maxY) {
            if (!edgeHideEnabled) return@LaunchedEffect
            if (menuOpen || menuVisible) return@LaunchedEffect
            val edge = dockEdge ?: computeDockEdge(resolvedFabOffsetPx) ?: return@LaunchedEffect
            if (maxX <= 0f && maxY <= 0f) return@LaunchedEffect
            edgeCollapsed = false
            delay(autoHideDelayMs)
            // Re-check: still at edge, still same edge.
            val still = computeDockEdge(fabOffsetPx ?: resolvedFabOffsetPx)
            if (still == edge && !menuOpen && !menuVisible) {
                dockEdge = edge
                edgeCollapsed = true
            }
        }

        val hideShiftPx =
            (fabMeasuredSizePx * (1f - edgePeekFraction.coerceIn(0.1f, 1f))).coerceAtLeast(0f)
        val hideAnimPx by animateFloatAsState(
            targetValue = if (edgeHideEnabled && edgeCollapsed && dockEdge != null) hideShiftPx else 0f,
            label = "fabEdgeHideShift",
        )
        val isEdgeHidden = edgeHideEnabled && dockEdge != null && hideAnimPx > 0.5f
        val renderOffsetPx =
            when (dockEdge) {
                DockEdge.Left -> resolvedFabOffsetPx.copy(x = resolvedFabOffsetPx.x - hideAnimPx)
                DockEdge.Right -> resolvedFabOffsetPx.copy(x = resolvedFabOffsetPx.x + hideAnimPx)
                null -> resolvedFabOffsetPx
            }

        val fabCenterPx = renderOffsetPx + Offset(fabMeasuredSizePx / 2, fabMeasuredSizePx / 2)

        fun availableSpacePx(): AvailableSpacePx {
            val half = actionButtonSizePx / 2
            return AvailableSpacePx(
                left = (fabCenterPx.x - contentPaddingPx - half).coerceAtLeast(0f),
                right = (containerWidthPx - fabCenterPx.x - contentPaddingPx - half).coerceAtLeast(0f),
                top = (fabCenterPx.y - contentPaddingPx - half).coerceAtLeast(0f),
                bottom = ((containerHeightPx - navBarsBottomPx) - fabCenterPx.y - contentPaddingPx - half).coerceAtLeast(0f),
            )
        }

        fun maxRadiusForAnglePx(space: AvailableSpacePx, angleDegrees: Float): Float {
            val theta = (angleDegrees.toDouble() * PI) / 180.0
            val dx = cos(theta).toFloat()
            val dy = sin(theta).toFloat()

            val limitX =
                when {
                    dx > 0.0001f -> space.right / dx
                    dx < -0.0001f -> space.left / (-dx)
                    else -> Float.POSITIVE_INFINITY
                }
            val limitY =
                when {
                    dy > 0.0001f -> space.bottom / dy
                    dy < -0.0001f -> space.top / (-dy)
                    else -> Float.POSITIVE_INFINITY
                }

            val maxAllowed = min(limitX, limitY).coerceAtLeast(0f)
            return min(desiredMaxRadiusPx, maxAllowed)
        }

        fun desiredMenuSpreadDegrees(actions: List<RadialFabAction>): Float =
            if (actions.size >= 6) 360f else 180f

        fun computeFanAnglesDegrees(
            count: Int,
            baseAngle: Float,
            spread: Float,
        ): List<Float> {
            if (count <= 0) return emptyList()
            return List(count) { idx ->
                val t =
                    if (spread >= 359.5f) {
                        (360f * idx / count)
                    } else {
                        (-spread / 2f + (idx + 0.5f) * (spread / count))
                    }
                baseAngle + t
            }
        }

        fun computeFanActionCenters(
            space: AvailableSpacePx,
            actions: List<RadialFabAction>,
            baseAngle: Float,
            spread: Float,
        ): Pair<Float, Map<String, Offset>> {
            val angles = computeFanAnglesDegrees(count = actions.size, baseAngle = baseAngle, spread = spread)
            if (angles.isEmpty()) return 0f to emptyMap()

            val baseRadiusPx = angles.minOf { angle -> maxRadiusForAnglePx(space, angle) }.coerceAtLeast(0f)
            val centers = HashMap<String, Offset>(actions.size)
            for (idx in actions.indices) {
                val action = actions[idx]
                val theta = (angles[idx].toDouble() * PI) / 180.0
                val dx = cos(theta).toFloat() * baseRadiusPx
                val dy = sin(theta).toFloat() * baseRadiusPx
                centers[action.id] = fabCenterPx + Offset(dx, dy)
            }
            return baseRadiusPx to centers
        }

        fun pickMenuBaseAngle(space: AvailableSpacePx, actions: List<RadialFabAction>, spread: Float): Pair<Float, Float> {
            fun candidateScore(baseAngle: Float): Float {
                val (baseRadiusPx, centers) =
                    computeFanActionCenters(space = space, actions = actions, baseAngle = baseAngle, spread = spread)
                if (centers.isEmpty()) return 0f

                var minPairwiseDistance = Float.POSITIVE_INFINITY
                val centerList = centers.values.toList()
                for (i in 0 until centerList.size) {
                    for (j in i + 1 until centerList.size) {
                        val d = hypot(centerList[i].x - centerList[j].x, centerList[i].y - centerList[j].y)
                        if (d < minPairwiseDistance) minPairwiseDistance = d
                    }
                }
                if (centerList.size < 2) minPairwiseDistance = 1_000_000f

                return minPairwiseDistance + (0.25f * baseRadiusPx)
            }

            val candidates = (0 until 360 step 15).map { it.toFloat() }
            val bestAngle = candidates.maxBy { candidateScore(it) }
            val bestRadius =
                computeFanActionCenters(space = space, actions = actions, baseAngle = bestAngle, spread = spread).first
            return bestAngle to bestRadius
        }

        fun computeMenuLayout(space: AvailableSpacePx, actions: List<RadialFabAction>): MenuLayout {
            if (!adaptiveMenuLayout) {
                val maxAllowedMenuRadiusPx =
                    min(
                        a = space.left,
                        b = min(space.right, min(space.top, space.bottom)),
                    ).coerceAtLeast(0f)
                val maxMenuRadiusPx = min(desiredMaxRadiusPx, maxAllowedMenuRadiusPx)
                val ringRadiiPx = listOf(maxMenuRadiusPx * 0.5f, maxMenuRadiusPx * 0.75f, maxMenuRadiusPx)
                val centers =
                    actions.associate { action ->
                        val ringRadius = ringRadiiPx.getOrElse(action.ringIndex) { ringRadiiPx.lastOrNull() ?: 0f }
                        val theta = (action.angleDegrees.toDouble() * PI) / 180.0
                        val dx = cos(theta).toFloat() * ringRadius
                        val dy = sin(theta).toFloat() * ringRadius
                        action.id to (fabCenterPx + Offset(dx, dy))
                    }
                return MenuLayout(
                    baseRadiusPx = maxMenuRadiusPx,
                    ringRadiiPx = ringRadiiPx,
                    actionCentersPx = centers,
                )
            }

            val spread = desiredMenuSpreadDegrees(actions)
            val (baseAngle, baseRadiusPx) = pickMenuBaseAngle(space = space, actions = actions, spread = spread)
            val (_, centers) =
                computeFanActionCenters(space = space, actions = actions, baseAngle = baseAngle, spread = spread)
            val ringRadiiPx = listOf(baseRadiusPx * 0.66f, baseRadiusPx)

            return MenuLayout(
                baseRadiusPx = baseRadiusPx,
                ringRadiiPx = ringRadiiPx,
                actionCentersPx = centers,
            )
        }

        LaunchedEffect(
            persistenceKey,
            containerWidthPx,
            containerHeightPx,
            navBarsBottomPx,
            fabMeasuredSizePx,
            persistedOffsetLoaded,
            initialOffsetApplied,
        ) {
            if (containerWidthPx <= 0f || containerHeightPx <= 0f) return@LaunchedEffect
            if (!persistedOffsetLoaded || !initialOffsetApplied) return@LaunchedEffect
            snapshotFlow { fabOffsetPx }
                .filterNotNull()
                .distinctUntilChanged()
                .debounce(300)
                .map { offset ->
                    val maxX = (containerWidthPx - fabMeasuredSizePx).coerceAtLeast(0f)
                    val maxY = (containerHeightPx - navBarsBottomPx - fabMeasuredSizePx).coerceAtLeast(0f)
                    if (maxX <= 0f || maxY <= 0f) null
                    else Offset((offset.x / maxX).coerceIn(0f, 1f), (offset.y / maxY).coerceIn(0f, 1f))
                }
                .distinctUntilChanged()
                .collect { frac ->
                    if (frac == null) return@collect
                    context.dataStore.edit { prefs ->
                        prefs[offsetFracXKey] = frac.x
                        prefs[offsetFracYKey] = frac.y
                    }
                }
        }

        val menuProgress by animateFloatAsState(
            targetValue = if (menuOpen) 1f else 0f,
            animationSpec = tween(durationMillis = if (menuOpen) 220 else 160, easing = FastOutSlowInEasing),
            label = "radialFabMenuProgress",
        )

        LaunchedEffect(menuOpen, currentActions.size) {
            if (menuOpen) {
                menuVisible = true
            } else if (menuVisible) {
                val exitDurationMs = 160
                val exitStaggerMs = 28
                val exitTotalMs = ((currentActions.size - 1).coerceAtLeast(0) * exitStaggerMs) + exitDurationMs
                delay(exitTotalMs.toLong())
                menuVisible = false
            }
        }

        if (menuVisible) {
            val space = availableSpacePx()
            val layout = computeMenuLayout(space = space, actions = currentActions)

            val overlayAlpha = 0.28f * menuProgress
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = overlayAlpha))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                menuOpen = false
                                hoveredActionId = null
                    }
                },
            )

            val menuBgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f * menuProgress)
            val ringColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f * menuProgress)
            Canvas(modifier = Modifier.matchParentSize()) {
                val menuBgRadius = (layout.baseRadiusPx + actionButtonSizePx / 2).coerceAtLeast(0f)
                drawCircle(
                    color = menuBgColor,
                    radius = menuBgRadius,
                    center = fabCenterPx,
                )
                for (radius in layout.ringRadiiPx) {
                    drawCircle(
                        color = ringColor,
                        radius = radius,
                        center = fabCenterPx,
                    )
                }
            }

            val enterDurationMs = 220
            val enterStaggerMs = 28
            val exitDurationMs = 160
            val exitStaggerMs = 28

            for ((index, action) in currentActions.withIndex()) {
                val center = layout.actionCentersPx[action.id] ?: fabCenterPx
                val selected = hoveredActionId == action.id
                val hoverScale by animateFloatAsState(if (selected) 1.10f else 1.0f, label = "radialFabHoverScale")

                val delayMs =
                    if (menuOpen) {
                        index * enterStaggerMs
                    } else {
                        (currentActions.size - 1 - index).coerceAtLeast(0) * exitStaggerMs
                    }
                val actionProgress by animateFloatAsState(
                    targetValue = if (menuOpen) 1f else 0f,
                    animationSpec =
                        tween(
                            durationMillis = if (menuOpen) enterDurationMs else exitDurationMs,
                            delayMillis = delayMs,
                            easing = FastOutSlowInEasing,
                        ),
                    label = "radialFabActionProgress_${action.id}",
                )

                val animatedCenter =
                    Offset(
                        x = fabCenterPx.x + ((center.x - fabCenterPx.x) * actionProgress),
                        y = fabCenterPx.y + ((center.y - fabCenterPx.y) * actionProgress),
                    )
                val baseScale = 0.64f + (0.36f * actionProgress)
                val scale = baseScale * hoverScale
                val topLeft =
                    IntOffset(
                        x = (animatedCenter.x - actionButtonSizePx / 2).roundToInt(),
                        y = (animatedCenter.y - actionButtonSizePx / 2).roundToInt(),
                    )
                Box(
                    modifier =
                        Modifier
                            .offset { topLeft }
                            .size(actionButtonSizeDp)
                            .graphicsLayer(
                                alpha = actionProgress,
                                scaleX = scale,
                                scaleY = scale,
                            )
                            .background(
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                shape = CircleShape,
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
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
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        val viewConfiguration = LocalViewConfiguration.current
        FloatingActionButton(
            onClick = {},
            shape = if (isEdgeHidden) CircleShape else RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation =
                if (isEdgeHidden) {
                    FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                    )
                } else {
                    FloatingActionButtonDefaults.elevation()
                },
            modifier =
                Modifier
                    .offset { IntOffset(renderOffsetPx.x.roundToInt(), renderOffsetPx.y.roundToInt()) }
                    .size(fabSizeDp)
                    .onSizeChanged { fabMeasuredSizePx = it.toSize().width }
                    .pointerInput(containerWidthPx, containerHeightPx, navBarsBottomPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pointerId: PointerId = down.id
                            var totalDrag = Offset.Zero
                            hoveredActionId = null
                            val wasCollapsed = edgeHideEnabled && edgeCollapsed

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
                                interactionTick++
                            }

                            if (longPressed) {
                                val space = availableSpacePx()
                                val layout = computeMenuLayout(space = space, actions = currentActions)

                                fun findHoveredAction(pointerPosition: Offset): String? {
                                    var bestId: String? = null
                                    var bestDistance = Float.POSITIVE_INFINITY
                                    for (action in currentActions) {
                                        val center = layout.actionCentersPx[action.id] ?: fabCenterPx
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
                                    // `change.position` is FAB-local; convert to parent coordinates for hit-testing.
                                    val pointerGlobal = renderOffsetPx + change.position
                                    hoveredActionId = findHoveredAction(pointerGlobal)
                                    change.consume()
                                    if (!change.pressed) {
                                        val selectedId = hoveredActionId
                                        menuOpen = false
                                        hoveredActionId = null
                                        if (selectedId != null) {
                                            currentActions.firstOrNull { it.id == selectedId }?.let(currentOnClickAction)
                                        }
                                        interactionTick++
                                        break
                                    }
                                }
                                return@awaitEachGesture
                            }

                            if (!dragging) {
                                if (wasCollapsed) {
                                    edgeCollapsed = false
                                } else {
                                    currentOnClickPrimary()
                                }
                                interactionTick++
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
                            dockEdge = computeDockEdge(fabOffsetPx ?: resolvedFabOffsetPx)
                            edgeCollapsed = false
                            interactionTick++
                        }
                    },
        ) {
            if (isEdgeHidden) {
                val peekWidthDp = fabSizeDp * edgePeekFraction.coerceIn(0.1f, 1f)
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier =
                            if (dockEdge == DockEdge.Left) {
                                Modifier.align(Alignment.CenterEnd).width(peekWidthDp).fillMaxHeight()
                            } else {
                                Modifier.align(Alignment.CenterStart).width(peekWidthDp).fillMaxHeight()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(if (dockEdge == DockEdge.Left) Ionicons.ChevronForward else Ionicons.ChevronBack),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            } else {
                Text(text = if (menuVisible) "×" else primaryLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private enum class DockEdge {
    Left,
    Right,
}

@Immutable
private data class AvailableSpacePx(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
)

@Immutable
private data class MenuLayout(
    val baseRadiusPx: Float,
    val ringRadiiPx: List<Float>,
    val actionCentersPx: Map<String, Offset>,
)
