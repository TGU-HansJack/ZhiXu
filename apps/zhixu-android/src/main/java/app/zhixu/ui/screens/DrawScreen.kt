package app.zhixu.ui.screens

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.zhixu.R
import app.zhixu.data.VaultRepository
import app.zhixu.draw.ZhixuDrawElement
import app.zhixu.draw.ZhixuDrawFormat
import app.zhixu.draw.editor.DrawEditorState
import app.zhixu.draw.editor.DrawElementState
import app.zhixu.draw.editor.DrawShapeMode
import app.zhixu.draw.editor.DrawShapeState
import app.zhixu.draw.editor.DrawStrokeState
import app.zhixu.draw.editor.DrawToolId
import app.zhixu.draw.tools.DrawToolMachine
import app.zhixu.draw.tools.EraserToolMachine
import app.zhixu.draw.tools.HighlighterToolMachine
import app.zhixu.draw.tools.LassoToolMachine
import app.zhixu.draw.tools.PanToolMachine
import app.zhixu.draw.tools.PenToolMachine
import app.zhixu.draw.tools.ShapeToolMachine
import app.zhixu.draw.tools.ToolPointerEvent
import app.zhixu.ui.Heroicons
import app.zhixu.ui.Ionicons
import app.zhixu.ui.DocListMutation
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTextField
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlinx.coroutines.launch

private const val MinScale: Float = 0.3f
private const val MaxScale: Float = 5.0f

private data class DrawUndoHistory(
    val undoStack: ArrayDeque<List<ZhixuDrawElement>> = ArrayDeque(),
    val redoStack: ArrayDeque<List<ZhixuDrawElement>> = ArrayDeque(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawScreen(
    vaultRootUri: Uri,
    repository: VaultRepository,
    docUri: Uri?,
    onDocListMutated: (DocListMutation) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val marginPx = with(density) { 24.dp.toPx() }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentDocUri by remember { mutableStateOf(docUri) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf(DrawEditorState.newDocument()) }

    val penTool = remember { PenToolMachine() }
    val highlighterTool = remember { HighlighterToolMachine() }
    val shapeTool = remember { ShapeToolMachine() }
    val lassoTool = remember { LassoToolMachine() }
    val eraserTool = remember { EraserToolMachine() }
    val panTool = remember { PanToolMachine() }

    fun toolMachineFor(id: DrawToolId): DrawToolMachine =
        when (id) {
            DrawToolId.Pen -> penTool
            DrawToolId.Highlighter -> highlighterTool
            DrawToolId.Shape -> shapeTool
            DrawToolId.Lasso -> lassoTool
            DrawToolId.Eraser -> eraserTool
            DrawToolId.Pan -> panTool
        }

    fun markEdited() {
        editor.modifiedAtMs = System.currentTimeMillis()
    }

    val histories = remember { HashMap<String, DrawUndoHistory>() }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }

    fun ensureHistoryForCurrentPage(): DrawUndoHistory? {
        val page = editor.currentPageOrNull() ?: return null
        val history =
            histories.getOrPut(page.id) {
                DrawUndoHistory().also { it.undoStack.addLast(editor.snapshotPageElements()) }
            }
        if (history.undoStack.isEmpty()) history.undoStack.addLast(editor.snapshotPageElements())
        return history
    }

    fun refreshUndoRedoAvailability() {
        val history = ensureHistoryForCurrentPage()
        canUndo = (history?.undoStack?.size ?: 0) > 1
        canRedo = history?.redoStack?.isNotEmpty() == true
    }

    fun commitEdit() {
        markEdited()
        val history = ensureHistoryForCurrentPage() ?: return
        val snapshot = editor.snapshotPageElements()
        val last = history.undoStack.lastOrNull()
        if (last != null && last == snapshot) {
            refreshUndoRedoAvailability()
            return
        }
        if (history.undoStack.size >= 100) history.undoStack.removeFirst()
        history.undoStack.addLast(snapshot)
        history.redoStack.clear()
        refreshUndoRedoAvailability()
    }

    fun undoStroke() {
        val history = ensureHistoryForCurrentPage() ?: return
        if (history.undoStack.size <= 1) return
        val current = history.undoStack.removeLast()
        history.redoStack.addLast(current)
        val previous = history.undoStack.last()
        editor.restorePageElements(previous)
        markEdited()
        refreshUndoRedoAvailability()
    }

    fun redoStroke() {
        val history = ensureHistoryForCurrentPage() ?: return
        if (history.redoStack.isEmpty()) return
        val next = history.redoStack.removeLast()
        history.undoStack.addLast(next)
        editor.restorePageElements(next)
        markEdited()
        refreshUndoRedoAvailability()
    }

    LaunchedEffect(editor) {
        histories.clear()
        refreshUndoRedoAvailability()
    }

    LaunchedEffect(editor, editor.currentPageIndex) { refreshUndoRedoAvailability() }

    LaunchedEffect(docUri) {
        currentDocUri = docUri
        isLoading = true
        loadFailed = false
        editor = DrawEditorState.newDocument()

        if (docUri == null) {
            isLoading = false
            return@LaunchedEffect
        }

        val bytes = runCatching { repository.readBytes(docUri) }.getOrNull()
        if (bytes == null) {
            loadFailed = true
            isLoading = false
            return@LaunchedEffect
        }

        val doc = runCatching { ZhixuDrawFormat.decode(bytes) }.getOrNull()
        if (doc == null) {
            loadFailed = true
            isLoading = false
            return@LaunchedEffect
        }

        editor = DrawEditorState.fromDocument(doc)
        editor.ensureHasAtLeastOnePage()
        isLoading = false
    }

    suspend fun saveTo(uri: Uri) {
        markEdited()
        val bytes = ZhixuDrawFormat.encode(editor.toDocument())
        repository.writeBytes(uri, bytes)
        repository.indexDrawingUri(vaultRootUri, uri)
        onDocListMutated(DocListMutation.EntryChanged(uri))
    }

    var showSaveAsDialog by remember { mutableStateOf(false) }
    var saveAsName by remember {
        mutableStateOf(
            SimpleDateFormat("'Drawing'_yyyyMMdd_HHmm", Locale.US).format(Date()) + ZhixuDrawFormat.EXTENSION,
        )
    }

    fun requestSave() {
        val uri = currentDocUri
        if (uri == null) {
            showSaveAsDialog = true
            return
        }
        scope.launch {
            runCatching { saveTo(uri) }
                .onSuccess { snackbarHostState.showSnackbar(context.getString(R.string.snackbar_saved)) }
                .onFailure { e ->
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.draw_save_failed, e.message ?: e.javaClass.simpleName),
                    )
                }
        }
    }

    fun requestSaveAs() {
        showSaveAsDialog = true
    }

    var showClearDialog by remember { mutableStateOf(false) }

    fun clearCurrentPage() {
        val page = editor.currentPageOrNull() ?: return
        page.elements.clear()
        editor.clearOverlaysAndSelection()
        commitEdit()
    }

    fun deleteSelection() {
        val page = editor.currentPageOrNull() ?: return
        val selected = editor.selectedElementIds
        if (selected.isEmpty()) return
        page.elements.removeAll { it.id in selected }
        editor.selectedElementIds = emptySet()
        commitEdit()
    }

    var showToolDrawer by remember { mutableStateOf(false) }
    val toolDrawerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        val editorReady = !isLoading && !loadFailed
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                loadFailed -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.draw_load_failed))
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                        }
                    }
                }

                else -> {
                    DrawEditorCanvas(
                        editor = editor,
                        marginPx = marginPx,
                        toolMachineFor = ::toolMachineFor,
                        onEdited = ::commitEdit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawCircleIconButton(
                    onClick = onBack,
                    contentDescription = stringResource(R.string.action_back),
                ) {
                    androidx.compose.material3.Icon(
                        painter =
                            painterResource(
                                if (LocalLayoutDirection.current == LayoutDirection.Rtl) Ionicons.ArrowForward else Ionicons.ArrowBack,
                            ),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
                DrawCircleIconButton(
                    onClick = ::undoStroke,
                    enabled = editorReady && canUndo,
                    contentDescription = stringResource(R.string.action_undo),
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(Heroicons.ArrowUturnLeft),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
                DrawCircleIconButton(
                    onClick = ::redoStroke,
                    enabled = editorReady && canRedo,
                    contentDescription = stringResource(R.string.action_redo),
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(Heroicons.ArrowUturnRight),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            if (editorReady) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DrawCircleIconButton(
                        onClick = { showClearDialog = true },
                        contentDescription = stringResource(R.string.draw_action_clear),
                    ) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(Heroicons.Trash),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    DrawCircleIconButton(
                        onClick = ::requestSave,
                        contentDescription = stringResource(R.string.action_save),
                    ) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(Ionicons.SaveOutline),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                DrawToolDock(
                    editor = editor,
                    onSelectTool = {
                        editor.toolId = it
                        editor.clearOverlaysAndSelection()
                        showToolDrawer = true
                    },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 12.dp),
                )
            }
        }
    }

    if (showToolDrawer && !isLoading && !loadFailed) {
        ModalBottomSheet(
            onDismissRequest = { showToolDrawer = false },
            sheetState = toolDrawerState,
        ) {
            DrawToolDrawerContent(
                editor = editor,
                marginPx = marginPx,
                onEdited = ::commitEdit,
                onSaveAs = ::requestSaveAs,
                onDeleteSelection = ::deleteSelection,
            )
        }
    }

    if (showSaveAsDialog) {
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            title = { Text(stringResource(R.string.draw_save_as_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZhixuTextField(
                        value = saveAsName,
                        onValueChange = { saveAsName = it },
                        label = { Text(stringResource(R.string.field_file_name)) },
                    )
                    Text(
                        text = ZhixuDrawFormat.EXTENSION,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val cleaned = sanitizeFileName(saveAsName).ifBlank { "Drawing" }
                            val finalName =
                                if (cleaned.endsWith(ZhixuDrawFormat.EXTENSION, ignoreCase = true)) cleaned else cleaned + ZhixuDrawFormat.EXTENSION
                            val relPath = "Drawings/$finalName"
                            val uri =
                                runCatching { repository.ensureVaultFile(vaultRootUri, relPath, ZhixuDrawFormat.MIME_TYPE) }
                                    .getOrElse { e ->
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.draw_save_failed, e.message ?: e.javaClass.simpleName),
                                        )
                                        return@launch
                                    }

                            runCatching { saveTo(uri) }
                                .onSuccess {
                                    currentDocUri = uri
                                    showSaveAsDialog = false
                                    snackbarHostState.showSnackbar(context.getString(R.string.snackbar_saved))
                                }
                                .onFailure { e ->
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.draw_save_failed, e.message ?: e.javaClass.simpleName),
                                    )
                                }
                        }
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.draw_clear_title)) },
            text = { Text(stringResource(R.string.draw_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        clearCurrentPage()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun DrawCircleIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = CircleShape,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        modifier =
            modifier
                .size(44.dp)
                .alpha(contentAlpha)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun DrawToolDock(
    editor: DrawEditorState,
    onSelectTool: (DrawToolId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier.height(44.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrawToolDockButton(
                selected = editor.toolId == DrawToolId.Pen,
                iconRes = Ionicons.BrushOutline,
                contentDescription = stringResource(R.string.draw_tool_pen),
                onClick = { onSelectTool(DrawToolId.Pen) },
            )
            DrawToolDockButton(
                selected = editor.toolId == DrawToolId.Highlighter,
                iconRes = R.drawable.ic_ink_highlighter_24px,
                contentDescription = stringResource(R.string.draw_tool_highlighter),
                onClick = { onSelectTool(DrawToolId.Highlighter) },
            )
            DrawToolDockButton(
                selected = editor.toolId == DrawToolId.Shape,
                iconRes = R.drawable.ic_triangle_circle_24px,
                contentDescription = stringResource(R.string.draw_tool_shape),
                onClick = { onSelectTool(DrawToolId.Shape) },
            )
            DrawToolDockButton(
                selected = editor.toolId == DrawToolId.Lasso,
                iconRes = R.drawable.ic_lasso_select_24px,
                contentDescription = stringResource(R.string.draw_tool_lasso),
                onClick = { onSelectTool(DrawToolId.Lasso) },
            )
            DrawToolDockButton(
                selected = editor.toolId == DrawToolId.Eraser,
                iconRes = R.drawable.ic_ink_eraser_24px,
                contentDescription = stringResource(R.string.draw_tool_eraser),
                onClick = { onSelectTool(DrawToolId.Eraser) },
            )
            DrawToolDockButton(
                selected = editor.toolId == DrawToolId.Pan,
                iconRes = R.drawable.ic_pinch_zoom_out_24px,
                contentDescription = stringResource(R.string.draw_tool_pan),
                onClick = { onSelectTool(DrawToolId.Pan) },
            )
        }
    }
}

@Composable
private fun DrawToolDockButton(
    selected: Boolean,
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier =
            Modifier
                .size(36.dp)
                .clickable(onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = fg,
            )
        }
    }
}

@Composable
private fun DrawToolDrawerContent(
    editor: DrawEditorState,
    marginPx: Float,
    onEdited: () -> Unit,
    onSaveAs: () -> Unit,
    onDeleteSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val toolLabel =
            when (editor.toolId) {
                DrawToolId.Pen -> stringResource(R.string.draw_tool_pen)
                DrawToolId.Highlighter -> stringResource(R.string.draw_tool_highlighter)
                DrawToolId.Shape -> stringResource(R.string.draw_tool_shape)
                DrawToolId.Lasso -> stringResource(R.string.draw_tool_lasso)
                DrawToolId.Eraser -> stringResource(R.string.draw_tool_eraser)
                DrawToolId.Pan -> stringResource(R.string.draw_tool_pan)
            }
        Text(toolLabel, style = MaterialTheme.typography.titleMedium)

        if (editor.toolId == DrawToolId.Shape) {
            val chipScroll = rememberScrollState()
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(chipScroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeChip(
                    selected = editor.shapeMode == DrawShapeMode.Line,
                    label = stringResource(R.string.draw_shape_line),
                    onClick = { editor.shapeMode = DrawShapeMode.Line },
                )
                ModeChip(
                    selected = editor.shapeMode == DrawShapeMode.Rectangle,
                    label = stringResource(R.string.draw_shape_rect),
                    onClick = { editor.shapeMode = DrawShapeMode.Rectangle },
                )
                ModeChip(
                    selected = editor.shapeMode == DrawShapeMode.Ellipse,
                    label = stringResource(R.string.draw_shape_ellipse),
                    onClick = { editor.shapeMode = DrawShapeMode.Ellipse },
                )
            }
        }

        if (editor.toolId == DrawToolId.Pen || editor.toolId == DrawToolId.Highlighter || editor.toolId == DrawToolId.Shape) {
            DrawColorRow(
                selectedArgb = editor.colorArgb,
                onPick = { editor.colorArgb = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (editor.toolId) {
            DrawToolId.Pen, DrawToolId.Shape -> {
                LabeledSlider(
                    label = stringResource(R.string.draw_stroke_width),
                    value = editor.penWidth,
                    range = 0.5f..18f,
                    onValueChange = { editor.penWidth = it },
                )
            }

            DrawToolId.Highlighter -> {
                LabeledSlider(
                    label = stringResource(R.string.draw_stroke_width),
                    value = editor.highlighterWidth,
                    range = 4f..42f,
                    onValueChange = { editor.highlighterWidth = it },
                )
                LabeledSlider(
                    label = stringResource(R.string.draw_highlighter_alpha),
                    value = editor.highlighterAlpha,
                    range = 0.05f..0.9f,
                    onValueChange = { editor.highlighterAlpha = it },
                )
            }

            DrawToolId.Eraser -> {
                LabeledSlider(
                    label = stringResource(R.string.draw_eraser_size),
                    value = editor.eraserRadius,
                    range = 4f..64f,
                    onValueChange = { editor.eraserRadius = it },
                )
            }

            DrawToolId.Lasso -> {
                if (editor.selectedElementIds.isNotEmpty()) {
                    TextButton(onClick = onDeleteSelection) { Text(stringResource(R.string.action_delete)) }
                }
            }

            else -> Unit
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ZhixuIconButton(
                    onClick = {
                        editor.goToPreviousPage()
                        centerPage(editor, marginPx)
                    },
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(Ionicons.ChevronBack),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = "${editor.currentPageIndex + 1} / ${editor.pages.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ZhixuIconButton(
                    onClick = {
                        editor.goToNextPage()
                        centerPage(editor, marginPx)
                    },
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(Ionicons.ChevronForward),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
                ZhixuIconButton(
                    onClick = {
                        editor.addPageLikeCurrent()
                        centerPage(editor, marginPx)
                        onEdited()
                    },
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(Ionicons.AddCircleOutline),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            TextButton(onClick = onSaveAs) { Text(stringResource(R.string.draw_save_as_title)) }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ModeChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(String.format(Locale.US, "%.1f", value), style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

@Composable
private fun DrawColorRow(
    selectedArgb: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors =
        remember {
            listOf(
                0xFF000000.toInt(),
                0xFF1E88E5.toInt(),
                0xFF43A047.toInt(),
                0xFFF4511E.toInt(),
                0xFFE53935.toInt(),
                0xFF8E24AA.toInt(),
                0xFFFDD835.toInt(),
                0xFFFFFFFF.toInt(),
            )
        }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (argb in colors) {
            val isSelected = argb == selectedArgb
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .background(Color(argb), CircleShape)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onPick(argb) },
            )
        }
        Spacer(modifier = Modifier.width(1.dp))
    }
}

@Composable
private fun DrawEditorCanvas(
    editor: DrawEditorState,
    marginPx: Float,
    toolMachineFor: (DrawToolId) -> DrawToolMachine,
    onEdited: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canvasBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    val lassoColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)
    val eraserColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
    val pageBorderColor = Color(0xFFDDDDDD)

    var didInitViewport by remember(editor) { mutableStateOf(false) }

    Canvas(
        modifier =
            modifier
                .background(canvasBackground)
                .onSizeChanged { size ->
                    editor.viewport.viewportSize = size
                    if (!didInitViewport && size != IntSize.Zero) {
                        didInitViewport = true
                        fitPageToWidth(editor, marginPx)
                    } else {
                        snapViewport(editor, marginPx)
                    }
                }
                .pointerInput(editor, marginPx) {
                    handleDrawGestures(
                        editor = editor,
                        marginPx = marginPx,
                        toolMachineFor = toolMachineFor,
                        onEdited = onEdited,
                    )
                },
    ) {
        val page = editor.currentPageOrNull() ?: return@Canvas
        val scale = editor.viewport.scale.coerceAtLeast(0.0001f)
        val translation = editor.viewport.translation

        withTransform(
            transformBlock = {
                translate(translation.x, translation.y)
                scale(scale, scale)
            },
        ) {
            drawPageBackground(
                pageWidth = page.width,
                pageHeight = page.height,
                scale = scale,
                borderColor = pageBorderColor,
            )
            drawElements(
                elements = page.elements.toList(),
                editor = editor,
                scale = scale,
                selectionColor = selectionColor,
            )
            drawOverlays(
                editor = editor,
                pageWidth = page.width,
                pageHeight = page.height,
                scale = scale,
                lassoColor = lassoColor,
                eraserColor = eraserColor,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPageBackground(
    pageWidth: Float,
    pageHeight: Float,
    scale: Float,
    borderColor: Color,
) {
    drawRect(
        color = Color.White,
        topLeft = Offset.Zero,
        size = Size(pageWidth, pageHeight),
    )
    drawRect(
        color = borderColor,
        topLeft = Offset.Zero,
        size = Size(pageWidth, pageHeight),
        style = Stroke(width = 1f / scale.coerceAtLeast(0.0001f)),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawElements(
    elements: List<DrawElementState>,
    editor: DrawEditorState,
    scale: Float,
    selectionColor: Color,
) {
    for (el in elements) {
        when (el) {
            is DrawStrokeState -> {
                val pts = el.points
                if (pts.isEmpty()) continue
                val path = buildPath(pts)
                val color = Color(el.colorArgb).copy(alpha = el.alpha.coerceIn(0f, 1f))
                drawPath(
                    path = path,
                    color = color,
                    style =
                        Stroke(
                            width = el.width.coerceAtLeast(0.2f),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                )
            }

            is DrawShapeState -> {
                val color = Color(el.colorArgb).copy(alpha = el.alpha.coerceIn(0f, 1f))
                val w = el.width.coerceAtLeast(0.2f)
                val rect = rectFromPoints(el.start, el.end)
                when (el.shape) {
                    DrawShapeMode.Line ->
                        drawLine(
                            color = color,
                            start = el.start,
                            end = el.end,
                            strokeWidth = w,
                            cap = StrokeCap.Round,
                        )
                    DrawShapeMode.Rectangle ->
                        drawRect(
                            color = color,
                            topLeft = rect.topLeft,
                            size = rect.size,
                            style = Stroke(width = w),
                        )
                    DrawShapeMode.Ellipse ->
                        drawOval(
                            color = color,
                            topLeft = rect.topLeft,
                            size = rect.size,
                            style = Stroke(width = w),
                        )
                }
            }
        }

        if (el.id in editor.selectedElementIds) {
            val bounds = elementBounds(el)
            if (bounds != null) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(8f / scale, 8f / scale))
                drawRect(
                    color = selectionColor,
                    topLeft = bounds.topLeft,
                    size = bounds.size,
                    style = Stroke(width = 1.5f / scale, pathEffect = dash),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOverlays(
    editor: DrawEditorState,
    pageWidth: Float,
    pageHeight: Float,
    scale: Float,
    lassoColor: Color,
    eraserColor: Color,
) {
    editor.previewShape?.let { preview ->
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f / scale, 10f / scale))
        val color = Color(editor.colorArgb).copy(alpha = 0.9f)
        val w = editor.penWidth.coerceAtLeast(0.2f)
        val rect = rectFromPoints(preview.start, preview.end)
        when (preview.mode) {
            DrawShapeMode.Line ->
                drawLine(
                    color = color,
                    start = preview.start,
                    end = preview.end,
                    strokeWidth = w,
                    cap = StrokeCap.Round,
                    pathEffect = dash,
                )
            DrawShapeMode.Rectangle ->
                drawRect(
                    color = color,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = w, pathEffect = dash),
                )
            DrawShapeMode.Ellipse ->
                drawOval(
                    color = color,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = w, pathEffect = dash),
                )
        }
    }

    val lasso = editor.lassoPathPoints
    if (lasso.size >= 2) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f / scale, 8f / scale))
        val path = buildPath(lasso)
        drawPath(
            path = path,
            color = lassoColor,
            style = Stroke(width = 1.6f / scale, pathEffect = dash),
        )
    }

    editor.eraserCursor?.let { c ->
        drawCircle(
            color = eraserColor,
            center = c,
            radius = editor.eraserRadius.coerceAtLeast(1f),
            style = Stroke(width = 1.5f / scale),
        )
    }

    drawRect(
        color = Color.Transparent,
        topLeft = Offset.Zero,
        size = Size(pageWidth, pageHeight),
        style = Stroke(width = 0.5f / scale),
    )
}

private suspend fun PointerInputScope.handleDrawGestures(
    editor: DrawEditorState,
    marginPx: Float,
    toolMachineFor: (DrawToolId) -> DrawToolMachine,
    onEdited: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)

        var activeTool: DrawToolMachine = toolMachineFor(editor.toolId)
        val modifiesDocument =
            activeTool.id == DrawToolId.Pen ||
                activeTool.id == DrawToolId.Highlighter ||
                activeTool.id == DrawToolId.Shape ||
                activeTool.id == DrawToolId.Eraser

        val activePointerId = down.id
        var lastViewPos = down.position
        var lastPagePos = clampToPage(editor, editor.viewport.viewToPage(lastViewPos))

        activeTool.onDown(
            editor,
            ToolPointerEvent(
                viewPosition = lastViewPos,
                viewDelta = Offset.Zero,
                pagePosition = lastPagePos,
                pageDelta = Offset.Zero,
            ),
        )

        var isTransform = false
        var prevCentroid = down.position
        var prevSpan = 0f

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }

            if (pressed.size >= 2) {
                if (!isTransform) {
                    isTransform = true
                    activeTool.onCancel(editor)
                    editor.previewShape = null
                    editor.eraserCursor = null
                    editor.lassoPathPoints.clear()
                    prevCentroid = centroidOf(pressed)
                    prevSpan = spanOf(pressed)
                }

                val currCentroid = centroidOf(pressed)
                val currSpan = spanOf(pressed)
                val zoomChange = if (prevSpan > 0f) currSpan / prevSpan else 1f

                val oldScale = editor.viewport.scale.coerceAtLeast(0.0001f)
                val oldTranslation = editor.viewport.translation
                val newScale = (oldScale * zoomChange).coerceIn(MinScale, MaxScale)

                 val pageUnderPrevCentroid = (prevCentroid - oldTranslation) / oldScale
                 val newTranslation = currCentroid - pageUnderPrevCentroid * newScale

                 editor.viewport.scale = newScale
                 editor.viewport.translation = newTranslation

                 prevCentroid = currCentroid
                 prevSpan = currSpan

                pressed.forEach { it.consume() }
            } else if (!isTransform) {
                val change =
                    event.changes.firstOrNull { it.id == activePointerId }
                        ?: event.changes.firstOrNull()
                        ?: break

                val viewPos = change.position
                val viewDelta = viewPos - lastViewPos
                val pagePos = clampToPage(editor, editor.viewport.viewToPage(viewPos))
                val pageDelta = pagePos - lastPagePos
                val toolEvent =
                    ToolPointerEvent(
                        viewPosition = viewPos,
                        viewDelta = viewDelta,
                        pagePosition = pagePos,
                        pageDelta = pageDelta,
                    )

                if (change.pressed) {
                    activeTool.onMove(editor, toolEvent)
                    lastViewPos = viewPos
                    lastPagePos = pagePos
                    if (activeTool.id == DrawToolId.Pan) snapViewport(editor, marginPx)
                    change.consume()
                } else {
                    activeTool.onUp(editor, toolEvent)
                    if (modifiesDocument) onEdited()
                    if (activeTool.id == DrawToolId.Pan) snapViewport(editor, marginPx)
                    change.consume()
                    break
                }
            }

            if (event.changes.none { it.pressed }) {
                if (isTransform) snapViewport(editor, marginPx)
                break
            }
        }
    }
}

private fun centroidOf(changes: List<PointerInputChange>): Offset {
    if (changes.isEmpty()) return Offset.Zero
    var x = 0f
    var y = 0f
    for (c in changes) {
        x += c.position.x
        y += c.position.y
    }
    return Offset(x / changes.size.toFloat(), y / changes.size.toFloat())
}

private fun spanOf(changes: List<PointerInputChange>): Float {
    if (changes.size < 2) return 0f
    val a = changes[0].position
    val b = changes[1].position
    return hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat().coerceAtLeast(0f)
}

private fun snapViewport(editor: DrawEditorState, marginPx: Float) {
    val page = editor.currentPageOrNull() ?: return
    val size = editor.viewport.viewportSize
    if (size == IntSize.Zero) return
    editor.viewport.translation =
        clampTranslation(
            viewportSize = size,
            pageWidth = page.width,
            pageHeight = page.height,
            scale = editor.viewport.scale,
            translation = editor.viewport.translation,
            marginPx = marginPx,
        )
}

private fun fitPageToWidth(editor: DrawEditorState, marginPx: Float) {
    val page = editor.currentPageOrNull() ?: return
    val size = editor.viewport.viewportSize
    if (size == IntSize.Zero) return
    val availableW = (size.width.toFloat() - marginPx * 2f).coerceAtLeast(1f)
    val scale = (availableW / page.width.coerceAtLeast(1f)).coerceIn(MinScale, MaxScale)
    editor.viewport.scale = scale
    editor.viewport.translation = Offset(marginPx, marginPx)
    snapViewport(editor, marginPx)
}

private fun centerPage(editor: DrawEditorState, marginPx: Float) {
    val page = editor.currentPageOrNull() ?: return
    val size = editor.viewport.viewportSize
    if (size == IntSize.Zero) return
    val scale = editor.viewport.scale.coerceAtLeast(0.0001f)
    val vw = size.width.toFloat()
    val vh = size.height.toFloat()
    val contentW = page.width * scale
    val contentH = page.height * scale
    editor.viewport.translation = Offset((vw - contentW) / 2f, (vh - contentH) / 2f)
    snapViewport(editor, marginPx)
}

private fun clampTranslation(
    viewportSize: IntSize,
    pageWidth: Float,
    pageHeight: Float,
    scale: Float,
    translation: Offset,
    marginPx: Float,
): Offset {
    if (viewportSize == IntSize.Zero) return translation
    val vw = viewportSize.width.toFloat()
    val vh = viewportSize.height.toFloat()
    val s = scale.coerceAtLeast(0.0001f)
    val contentW = pageWidth * s
    val contentH = pageHeight * s

    val minX = vw - contentW - marginPx
    val maxX = marginPx
    val minY = vh - contentH - marginPx
    val maxY = marginPx

    val x =
        if (contentW + marginPx * 2f <= vw) {
            (vw - contentW) / 2f
        } else {
            translation.x.coerceIn(minX, maxX)
        }
    val y =
        if (contentH + marginPx * 2f <= vh) {
            (vh - contentH) / 2f
        } else {
            translation.y.coerceIn(minY, maxY)
        }
    return Offset(x, y)
}

private fun clampToPage(editor: DrawEditorState, pagePoint: Offset): Offset {
    val page = editor.currentPageOrNull() ?: return pagePoint
    val x = pagePoint.x.coerceIn(0f, page.width)
    val y = pagePoint.y.coerceIn(0f, page.height)
    return Offset(x, y)
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

private fun elementBounds(el: DrawElementState): Rect? {
    return when (el) {
        is DrawStrokeState -> {
            val pts = el.points
            if (pts.isEmpty()) {
                null
            } else {
                var minX = Float.POSITIVE_INFINITY
                var minY = Float.POSITIVE_INFINITY
                var maxX = Float.NEGATIVE_INFINITY
                var maxY = Float.NEGATIVE_INFINITY
                for (p in pts) {
                    minX = minOf(minX, p.x)
                    minY = minOf(minY, p.y)
                    maxX = maxOf(maxX, p.x)
                    maxY = maxOf(maxY, p.y)
                }
                val pad = el.width.coerceAtLeast(1f) * 0.6f
                Rect(minX - pad, minY - pad, maxX + pad, maxY + pad)
            }
        }

        is DrawShapeState -> {
            val rect = rectFromPoints(el.start, el.end)
            val pad = el.width.coerceAtLeast(1f) * 0.6f
            Rect(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad)
        }

        else -> null
    }
}

private fun sanitizeFileName(input: String): String {
    val trimmed = input.trim().trim('.')
    if (trimmed.isBlank()) return ""
    return trimmed
        .replace('/', '_')
        .replace('\\', '_')
        .replace(':', '_')
        .replace('*', '_')
        .replace('?', '_')
        .replace('"', '_')
        .replace('<', '_')
        .replace('>', '_')
        .replace('|', '_')
        .take(80)
        .trim()
        .trim('.')
}

private fun guessTitleFromUri(uri: Uri?): String? {
    if (uri == null) return null
    if (uri.scheme.equals("file", ignoreCase = true)) {
        val path = uri.path ?: return null
        return ZhixuDrawFormat.stripDrawingExtension(File(path).name)
    }
    val last = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()
    if (last.isBlank()) return null
    return ZhixuDrawFormat.stripDrawingExtension(last)
}
