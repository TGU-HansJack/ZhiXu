package app.zhixu.ui.screens

import android.net.Uri
import android.util.Xml
import android.content.Context
import androidx.documentfile.provider.DocumentFile
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
import app.zhixu.ui.components.ZhixuTextField
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser

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

private data class ZhixuDrawingDoc(
    val width: Int,
    val height: Int,
    val strokes: List<StrokeSnapshot>,
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
    docUri: Uri?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val canvasBg = Color.White
    val palette = remember { xournalppPalette }

    var selectedColorArgb by rememberSaveable { mutableIntStateOf(Color.Black.toArgb()) }
    var strokeWidthDp by rememberSaveable { mutableFloatStateOf(4f) }
    var isEraser by rememberSaveable { mutableStateOf(false) }

    var fileUri by remember { mutableStateOf<Uri?>(docUri) }
    var fileName by rememberSaveable { mutableStateOf(buildDefaultDrawingFileName()) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var cachedFileDisplayName by remember { mutableStateOf<String?>(null) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val strokes = remember { mutableStateListOf<StrokeState>() }
    val redoStrokes = remember { mutableStateListOf<StrokeState>() }

    var pendingLoadDoc by remember { mutableStateOf<ZhixuDrawingDoc?>(null) }
    var isLoading by remember { mutableStateOf(docUri != null) }
    var isSaving by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val strokeWidthPx = remember(strokeWidthDp, density) { with(density) { strokeWidthDp.dp.toPx() } }
    val drawColorArgb = remember(selectedColorArgb, isEraser, canvasBg) {
        if (isEraser) canvasBg.toArgb() else selectedColorArgb
    }

    val canUndo = strokes.isNotEmpty() && !isSaving && !isLoading
    val canRedo = redoStrokes.isNotEmpty() && !isSaving && !isLoading
    val canSave = strokes.isNotEmpty() && canvasSize.width > 0 && canvasSize.height > 0 && !isSaving && !isLoading

    androidx.compose.runtime.LaunchedEffect(docUri) {
        if (docUri == null) return@LaunchedEffect
        isLoading = true
        val bytes = runCatching { repository.readBytes(docUri) }.getOrNull() ?: byteArrayOf()
        val parsed =
            runCatching { decodeZhixud(bytes) }
                .onFailure {
                    snackbarHostState.showSnackbar(context.getString(R.string.draw_load_failed))
                }
                .getOrNull()
        pendingLoadDoc = parsed
        cachedFileDisplayName =
            runCatching {
                DocumentFile.fromSingleUri(context, docUri)?.name?.substringBeforeLast('.')?.ifBlank { null }
            }.getOrNull()
        isLoading = false
    }

    androidx.compose.runtime.LaunchedEffect(pendingLoadDoc, canvasSize) {
        val doc = pendingLoadDoc ?: return@LaunchedEffect
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return@LaunchedEffect
        val srcW = doc.width.coerceAtLeast(1)
        val srcH = doc.height.coerceAtLeast(1)
        val sx = canvasSize.width.toFloat() / srcW.toFloat()
        val sy = canvasSize.height.toFloat() / srcH.toFloat()
        val sw = (sx + sy) / 2f

        strokes.clear()
        redoStrokes.clear()
        for (stroke in doc.strokes) {
            val pts = mutableStateListOf<Offset>()
            pts.addAll(stroke.points.map { p -> Offset(p.x * sx, p.y * sy) })
            strokes.add(
                StrokeState(
                    colorArgb = stroke.colorArgb,
                    widthPx = stroke.widthPx * sw,
                    points = pts,
                ),
            )
        }
        pendingLoadDoc = null
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = {
                        val titleText = cachedFileDisplayName ?: stringResource(R.string.draw_title)
                        Text(
                            text = titleText,
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
                            if (fileUri == null) {
                                showSaveAsDialog = true
                            } else {
                                scope.launch {
                                    isSaving = true
                                    saveDrawing(
                                        context = context,
                                        repository = repository,
                                        vaultRootUri = vaultRootUri,
                                        targetUri = fileUri,
                                        canvasSize = canvasSize,
                                        strokes = strokes,
                                        snackbarHostState = snackbarHostState,
                                    ) { uri, name ->
                                        fileUri = uri
                                        cachedFileDisplayName = name
                                    }
                                    isSaving = false
                                }
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

    if (showSaveAsDialog) {
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            title = { Text(stringResource(R.string.draw_save_as_title)) },
            text = {
                ZhixuTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    singleLine = true,
                    placeholder = { Text("Drawing.zhixud") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = fileName.trim().isNotBlank() && !isSaving,
                    onClick = {
                        showSaveAsDialog = false
                        scope.launch {
                            isSaving = true
                            saveDrawing(
                                context = context,
                                repository = repository,
                                vaultRootUri = vaultRootUri,
                                targetUri = null,
                                canvasSize = canvasSize,
                                strokes = strokes,
                                snackbarHostState = snackbarHostState,
                                desiredName = fileName,
                            ) { uri, name ->
                                fileUri = uri
                                cachedFileDisplayName = name
                            }
                            isSaving = false
                        }
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = { showSaveAsDialog = false },
                ) { Text(stringResource(R.string.action_cancel)) }
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

private suspend fun saveDrawing(
    context: Context,
    repository: VaultRepository,
    vaultRootUri: Uri,
    targetUri: Uri?,
    canvasSize: IntSize,
    strokes: List<StrokeState>,
    snackbarHostState: SnackbarHostState,
    desiredName: String? = null,
    onSaved: (Uri, String?) -> Unit,
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
    val strokeSnapshot =
        strokes.map { s ->
            StrokeSnapshot(
                colorArgb = s.colorArgb,
                widthPx = s.widthPx,
                points = s.points.toList(),
            )
        }
    val doc =
        ZhixuDrawingDoc(
            width = canvasSize.width,
            height = canvasSize.height,
            strokes = strokeSnapshot,
        )

    val bytes =
        runCatching { encodeZhixud(doc) }.getOrNull()
            ?: run {
                snackbarHostState.showSnackbar(context.getString(R.string.draw_save_failed, "encode"))
                return
            }

    val finalUri =
        if (targetUri != null) {
            targetUri
        } else {
            val trimmed = desiredName.orEmpty().trim().ifBlank { buildDefaultDrawingFileName() }
            val safe = sanitizeDrawingFileName(trimmed)
            val fileName = if (safe.endsWith(".zhixud", ignoreCase = true)) safe else "$safe.zhixud"
            val relPath = "Drawings/$fileName"
            runCatching { repository.ensureVaultFile(vaultRootUri, relPath, "application/zhixu-drawing") }
                .onFailure { e ->
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.draw_save_failed,
                            e.message ?: e.javaClass.simpleName,
                        ),
                    )
                }
                .getOrNull()
                ?: return
        }

    runCatching { repository.writeBytes(finalUri, bytes) }
        .onSuccess {
            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_saved))
            val displayName =
                runCatching { DocumentFile.fromSingleUri(context, finalUri)?.name?.substringBeforeLast('.') }
                    .getOrNull()
            onSaved(finalUri, displayName)
        }
        .onFailure { e ->
            snackbarHostState.showSnackbar(
                context.getString(
                    R.string.draw_save_failed,
                    e.message ?: e.javaClass.simpleName,
                ),
            )
        }
}

private fun buildDefaultDrawingFileName(): String = "Drawing_${System.currentTimeMillis()}.zhixud"

private fun sanitizeDrawingFileName(name: String): String =
    name.trim()
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { "Drawing_${System.currentTimeMillis()}" }

private fun encodeZhixud(doc: ZhixuDrawingDoc): ByteArray {
    val xml = buildContentXml(doc)
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zos ->
        zos.putNextEntry(ZipEntry("mimetype"))
        zos.write("application/zhixu-drawing".toByteArray(Charsets.US_ASCII))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("META-INF/version"))
        zos.write("current=1\nmin=1\n".toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("content.xml"))
        zos.write(xml.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }
    return out.toByteArray()
}

private fun decodeZhixud(bytes: ByteArray): ZhixuDrawingDoc {
    if (bytes.isEmpty()) error("Empty file")
    ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
        while (true) {
            val entry = zis.nextEntry ?: break
            if (entry.name == "content.xml") {
                val xml = zis.readBytes().toString(Charsets.UTF_8)
                return parseContentXml(xml)
            }
        }
    }
    error("Missing content.xml")
}

private fun buildContentXml(doc: ZhixuDrawingDoc): String {
    val sb = StringBuilder(1024 + doc.strokes.size * 64)
    sb.append("<?xml version=\"1.0\" standalone=\"no\"?>\n")
    sb.append("<xournal creator=\"Zhixu\" fileversion=\"1\">\n")
    sb.append("<title>Zhixu drawing</title>\n")
    sb.append("<page width=\"")
        .append(doc.width)
        .append("\" height=\"")
        .append(doc.height)
        .append("\">\n")
    sb.append("<background type=\"solid\" color=\"#ffffffff\" style=\"plain\"/>\n")
    sb.append("<layer>\n")
    for (stroke in doc.strokes) {
        sb.append("<stroke tool=\"pen\" ts=\"0\" fn=\"\" color=\"")
            .append(argbToRgbaHex(stroke.colorArgb))
            .append("\" width=\"")
            .append(formatFloat(stroke.widthPx))
            .append("\">")
        val pts = stroke.points
        for (i in pts.indices) {
            val p = pts[i]
            sb.append(formatFloat(p.x)).append(' ').append(formatFloat(p.y))
            if (i != pts.lastIndex) sb.append(' ')
        }
        sb.append("</stroke>\n")
    }
    sb.append("</layer>\n")
    sb.append("</page>\n")
    sb.append("</xournal>\n")
    return sb.toString()
}

private fun parseContentXml(xml: String): ZhixuDrawingDoc {
    val parser = Xml.newPullParser()
    parser.setInput(StringReader(xml))
    var width = 0
    var height = 0
    val strokes = ArrayList<StrokeSnapshot>()

    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG) {
            when (parser.name) {
                "page" -> {
                    width = parser.getAttributeValue(null, "width")?.toFloatOrNull()?.toInt() ?: width
                    height = parser.getAttributeValue(null, "height")?.toFloatOrNull()?.toInt() ?: height
                }

                "stroke" -> {
                    val color = parser.getAttributeValue(null, "color")?.let(::rgbaHexToArgb) ?: Color.Black.toArgb()
                    val w = parser.getAttributeValue(null, "width")?.toFloatOrNull() ?: 3f
                    val body = parser.nextText().orEmpty()
                    val pts = parsePoints(body)
                    strokes.add(StrokeSnapshot(colorArgb = color, widthPx = w, points = pts))
                }
            }
        }
        parser.next()
    }
    if (width <= 0 || height <= 0) error("Invalid document size")
    return ZhixuDrawingDoc(width = width, height = height, strokes = strokes)
}

private fun parsePoints(text: String): List<Offset> {
    val items = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (items.isEmpty()) return emptyList()
    val floats = items.mapNotNull { it.toFloatOrNull() }
    val out = ArrayList<Offset>(floats.size / 2)
    var i = 0
    while (i + 1 < floats.size) {
        out.add(Offset(floats[i], floats[i + 1]))
        i += 2
    }
    return out
}

private fun formatFloat(value: Float): String = String.format(Locale.US, "%.2f", value)

private fun argbToRgbaHex(argb: Int): String {
    val a = (argb ushr 24) and 0xFF
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    return String.format(Locale.US, "#%02x%02x%02x%02x", r, g, b, a)
}

private fun rgbaHexToArgb(hex: String): Int {
    val s = hex.trim().removePrefix("#")
    if (s.length == 8) {
        val r = s.substring(0, 2).toInt(16)
        val g = s.substring(2, 4).toInt(16)
        val b = s.substring(4, 6).toInt(16)
        val a = s.substring(6, 8).toInt(16)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    if (s.length == 6) {
        val r = s.substring(0, 2).toInt(16)
        val g = s.substring(2, 4).toInt(16)
        val b = s.substring(4, 6).toInt(16)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    return Color.Black.toArgb()
}
