package app.zhixu.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.data.VaultRepository
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class StrokeSnapshot(
    val colorArgb: Int,
    val widthPx: Float,
    val points: List<Offset>,
)

private data class StrokeState(
    val colorArgb: Int,
    val widthPx: Float,
    val points: SnapshotStateList<Offset>,
)

private val xournalppPalette: List<Color> =
    listOf(
        Color(0xFFFFE16B), // Yellow
        Color(0xFFFFA154), // Orange
        Color(0xFFCD9EF7), // Purple
        Color(0xFF9BDB4D), // Light Green
        Color(0xFF64BAFF), // Light Blue
        Color(0xFF808080), // Gray
        Color(0xFF3A9104), // Dark Green
        Color(0xFFED5353), // Red
        Color(0xFF002E99), // Dark Blue
        Color(0xFF000000), // Black
        Color(0xFFFFFFFF), // White
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawScreen(
    vaultRootUri: Uri,
    repository: VaultRepository,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val canvasBg = Color.White
    val palette = remember { xournalppPalette }

    var selectedColorArgb by rememberSaveable { mutableIntStateOf(Color.Black.toArgb()) }
    var strokeWidthDp by rememberSaveable { mutableFloatStateOf(4f) }
    var isEraser by rememberSaveable { mutableStateOf(false) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val strokes = remember { mutableStateListOf<StrokeState>() }
    val redoStrokes = remember { mutableStateListOf<StrokeState>() }

    var isSaving by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val strokeWidthPx = remember(strokeWidthDp, density) { with(density) { strokeWidthDp.dp.toPx() } }
    val drawColorArgb = remember(selectedColorArgb, isEraser, canvasBg) {
        if (isEraser) canvasBg.toArgb() else selectedColorArgb
    }

    val canUndo = strokes.isNotEmpty() && !isSaving
    val canRedo = redoStrokes.isNotEmpty() && !isSaving
    val canSave = strokes.isNotEmpty() && canvasSize.width > 0 && canvasSize.height > 0 && !isSaving

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = {
                        Text(
                            text = stringResource(R.string.draw_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                    },
                    actions = {
                        ZhixuIconButton(enabled = canUndo, onClick = {
                            if (strokes.isEmpty()) return@ZhixuIconButton
                            redoStrokes.add(strokes.removeAt(strokes.lastIndex))
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Undo,
                                contentDescription = stringResource(R.string.action_undo),
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                        ZhixuIconButton(enabled = canRedo, onClick = {
                            if (redoStrokes.isEmpty()) return@ZhixuIconButton
                            strokes.add(redoStrokes.removeAt(redoStrokes.lastIndex))
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Redo,
                                contentDescription = stringResource(R.string.action_redo),
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                        ZhixuIconButton(enabled = strokes.isNotEmpty() && !isSaving, onClick = { showClearConfirm = true }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.draw_action_clear),
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                        TextButton(enabled = canSave, onClick = {
                            if (!canSave) return@TextButton
                            val strokeSnapshot =
                                strokes.map { s ->
                                    StrokeSnapshot(
                                        colorArgb = s.colorArgb,
                                        widthPx = s.widthPx,
                                        points = s.points.toList(),
                                    )
                                }
                            val sizeSnapshot = canvasSize

                            scope.launch {
                                isSaving = true
                                val result =
                                    runCatching {
                                        val pngBytes =
                                            withContext(Dispatchers.Default) {
                                                renderPng(
                                                    backgroundArgb = canvasBg.toArgb(),
                                                    size = sizeSnapshot,
                                                    strokes = strokeSnapshot,
                                                )
                                            }

                                        val ts = System.currentTimeMillis()
                                        val imageFileName = "drawing_$ts.png"
                                        val imageRelPath = ".zhixu/draw/images/$imageFileName"

                                        repository.ensureVaultStructure(vaultRootUri)
                                        val imageUri = repository.ensureVaultFile(vaultRootUri, imageRelPath, "image/png")
                                        repository.writeBytes(imageUri, pngBytes)

                                        val createdDoc = repository.createDoc(vaultRootUri, "Drawing_$ts")
                                        val md =
                                            buildString {
                                                append("# ")
                                                append(context.getString(R.string.draw_note_title))
                                                append("\n\n![](")
                                                append(imageRelPath)
                                                append(")\n")
                                            }
                                        repository.writeText(createdDoc.uri, md)
                                        repository.indexDocUri(createdDoc.uri)
                                        createdDoc.uri.toString()
                                    }

                                result.onSuccess { docUri ->
                                    onSaved(docUri)
                                }.onFailure { e ->
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.draw_save_failed,
                                            e.message ?: e.javaClass.simpleName,
                                        ),
                                    )
                                }
                                isSaving = false
                            }
                        }) {
                            Icon(
                                painter = painterResource(Ionicons.Checkmark),
                                contentDescription = stringResource(R.string.action_save),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_save))
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            val canvasModifierBase =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            val canvasModifier =
                if (isSaving) {
                    canvasModifierBase
                } else {
                    canvasModifierBase.pointerInput(drawColorArgb, strokeWidthPx) {
                        var currentStroke: StrokeState? = null
                        detectDragGestures(
                            onDragStart = { start ->
                                val stroke =
                                    StrokeState(
                                        colorArgb = drawColorArgb,
                                        widthPx = strokeWidthPx,
                                        points = mutableStateListOf(start),
                                    )
                                strokes.add(stroke)
                                redoStrokes.clear()
                                currentStroke = stroke
                            },
                            onDrag = { change, _ ->
                                currentStroke?.points?.add(change.position)
                                change.consume()
                            },
                            onDragEnd = { currentStroke = null },
                            onDragCancel = { currentStroke = null },
                        )
                    }
                }

            Surface(
                modifier =
                    canvasModifier
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                color = canvasBg,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Canvas(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { canvasSize = it }
                            .background(canvasBg)
                            .padding(2.dp),
                    onDraw = {
                        for (stroke in strokes) {
                            drawStroke(stroke)
                        }
                    },
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.draw_tool),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(if (isEraser) R.string.draw_tool_eraser else R.string.draw_tool_pen),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            enabled = !isSaving,
                            onClick = { isEraser = !isEraser },
                        ) {
                            Icon(
                                painter = painterResource(if (isEraser) Ionicons.TrashOutline else R.drawable.ic_hero_paint_brush),
                                contentDescription = stringResource(R.string.draw_tool),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(if (isEraser) R.string.draw_tool_eraser else R.string.draw_tool_pen))
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(palette) { _, c ->
                        val selected = c.toArgb() == selectedColorArgb && !isEraser
                        Box(
                            modifier =
                                Modifier
                                    .size(44.dp)
                                    .background(Color.Transparent, CircleShape)
                                    .clickable(enabled = !isSaving) {
                                        selectedColorArgb = c.toArgb()
                                        isEraser = false
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = c,
                                shadowElevation = 0.dp,
                                tonalElevation = 0.dp,
                            ) {}
                            if (selected) {
                                Surface(
                                    modifier = Modifier.size(18.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(Ionicons.Checkmark),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.draw_stroke_width),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Slider(
                        enabled = !isSaving,
                        value = strokeWidthDp,
                        onValueChange = { strokeWidthDp = it },
                        valueRange = 1f..18f,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = strokeWidthDp.toInt().toString(),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(20.dp),
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.draw_clear_title)) },
            text = { Text(stringResource(R.string.draw_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        strokes.clear()
                        redoStrokes.clear()
                        showClearConfirm = false
                    },
                ) { Text(stringResource(R.string.draw_action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun drawStroke(stroke: StrokeState, drawScope: androidx.compose.ui.graphics.drawscope.DrawScope) =
    with(drawScope) {
        if (stroke.points.isEmpty()) return
        if (stroke.points.size == 1) {
            drawCircle(
                color = Color(stroke.colorArgb),
                radius = stroke.widthPx / 2f,
                center = stroke.points.first(),
            )
            return
        }
        val path = Path()
        val first = stroke.points.first()
        path.moveTo(first.x, first.y)
        for (i in 1 until stroke.points.size) {
            val p = stroke.points[i]
            path.lineTo(p.x, p.y)
        }
        drawPath(
            path = path,
            color = Color(stroke.colorArgb),
            style = Stroke(width = stroke.widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(stroke: StrokeState) {
    drawStroke(stroke, this)
}

private fun renderPng(
    backgroundArgb: Int,
    size: IntSize,
    strokes: List<StrokeSnapshot>,
): ByteArray {
    val width = size.width.coerceAtLeast(1)
    val height = size.height.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(backgroundArgb)

    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    val path = AndroidPath()

    for (stroke in strokes) {
        val points = stroke.points
        if (points.isEmpty()) continue
        paint.color = stroke.colorArgb
        paint.strokeWidth = stroke.widthPx
        path.reset()
        val first = points.first()
        path.moveTo(first.x, first.y)
        for (i in 1 until points.size) {
            val p = points[i]
            path.lineTo(p.x, p.y)
        }
        canvas.drawPath(path, paint)
    }

    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}
