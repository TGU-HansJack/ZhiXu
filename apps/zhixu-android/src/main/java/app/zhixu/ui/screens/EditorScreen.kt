package app.zhixu.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.zhixu.R
import app.zhixu.data.dataStore
import app.zhixu.data.AppLogRepository
import app.zhixu.data.LogPreferences
import app.zhixu.data.VaultRepository
import app.zhixu.ui.Heroicons
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.sync.SyncServerClient
import app.zhixu.sync.resolveSyncServerAuth
import app.zhixu.sync.VaultAutoSync
import app.zhixu.plugins.InstalledPlugin
import app.zhixu.plugins.FrontMatterParser
import app.zhixu.plugins.PluginRepository
import app.zhixu.plugins.runtime.EditorActionContext
import app.zhixu.plugins.runtime.JsPluginRuntime
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import app.zhixu.ui.components.CodeMirrorMarkdownEditor
import app.zhixu.ui.components.MarkdownPreview
import app.zhixu.ui.components.PdfPreview
import app.zhixu.ui.components.PdfPreviewController
import app.zhixu.ui.components.PdfLayoutMode
import app.zhixu.ui.components.RadialFabAction
import app.zhixu.ui.components.LineDiff
import app.zhixu.ui.components.DiffOp
import app.zhixu.ui.components.DiffLine
import app.zhixu.ui.components.ZhixuDialogDefaults
import app.zhixu.ui.components.SheetActionRow
import app.zhixu.ui.components.SheetQuickAction
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuCenterAlignedTopAppBar
import app.zhixu.ui.components.ZhixuSwitch
import app.zhixu.ui.components.VaultDrawer
import app.zhixu.ui.components.ZhixuSwipeDualDrawer
import app.zhixu.core.tasks.TaskSyntax
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import org.json.JSONObject
import androidx.documentfile.provider.DocumentFile
import app.zhixu.ui.DocListMutation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

data class LongImageRequest(
    val markdown: String,
    val vaultRootUri: Uri?,
    val fontScale: Float,
    val title: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    docUri: Uri,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    onBack: () -> Unit,
    onDocListMutated: (DocListMutation) -> Unit,
    onOpenDoc: (String, String?, Int?) -> Unit,
    onGenerateLongImage: (LongImageRequest) -> Unit,
    initialQuery: String?,
    initialLineIndex: Int?,
    dirStructureMutationToken: Long = 0L,
    dirStructureMutation: DocListMutation? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val topBarSurfaceColor = MaterialTheme.colorScheme.surface
    DisposableEffect(view, topBarSurfaceColor) {
        val activity = view.context.findActivity() ?: return@DisposableEffect onDispose { }
        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, view)
        val previousStatusBarColor = window.statusBarColor
        val previousLightStatusBars = controller.isAppearanceLightStatusBars

        window.statusBarColor = topBarSurfaceColor.toArgb()
        controller.isAppearanceLightStatusBars = topBarSurfaceColor.luminance() > 0.5f

        onDispose {
            window.statusBarColor = previousStatusBarColor
            controller.isAppearanceLightStatusBars = previousLightStatusBars
        }
    }
    val pluginRepo = remember(context) { PluginRepository(context) }
    val logPrefs = remember(context) { LogPreferences(context.applicationContext) }
    val debugLogs by logPrefs.debugEnabled.collectAsState(initial = false)
    val appLogs = remember(context) { AppLogRepository(context.applicationContext) }
    var title by remember { mutableStateOf("") }
    var titleFieldValue by remember { mutableStateOf(TextFieldValue(text = "")) }
    var originalFileName by remember { mutableStateOf("") }
    var content by remember { mutableStateOf(TextFieldValue("")) }
    var isPreview by remember { mutableStateOf(false) }
    var isPdfDoc by remember { mutableStateOf(false) }
    val pdfController = remember { PdfPreviewController() }
    var pdfCurrentPage by remember { mutableIntStateOf(1) }
    var pdfTotalPages by remember { mutableIntStateOf(0) }
    var pdfLayoutMode by remember { mutableStateOf(PdfLayoutMode.FitWidth) }
    var showPdfMenu by remember { mutableStateOf(false) }
    var showPdfThumbnails by remember { mutableStateOf(false) }
    var pdfPageInput by remember { mutableStateOf("1") }
    val docNameScrollState = rememberScrollState()
    var docNameFieldWidthPx by remember { mutableIntStateOf(0) }
    var docNameLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isRenamingDoc by remember { mutableStateOf(false) }
    val docNameFocusRequester = remember { FocusRequester() }
    var docNameHadFocus by remember { mutableStateOf(false) }
    var showOverflowSheet by remember { mutableStateOf(false) }
    val overflowSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showFontSizeSheet by remember { mutableStateOf(false) }
    val fontSizeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    var imeBottomPx by remember { mutableIntStateOf(0) }
    val isImeVisible = imeBottomPx > 0
    val showEditorToolbar = !isPreview && !isPdfDoc
    val fallbackToolbarHeightPx = with(density) { 56.dp.roundToPx() }
    var toolbarHeightPx by remember { mutableIntStateOf(fallbackToolbarHeightPx) }
    val toolbarGapPx = with(density) { 10.dp.roundToPx() }
    val anticipatedToolbarPx = if (showEditorToolbar) (toolbarHeightPx + toolbarGapPx) else 0
    val navBarsBottomDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val drawerGestureExcludeBottomDp =
        if (showEditorToolbar && !isImeVisible) {
            navBarsBottomDp + with(density) { (toolbarHeightPx + toolbarGapPx).toDp() }
        } else {
            0.dp
        }

    LaunchedEffect(isRenamingDoc) {
        docNameHadFocus = false
        if (isRenamingDoc) {
            titleFieldValue = TextFieldValue(text = title, selection = TextRange(title.length))
            docNameFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(title, isRenamingDoc) {
        if (!isRenamingDoc) {
            titleFieldValue = TextFieldValue(text = title)
        }
    }

    LaunchedEffect(
        isRenamingDoc,
        titleFieldValue.text,
        titleFieldValue.selection,
        docNameFieldWidthPx,
        docNameLayoutResult,
    ) {
        if (!isRenamingDoc) return@LaunchedEffect
        val layout = docNameLayoutResult ?: return@LaunchedEffect
        if (docNameFieldWidthPx <= 0) return@LaunchedEffect

        val cursorOffset = titleFieldValue.selection.end.coerceIn(0, titleFieldValue.text.length)
        val cursorRect = layout.getCursorRect(cursorOffset)
        val marginPx = with(density) { 12.dp.toPx() }
        val visibleStartPx = docNameScrollState.value.toFloat()
        val visibleEndPx = visibleStartPx + docNameFieldWidthPx

        val targetScrollPx =
            when {
                cursorRect.right + marginPx > visibleEndPx -> cursorRect.right + marginPx - docNameFieldWidthPx
                cursorRect.left - marginPx < visibleStartPx -> cursorRect.left - marginPx
                else -> visibleStartPx
            }

        docNameScrollState.scrollTo(targetScrollPx.toInt().coerceIn(0, docNameScrollState.maxValue))
    }

    var openOutlineToken by remember { mutableStateOf(0L) }

    val editorFontSizeKey = remember { floatPreferencesKey("editor_font_size_sp") }
    val editorSourceModeKey = remember { booleanPreferencesKey("editor_source_mode") }
    var editorFontSizeSpValue by remember { mutableStateOf(16f) }
    var isSourceMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        editorFontSizeSpValue = (prefs[editorFontSizeKey] ?: 16f).coerceIn(12f, 28f)
        isSourceMode = prefs[editorSourceModeKey] ?: false
    }

    fun persistEditorFontSize(next: Float) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[editorFontSizeKey] = next.coerceIn(12f, 28f)
            }
        }
    }

    fun persistEditorSourceMode(next: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[editorSourceModeKey] = next
            }
        }
    }

    var showLinkDialog by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }

    var showImageDialog by remember { mutableStateOf(false) }
    var imageAlt by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }

    var showCodeDialog by remember { mutableStateOf(false) }
    var codeLanguage by remember { mutableStateOf("python") }

    var showTableDialog by remember { mutableStateOf(false) }
    var tableRowsText by remember { mutableStateOf("2") }
    var tableColsText by remember { mutableStateOf("2") }

    var autosaveJob by remember { mutableStateOf<Job?>(null) }
    var exitSaveJob by remember { mutableStateOf<Job?>(null) }
    var isExitSaveInProgress by remember { mutableStateOf(false) }
    var isLoaded by remember { mutableStateOf(false) }
    var lastPersistedText by remember { mutableStateOf<String?>(null) }
    var lastParsedText by remember { mutableStateOf<String?>(null) }
    val isDirty by remember { derivedStateOf { isLoaded && lastPersistedText != null && content.text != lastPersistedText } }

    val undoStack = remember { ArrayDeque<TextFieldValue>() }
    val redoStack = remember { ArrayDeque<TextFieldValue>() }
    var lastHistoryStampMs by remember { mutableStateOf(0L) }

    var showFindReplace by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var pendingInitialJump by remember { mutableStateOf<JumpTarget?>(null) }
    var currentDocUri by remember(docUri) { mutableStateOf(docUri) }
    var outline by remember { mutableStateOf<List<OutlineItem>>(emptyList()) }
    var wikiLinks by remember { mutableStateOf<List<String>>(emptyList()) }
    var pluginFabActions by remember { mutableStateOf<List<RadialFabAction>>(emptyList()) }

    val latestOnBack by rememberUpdatedState(onBack)
    val latestOnOpenDoc by rememberUpdatedState(onOpenDoc)
    val latestIsLoaded by rememberUpdatedState(isLoaded)
    val latestIsDirty by rememberUpdatedState(isDirty)
    val latestDocUri by rememberUpdatedState(currentDocUri)
    val latestContentText by rememberUpdatedState(content.text)
    val latestTitle by rememberUpdatedState(title)
    val latestOriginalFileName by rememberUpdatedState(originalFileName)
    val latestOutline by rememberUpdatedState(outline)
    val latestWikiLinks by rememberUpdatedState(wikiLinks)
    val latestPdfCurrentPage by rememberUpdatedState(pdfCurrentPage)
    val latestPdfTotalPages by rememberUpdatedState(pdfTotalPages)

    fun decodeDataUrlToImageBitmap(dataUrl: String): ImageBitmap? {
        val comma = dataUrl.indexOf(',')
        val raw = if (comma >= 0) dataUrl.substring(comma + 1) else dataUrl
        val bytes = runCatching { Base64.decode(raw, Base64.DEFAULT) }.getOrNull() ?: return null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return bmp.asImageBitmap()
    }

    suspend fun persistNow(uri: Uri, text: String): Boolean {
        val beforeCached = lastPersistedText
        val beforeForDelta =
            beforeCached
                ?: runCatching {
                    withContext(Dispatchers.IO) { repository.readText(uri) }
                }.getOrNull()
        val ok =
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.writeText(uri, text)
                    repository.indexDocUri(uri)
                }
            }.isSuccess
        if (!ok) return false

        runCatching {
            VaultAutoSync.maybeUploadDoc(
                context = context,
                repository = repository,
                vaultRootUri = vaultRootUri,
                docUri = uri,
                force = false,
            )
        }

        lastPersistedText = text
        EditorScreenCache.put(
            uri,
            EditorScreenCache.Entry(
                text = text,
                title = latestTitle,
                originalFileName = latestOriginalFileName,
                outline = latestOutline,
                wikiLinks = latestWikiLinks,
            ),
        )

        repository.recordDailyDocEdited(docUri = uri)
        val beforeDone = beforeForDelta?.let { runCatching { app.zhixu.core.tasks.TaskSyntax.parseTasks(it).count { t -> t.checked } }.getOrNull() } ?: 0
        val afterDone = runCatching { app.zhixu.core.tasks.TaskSyntax.parseTasks(text).count { t -> t.checked } }.getOrNull() ?: 0
        val deltaDone = (afterDone - beforeDone).coerceAtLeast(0)
        if (deltaDone > 0) repository.recordDailyTasksDone(delta = deltaDone)

        return true
    }

    fun requestExit() {
        if (!latestIsLoaded) {
            latestOnBack()
            return
        }

        if (!latestIsDirty || isExitSaveInProgress) {
            latestOnBack()
            return
        }

        val uriSnapshot = latestDocUri
        val textSnapshot = latestContentText
        autosaveJob?.cancel()
        exitSaveJob?.cancel()
        isExitSaveInProgress = true
        exitSaveJob =
            scope.launch {
                val ok = persistNow(uriSnapshot, textSnapshot)
                if (ok) {
                    latestOnBack()
                } else {
                    isExitSaveInProgress = false
                    snackbarHostState.showSnackbar(context.getString(R.string.editor_save_failed_generic))
                }
            }
    }

    BackHandler(enabled = true) {
        requestExit()
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event != Lifecycle.Event.ON_PAUSE && event != Lifecycle.Event.ON_STOP) return@LifecycleEventObserver
                if (!latestIsLoaded) return@LifecycleEventObserver
                if (!latestIsDirty) return@LifecycleEventObserver
                if (isExitSaveInProgress) return@LifecycleEventObserver

                val uriSnapshot = latestDocUri
                val textSnapshot = latestContentText
                autosaveJob?.cancel()
                exitSaveJob?.cancel()
                exitSaveJob =
                    scope.launch {
                        persistNow(uriSnapshot, textSnapshot)
                    }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun iconForPluginAction(name: String?) =
        when (name?.lowercase()?.trim()) {
            "cloud_upload", "upload", "publish" -> Icons.Outlined.CloudUpload
            else -> Icons.Outlined.Extension
        }

    fun applyPdfJump() {
        val total = latestPdfTotalPages
        val desired = pdfPageInput.trim().toIntOrNull() ?: return
        val clamped = if (total > 0) desired.coerceIn(1, total) else desired.coerceAtLeast(1)
        pdfController.goToPage(clamped)
        pdfCurrentPage = clamped
        pdfPageInput = clamped.toString()
        focusManager.clearFocus(force = true)
    }

    fun buildPluginFabActions(installed: List<InstalledPlugin>): List<RadialFabAction> {
        val actions =
            installed
                .filter { it.enabled }
                .flatMap { plugin ->
                    plugin.manifest.actions
                        .filter { it.place == null || it.place.equals("editor_fab", ignoreCase = true) }
                        .map { spec ->
                            RadialFabAction(
                                id = "plugin:${plugin.manifest.id}/${spec.id}",
                                label = spec.label,
                                icon = iconForPluginAction(spec.icon),
                                ringIndex = spec.ringIndex ?: 0,
                                angleDegrees = 0f,
                            )
                        }
                }
        if (actions.isNotEmpty()) return actions
        return listOf(
            RadialFabAction(
                id = "plugins:none",
                label = "暂无启用的插件",
                icon = Icons.Outlined.Extension,
                ringIndex = 0,
                angleDegrees = 0f,
            ),
        )
    }

    LaunchedEffect(vaultRootUri) {
        val root = vaultRootUri
        if (root == null) {
            pluginFabActions = buildPluginFabActions(emptyList())
            return@LaunchedEffect
        }
        val installed =
            runCatching { withContext(Dispatchers.IO) { pluginRepo.listInstalled(root) } }
                .getOrElse { emptyList() }
        pluginFabActions = buildPluginFabActions(installed)
    }

    fun handlePluginAction(action: RadialFabAction) {
        when {
            action.id == "plugins:none" -> {
                scope.launch { snackbarHostState.showSnackbar("请到 设置 -> 创意工坊 启用/安装插件") }
            }

            action.id.startsWith("plugin:") -> {
                val root = vaultRootUri
                if (root == null) {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_vault_not_selected)) }
                    return
                }

                val raw = action.id.removePrefix("plugin:")
                val slash = raw.indexOf('/')
                if (slash <= 0 || slash >= raw.length - 1) {
                    scope.launch { snackbarHostState.showSnackbar("Invalid plugin action: ${action.id}") }
                    return
                }
                val pluginId = raw.substring(0, slash)
                val actionId = raw.substring(slash + 1)

                scope.launch {
                    try {
                        val result =
                            withContext(Dispatchers.IO) {
                                val runtime =
                                    JsPluginRuntime(
                                        appContext = context.applicationContext,
                                        pluginRepo = pluginRepo,
                                        appLogs = appLogs,
                                        debugLogging = debugLogs,
                                    )

                                val res =
                                    runtime.runEditorAction(
                                        rootUri = root,
                                        pluginId = pluginId,
                                        actionId = actionId,
                                        ctx =
                                            EditorActionContext(
                                            docUri = currentDocUri,
                                            title = title,
                                            fileName = originalFileName,
                                                text = content.text,
                                            ),
                                    )
                                appLogs.appendBlocking(
                                    AppLogRepository.Kind.Operation,
                                    "[Plugin] $pluginId/$actionId ok=${res.ok} msg=${res.message}",
                                )
                                res
                            }

                        if (result.ok && result.setText != null) {
                            withContext(Dispatchers.IO) {
                                repository.writeText(currentDocUri, result.setText)
                                repository.indexDocUri(currentDocUri)
                            }
                            content = content.copy(text = result.setText)
                            lastPersistedText = result.setText
                        }

                        snackbarHostState.showSnackbar(result.message)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                        withContext(Dispatchers.IO) {
                            appLogs.appendBlocking(
                                AppLogRepository.Kind.Operation,
                                "[PluginError] $pluginId/$actionId $msg",
                            )
                        }
                        snackbarHostState.showSnackbar("插件执行异常：$msg")
                    }
                }
            }

            else -> Unit
        }
    }

    LaunchedEffect(imeInsets, density) {
        snapshotFlow { imeInsets.getBottom(density) }
            .distinctUntilChanged()
            .collectLatest { bottom ->
                imeBottomPx = bottom
            }
    }

    LaunchedEffect(docUri) {
        isLoaded = false
        if (docUri.toString().isBlank()) {
            snackbarHostState.showSnackbar(context.getString(R.string.editor_load_failed_generic))
            onBack()
            return@LaunchedEffect
        }

        EditorScreenCache.get(docUri)?.let { cached ->
            originalFileName = cached.originalFileName
            isPdfDoc = cached.originalFileName.endsWith(".pdf", ignoreCase = true)
            if (!isPdfDoc) {
                title = cached.title
                content = TextFieldValue(cached.text)
                outline = cached.outline
                wikiLinks = cached.wikiLinks
                lastParsedText = cached.text
                lastPersistedText = cached.text
                isLoaded = true
            }
        }

        val (fileName, loaded, detectedPdf) =
            runCatching {
                withContext(Dispatchers.IO) {
                    val name =
                        if (docUri.scheme.equals("file", ignoreCase = true)) {
                            docUri.path?.let { java.io.File(it).name }.orEmpty()
                        } else {
                            DocumentFile.fromSingleUri(context, docUri)?.name.orEmpty()
                        }
                    val mime = runCatching { context.contentResolver.getType(docUri).orEmpty() }.getOrDefault("")
                    val isPdf = name.endsWith(".pdf", ignoreCase = true) || mime.equals("application/pdf", ignoreCase = true)
                    val text = if (isPdf) "" else repository.readText(docUri)
                    Triple(name, text, isPdf)
                }
            }.getOrElse { e ->
                val msg =
                    when (e) {
                        is SecurityException -> context.getString(R.string.editor_load_failed_permission)
                        is FileNotFoundException -> context.getString(R.string.editor_load_failed_not_found)
                        else -> context.getString(R.string.editor_load_failed_generic)
                    }
                snackbarHostState.showSnackbar(msg)
                Triple("", "", false)
            }

        originalFileName = fileName
        isPdfDoc = detectedPdf
        if (detectedPdf) {
            title =
                fileName
                    .removeSuffix(".pdf")
                    .ifBlank { fileName.ifBlank { context.getString(R.string.new_doc_default_title) } }
            isPreview = true
            content = TextFieldValue("")
            undoStack.clear()
            redoStack.clear()
            outline = emptyList()
            wikiLinks = emptyList()
            lastParsedText = null
            lastPersistedText = null
            isLoaded = true
            pendingInitialJump = null
            return@LaunchedEffect
        }
        title = fileName.removeSuffix(".md").ifBlank { context.getString(R.string.new_doc_default_title) }

        if (loaded != content.text) {
            content = TextFieldValue(loaded)
            undoStack.clear()
            redoStack.clear()
            outline = emptyList()
            wikiLinks = emptyList()
            lastParsedText = null
        }
        lastPersistedText = loaded
        isLoaded = true
        EditorScreenCache.put(
            docUri,
            EditorScreenCache.Entry(
                text = loaded,
                title = title,
                originalFileName = originalFileName,
                outline = outline,
                wikiLinks = wikiLinks,
            ),
        )

        val jumpText = content.text
        val jump =
            when {
                initialLineIndex != null && initialLineIndex >= 0 -> {
                    val start = lineStartOffset(jumpText, initialLineIndex)
                    val end = lineEndOffset(jumpText, start)
                    JumpTarget(start = start, end = end)
                }

                !initialQuery.isNullOrBlank() -> {
                    val match = findFirstMatchOffset(jumpText, initialQuery)
                    if (match != null) JumpTarget(start = match.first, end = match.second) else null
                }

                else -> null
            }
        pendingInitialJump = jump
        if (!initialQuery.isNullOrBlank()) {
            findText = initialQuery
        }
    }

    fun pushHistoryIfNeeded(previous: TextFieldValue) {
        val now = System.currentTimeMillis()
        if (now - lastHistoryStampMs < 500) return
        lastHistoryStampMs = now
        if (undoStack.size >= 100) undoStack.removeFirst()
        undoStack.addLast(previous)
        redoStack.clear()
    }

    fun wrapSelection(prefix: String, suffix: String) {
        val text = content.text
        val sel = content.selection
        val start = sel.start.coerceIn(0, text.length)
        val end = sel.end.coerceIn(0, text.length)
        pushHistoryIfNeeded(content)
        if (start == end) {
            val inserted = prefix + suffix
            content =
                content.copy(
                    text = text.substring(0, start) + inserted + text.substring(start),
                    selection = TextRange(start + prefix.length, start + prefix.length),
                )
        } else {
            content =
                content.copy(
                    text = text.substring(0, start) + prefix + text.substring(start, end) + suffix + text.substring(end),
                    selection = TextRange(start + prefix.length, end + prefix.length),
                )
        }
    }

    fun prefixCurrentLine(prefix: String) {
        val text = content.text
        val sel = content.selection.start.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', startIndex = (sel - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        pushHistoryIfNeeded(content)
        content =
            content.copy(
                text = text.substring(0, lineStart) + prefix + text.substring(lineStart),
                selection = TextRange(sel + prefix.length),
            )
    }

    fun applyReplaceAll() {
        val f = findText
        if (f.isBlank()) return
        val replaced = content.text.replace(f, replaceText)
        if (replaced == content.text) return
        pushHistoryIfNeeded(content)
        content = content.copy(text = replaced)
    }

    fun toggleTaskAtCursor() {
        val lineIndex = content.text.take(content.selection.start).count { it == '\n' }
        val toggled = TaskSyntax.toggleTaskAtLine(content.text, lineIndex)
        if (toggled != content.text) {
            pushHistoryIfNeeded(content)
            content = content.copy(text = toggled)
        }
    }

    fun insertTaskLine() {
        prefixCurrentLine("- [ ] ")
    }

    fun insertField(field: String) {
        val text = content.text
        val sel = content.selection.start.coerceIn(0, text.length)
        val prefix = if (sel > 0 && !text[sel - 1].isWhitespace()) " " else ""
        val insert = prefix + field
        pushHistoryIfNeeded(content)
        content =
            content.copy(
                text = text.substring(0, sel) + insert + text.substring(sel),
                selection = TextRange(sel + insert.length),
            )
    }

    fun insertHeading(level: Int) {
        val p = "#".repeat(level.coerceIn(1, 6)) + " "
        val text = content.text
        val sel = content.selection
        val start = sel.start.coerceIn(0, text.length)
        val end = sel.end.coerceIn(0, text.length)

        fun replaceHeadingPrefix(line: String): String {
            val trimmed = line.trimStart()
            val leadingSpaces = line.take(line.length - trimmed.length)
            val withoutOld = trimmed.replaceFirst(Regex("""^#{1,6}\s+"""), "")
            return leadingSpaces + p + withoutOld
        }

        if (start != end) {
            val startLineStart = text.lastIndexOf('\n', startIndex = (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
            val endLineEnd = text.indexOf('\n', startIndex = end).let { if (it < 0) text.length else it }
            val block = text.substring(startLineStart, endLineEnd)
            val lines = block.split('\n')
            val replaced = lines.joinToString("\n") { replaceHeadingPrefix(it) }
            pushHistoryIfNeeded(content)
            content =
                content.copy(
                    text = text.substring(0, startLineStart) + replaced + text.substring(endLineEnd),
                    selection = TextRange(startLineStart, startLineStart + replaced.length),
                )
            return
        }

        val lineStart = text.lastIndexOf('\n', startIndex = (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', startIndex = start).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val updated = replaceHeadingPrefix(line)
        pushHistoryIfNeeded(content)
        content =
            content.copy(
                text = text.substring(0, lineStart) + updated + text.substring(lineEnd),
                selection = TextRange((start + (updated.length - line.length)).coerceIn(0, (text.length + (updated.length - line.length)).coerceAtLeast(0))),
            )
    }

    fun insertOrderedList() = prefixCurrentLine("1. ")
    fun insertUnorderedList() = prefixCurrentLine("- ")
    fun insertQuote() = prefixCurrentLine("> ")
    fun insertDivider() {
        val text = content.text
        val sel = content.selection.start.coerceIn(0, text.length)
        val insert = "\n---\n"
        pushHistoryIfNeeded(content)
        content = content.copy(text = text.substring(0, sel) + insert + text.substring(sel), selection = TextRange(sel + insert.length))
    }

    fun insertCodeBlock(language: String? = null) {
        val lang = language?.trim().orEmpty()
        val fence = if (lang.isBlank()) "```" else "```$lang"
        wrapSelection("$fence\n", "\n```\n")
    }

    fun insertLink() = wrapSelection("[", "](url)")
    fun insertImage() = insertField("![](path)")
    fun insertTableTemplate() {
        val table =
            """
            | Col1 | Col2 |
            | --- | --- |
            |  |  |
            """.trimIndent()
        insertField("\n$table\n")
    }

    fun insertMermaidTemplate() {
        val body = "graph TD;\nA-->B;"
        insertField("\n```mermaid\n$body\n```\n")
    }

    fun insertInlineMath() = wrapSelection("$", "$")
    fun insertBlockMath() = wrapSelection("$$\n", "\n$$\n")

    fun openLinkDialog() {
        val text = content.text
        val sel = content.selection
        val start = sel.start.coerceIn(0, text.length)
        val end = sel.end.coerceIn(0, text.length)
        val selected = if (start != end) text.substring(start, end) else ""
        linkText = selected
        val clip = clipboard.getText()?.text.orEmpty()
        linkUrl = if (clip.startsWith("http://") || clip.startsWith("https://")) clip else ""
        showLinkDialog = true
    }

    fun insertLinkMarkdown(label: String, url: String) {
        val u = url.trim()
        if (u.isBlank()) return
        val t = content.text
        val sel = content.selection
        val start = sel.start.coerceIn(0, t.length)
        val end = sel.end.coerceIn(0, t.length)
        pushHistoryIfNeeded(content)

        if (start != end) {
            val selected = t.substring(start, end)
            val md = "[${label.ifBlank { selected }}]($u)"
            content =
                content.copy(
                    text = t.substring(0, start) + md + t.substring(end),
                    selection = TextRange(start + md.length),
                )
        } else {
            val md = "[${label}]($u)"
            val insertAt = start
            val cursor =
                if (label.isBlank()) {
                    insertAt + 1
                } else {
                    insertAt + md.length
                }
            content =
                content.copy(
                    text = t.substring(0, insertAt) + md + t.substring(insertAt),
                    selection = TextRange(cursor),
                )
        }
    }

    fun openImageDialog() {
        val clip = clipboard.getText()?.text.orEmpty()
        imageUrl = if (clip.startsWith("http://") || clip.startsWith("https://")) clip else ""
        imageAlt = ""
        showImageDialog = true
    }

    fun insertImageMarkdown(alt: String, url: String) {
        val u = url.trim()
        if (u.isBlank()) return
        val a = alt.trim()
        insertField("![$a]($u)")
    }

    fun insertImageFromUri(uri: Uri) {
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()
        insertField("![$name]($uri)")
    }

    val imagePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { picked: Uri? ->
            if (picked == null) return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    picked,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            insertImageFromUri(picked)
        }

    fun openCodeDialog() {
        showCodeDialog = true
    }

    fun openTableDialog() {
        showTableDialog = true
    }

    fun insertTable(rows: Int, cols: Int) {
        val safeRows = rows.coerceIn(1, 50)
        val safeCols = cols.coerceIn(1, 20)
        val header = (1..safeCols).joinToString(" | ", prefix = "| ", postfix = " |") { "Col$it" }
        val sep = (1..safeCols).joinToString(" | ", prefix = "| ", postfix = " |") { "---" }
        val body =
            (1..safeRows).joinToString("\n") {
                (1..safeCols).joinToString(" | ", prefix = "| ", postfix = " |") { "" }
            }
        insertField("\n$header\n$sep\n$body\n")
    }

    LaunchedEffect(isLoaded, content.text) {
        if (!isLoaded) return@LaunchedEffect
        val snapshot = content.text
        if (snapshot == lastParsedText) return@LaunchedEffect
        delay(350)
        val (nextOutline, nextWikiLinks) =
            withContext(Dispatchers.Default) {
                parseOutline(snapshot) to parseWikiLinks(snapshot)
            }
        lastParsedText = snapshot
        if (nextOutline != outline) outline = nextOutline
        if (nextWikiLinks != wikiLinks) wikiLinks = nextWikiLinks
        EditorScreenCache.put(
            currentDocUri,
            EditorScreenCache.Entry(
                text = snapshot,
                title = title,
                originalFileName = originalFileName,
                outline = nextOutline,
                wikiLinks = nextWikiLinks,
            ),
        )
    }

    fun jumpToSelection(startOffset: Int, endOffset: Int = startOffset) {
        val text = content.text
        val safeStart = startOffset.coerceIn(0, text.length)
        val safeEnd = endOffset.coerceIn(0, text.length).coerceAtLeast(safeStart)
        content = content.copy(selection = TextRange(safeStart, safeEnd))
    }

    LaunchedEffect(isLoaded, isPreview, pendingInitialJump) {
        val jump = pendingInitialJump ?: return@LaunchedEffect
        if (!isLoaded || isPreview) return@LaunchedEffect
        pendingInitialJump = null
        jumpToSelection(jump.start, jump.end)
    }

    LaunchedEffect(content.text, isLoaded, isPreview, currentDocUri, isDirty) {
        if (!isLoaded || isPreview || !isDirty) return@LaunchedEffect
        autosaveJob?.cancel()
        val textSnapshot = content.text
        val uriSnapshot = currentDocUri
        autosaveJob =
            scope.launch {
                delay(1200)
                if (!isDirty || content.text != textSnapshot) return@launch
                runCatching {
                    withContext(Dispatchers.IO) {
                        repository.writeText(uriSnapshot, textSnapshot)
                        repository.indexDocUri(uriSnapshot)
                    }
                }.onSuccess {
                    lastPersistedText = textSnapshot
                    EditorScreenCache.put(
                        uriSnapshot,
                        EditorScreenCache.Entry(
                            text = textSnapshot,
                            title = title,
                            originalFileName = originalFileName,
                            outline = outline,
                            wikiLinks = wikiLinks,
                        ),
                    )
                }
            }
    }

    LaunchedEffect(isLoaded, title, currentDocUri) {
        if (!isLoaded) return@LaunchedEffect
        if (!originalFileName.endsWith(".md", ignoreCase = true)) return@LaunchedEffect
        val desiredName = title.trim()
        if (desiredName.isBlank()) return@LaunchedEffect
        delay(600)
        if (desiredName != title.trim()) return@LaunchedEffect

        val base = originalFileName.removeSuffix(".md")
        if (desiredName == base) return@LaunchedEffect

        val oldUri = currentDocUri
        val renamedUri =
            withContext(Dispatchers.IO) {
                repository.renameDoc(oldUri, desiredName)
            } ?: return@LaunchedEffect
        currentDocUri = renamedUri
        EditorScreenCache.move(oldUri, renamedUri)
        onDocListMutated(DocListMutation.Renamed(oldUri = oldUri, newUri = renamedUri))
        originalFileName =
            withContext(Dispatchers.IO) {
                if (renamedUri.scheme.equals("file", ignoreCase = true)) {
                    renamedUri.path?.let { java.io.File(it).name }.orEmpty()
                } else {
                    DocumentFile.fromSingleUri(context, renamedUri)?.name.orEmpty()
                }
            }
    }

    ZhixuSwipeDualDrawer(
        enabled = !isPdfDoc,
        openGestureEnabled = !isImeVisible,
        gestureExcludeBottomHeight = drawerGestureExcludeBottomDp,
        resetKey = "${currentDocUri}|${vaultRootUri}",
        openRightToken = openOutlineToken,
        leftDrawerContent = { modifier, closeDrawer, isOpen ->
            val root = vaultRootUri
            if (root == null) {
                Box(modifier = modifier.width(0.dp))
            } else {
                    VaultDrawer(
                        vaultRootUri = root,
                        repository = repository,
                        onOpenDoc = { rawUri -> onOpenDoc(rawUri, null, null) },
                        onCloseDrawer = closeDrawer,
                        isActive = isOpen,
                        refreshToken = dirStructureMutationToken,
                        mutation = dirStructureMutation,
                        onDocListMutated = onDocListMutated,
                        headerMinHeight = 64.dp,
                        itemMinHeight = 40.dp,
                        itemTextStyle = MaterialTheme.typography.bodyMedium,
                        itemIconSize = 20.dp,
                    itemChevronSize = 18.dp,
                    modifier = modifier,
                )
            }
        },
        rightDrawerContent = { modifier, closeDrawer, isOpen ->
            ModalDrawerSheet(
                modifier = modifier.width(320.dp).fillMaxHeight(),
                drawerContainerColor = MaterialTheme.colorScheme.background,
                drawerTonalElevation = 0.dp,
            ) {
                Text(
                    text = stringResource(R.string.editor_outline_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    if (outline.isEmpty()) {
                        item { Text(stringResource(R.string.editor_outline_empty), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                    }
                    items(outline, key = { it.offset }) { item ->
                        Text(
                            text = item.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    closeDrawer()
                                    jumpToSelection(item.offset)
                                }
                                .padding(
                                    start = 12.dp + ((item.level - 1) * 12).dp,
                                    end = 12.dp,
                                    top = 8.dp,
                                    bottom = 8.dp,
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (wikiLinks.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.editor_links_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        items(wikiLinks, key = { it }) { name ->
                            Text(
                                text = name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        closeDrawer()
                                        val root = vaultRootUri ?: return@clickable
                                        scope.launch {
                                            val doc =
                                                withContext(Dispatchers.IO) {
                                                    repository.findDocByName(root, name)
                                                } ?: return@launch
                                            if (latestIsDirty) {
                                                autosaveJob?.cancel()
                                                val ok = persistNow(latestDocUri, latestContentText)
                                                if (!ok) {
                                                    snackbarHostState.showSnackbar(context.getString(R.string.editor_save_failed_generic))
                                                    return@launch
                                                }
                                            }
                                            latestOnOpenDoc(doc.uri.toString(), null, null)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
    ) { drawerActive ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    Column(
                        modifier =
                            Modifier
                                .background(MaterialTheme.colorScheme.surface)
                    ) {
                        ZhixuCenterAlignedTopAppBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleHorizontalPadding = if (isPdfDoc) 56.dp else 104.dp,
                            title = {
                                val docNameStyle = MaterialTheme.typography.titleSmall
                                val docNameColor = MaterialTheme.colorScheme.onSurface
                                val displayName =
                                    when {
                                        isPdfDoc ->
                                            title.ifBlank {
                                                originalFileName.removeSuffix(".pdf").ifBlank { stringResource(R.string.new_doc_default_title) }
                                            }
                                        else ->
                                            title.ifBlank {
                                                originalFileName.removeSuffix(".md").ifBlank { stringResource(R.string.new_doc_default_title) }
                                            }
                                    }

                                if (isPdfDoc) {
                                    Text(
                                        text = displayName,
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style =
                                            docNameStyle.copy(
                                                color = docNameColor,
                                                fontWeight = FontWeight.Normal,
                                                textAlign = TextAlign.Center,
                                            ),
                                    )
                                } else if (isRenamingDoc) {
                                    BasicTextField(
                                        value = titleFieldValue,
                                        onValueChange = { next ->
                                            titleFieldValue = next
                                            title = next.text
                                        },
                                        modifier =
                                            Modifier
                                                .focusRequester(docNameFocusRequester)
                                                .onFocusChanged {
                                                    if (it.isFocused) {
                                                        docNameHadFocus = true
                                                    } else if (docNameHadFocus) {
                                                        isRenamingDoc = false
                                                    }
                                                }
                                                .fillMaxWidth()
                                                .onSizeChanged { docNameFieldWidthPx = it.width }
                                                .horizontalScroll(docNameScrollState, enabled = false),
                                        singleLine = true,
                                        textStyle =
                                            docNameStyle.copy(
                                                color = docNameColor,
                                                fontWeight = FontWeight.Normal,
                                                textAlign = TextAlign.Center,
                                            ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions =
                                            KeyboardActions(
                                                onDone = {
                                                    isRenamingDoc = false
                                                    focusManager.clearFocus()
                                                },
                                            ),
                                        onTextLayout = { docNameLayoutResult = it },
                                        decorationBox = { inner ->
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                if (titleFieldValue.text.isBlank()) {
                                                    Text(
                                                        text = displayName,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        style =
                                                            docNameStyle.copy(
                                                                color = docNameColor.copy(alpha = 0.6f),
                                                                fontWeight = FontWeight.Normal,
                                                                textAlign = TextAlign.Center,
                                                            ),
                                                    )
                                                }
                                                inner()
                                            }
                                        },
                                    )
                                } else {
                                    Text(
                                        text = displayName,
                                        modifier = Modifier.fillMaxWidth().clickable { isRenamingDoc = true },
                                        style =
                                            docNameStyle.copy(
                                                color = docNameColor,
                                                fontWeight = FontWeight.Normal,
                                                textAlign = TextAlign.Center,
                                            ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            navigationIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ZhixuIconButton(onClick = { requestExit() }, enabled = !isExitSaveInProgress) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                            contentDescription = stringResource(R.string.action_back),
                                            modifier = Modifier.size(ZhixuTopBarIconSize),
                                        )
                                    }
                                }
                            },
                            actions = {
                                if (!isPdfDoc) {
                                    ZhixuIconButton(onClick = { isPreview = !isPreview }) {
                                        Icon(
                                            imageVector = if (isPreview) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = stringResource(R.string.action_preview),
                                            modifier = Modifier.size(ZhixuTopBarIconSize),
                                        )
                                    }
                                }
                                ZhixuIconButton(onClick = { showOverflowSheet = true }) {
                                    Icon(
                                        painter = painterResource(Ionicons.EllipsisHorizontal),
                                        contentDescription = "More",
                                        modifier = Modifier.size(ZhixuTopBarIconSize),
                                    )
                                }
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                },
            ) { padding ->
                Box(
                    modifier =
                        Modifier
                            .padding(padding)
                            .fillMaxSize(),
                ) {
                val contentOuterPadding = 0.dp
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = contentOuterPadding)) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        tonalElevation = 0.dp,
                        shape = RoundedCornerShape(0.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = if (isPdfDoc) 0.dp else 12.dp,
                                    end = if (isPdfDoc) 0.dp else 12.dp,
                                    top = if (isPdfDoc) 0.dp else 0.dp,
                                    bottom = if (isPdfDoc) 0.dp else 6.dp,
                                ),
                        ) {
                            if (isPdfDoc) {
                                Surface(
                                    tonalElevation = 0.dp,
                                    color = MaterialTheme.colorScheme.surface,
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            ZhixuIconButton(onClick = { showPdfThumbnails = true }, modifier = Modifier.size(32.dp)) {
                                                Icon(
                                                    painter = painterResource(Ionicons.GridOutline),
                                                    contentDescription = "Thumbnails",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(19.dp),
                                                )
                                            }
                                            ZhixuIconButton(onClick = { pdfController.zoomOut() }, modifier = Modifier.size(32.dp)) {
                                                Icon(
                                                    painter = painterResource(Ionicons.RemoveCircleOutline),
                                                    contentDescription = "Zoom out",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(19.dp),
                                                )
                                            }
                                            ZhixuIconButton(onClick = { pdfController.zoomIn() }, modifier = Modifier.size(32.dp)) {
                                                Icon(
                                                    painter = painterResource(Ionicons.AddCircleOutline),
                                                    contentDescription = "Zoom in",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(19.dp),
                                                )
                                            }

                                            Spacer(modifier = Modifier.weight(1f))

                                            Surface(
                                                tonalElevation = 0.dp,
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                            ) {
                                                BasicTextField(
                                                    value = pdfPageInput,
                                                    onValueChange = { s ->
                                                        pdfPageInput = s.filter { it.isDigit() }.take(6)
                                                    },
                                                    singleLine = true,
                                                    modifier =
                                                        Modifier
                                                            .widthIn(min = 42.dp, max = 64.dp)
                                                            .height(28.dp)
                                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    textStyle =
                                                        MaterialTheme.typography.labelMedium.copy(
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        ),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                                    keyboardActions = KeyboardActions(onDone = { applyPdfJump() }),
                                                )
                                            }
                                            Text(
                                                text = "/ ${pdfTotalPages.takeIf { it > 0 }?.toString() ?: "-"}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )

                                            ZhixuIconButton(onClick = { showPdfMenu = true }, modifier = Modifier.size(32.dp)) {
                                                Icon(
                                                    painter = painterResource(Ionicons.ChevronDown),
                                                    contentDescription = "Layout",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(19.dp),
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                    }
                                }
                            }
                            if (isPdfDoc) {
                                PdfPreview(
                                    modifier = Modifier.fillMaxSize(),
                                    docUri = currentDocUri,
                                    controller = pdfController,
                                    onPageState = { current, total ->
                                        pdfCurrentPage = current
                                        pdfTotalPages = total
                                        pdfPageInput = current.toString()
                                    },
                                )
                            } else if (isPreview) {
                                MarkdownPreview(
                                    modifier = Modifier.fillMaxSize(),
                                    markdown = content.text,
                                    fontScale = (editorFontSizeSpValue / 16f).coerceIn(0.75f, 1.75f),
                                    vaultRootUri = vaultRootUri,
                                    onOpenWikiLink = { name ->
                                        val root = vaultRootUri ?: return@MarkdownPreview
                                        scope.launch {
                                            if (latestIsDirty) {
                                                autosaveJob?.cancel()
                                                val ok = persistNow(latestDocUri, latestContentText)
                                                if (!ok) {
                                                    snackbarHostState.showSnackbar(context.getString(R.string.editor_save_failed_generic))
                                                    return@launch
                                                }
                                            }
                                            val doc = repository.findDocByName(root, name) ?: return@launch
                                            latestOnOpenDoc(doc.uri.toString(), null, null)
                                        }
                                    },
                                    onOpenVaultDocUri = { uri ->
                                        scope.launch {
                                            if (latestIsDirty) {
                                                autosaveJob?.cancel()
                                                val ok = persistNow(latestDocUri, latestContentText)
                                                if (!ok) {
                                                    snackbarHostState.showSnackbar(context.getString(R.string.editor_save_failed_generic))
                                                    return@launch
                                                }
                                            }
                                            latestOnOpenDoc(uri.toString(), null, null)
                                        }
                                    },
                                    loadWikiLinkPreview = { name ->
                                        val root = vaultRootUri ?: return@MarkdownPreview null
                                        val doc = repository.findDocByName(root, name) ?: return@MarkdownPreview null
                                        val text = repository.readText(doc.uri)
                                        buildPreviewSnippet(text)
                                    },
                                )
                            } else {
                                CodeMirrorMarkdownEditor(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(top = 6.dp),
                                    value = content,
                                    fontSizeSpValue = editorFontSizeSpValue,
                                    isSourceMode = isSourceMode,
                                    placeholder = if (isLoaded) "输入内容或使用 / 快速插入" else "",
                                    bottomInsetPx = anticipatedToolbarPx + with(density) { 24.dp.roundToPx() },
                                    vaultRootUri = vaultRootUri,
                                    interactionEnabled = !drawerActive,
                                    openDrawerGestureEnabled = !isImeVisible,
                                    onValueChange = { next ->
                                        val normalized =
                                            if (next.composition == null) {
                                                next.copy(text = next.text.toHalfWidthEditorSymbols())
                                            } else {
                                                next
                                            }
                                        if (normalized.text != content.text) pushHistoryIfNeeded(content)
                                        content = normalized
                                    },
                                )
                            }
                        }
                    }

                }

                if (showEditorToolbar) {
                    EditorBottomToolbar(
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty(),
                        onUndo = {
                            val prev = undoStack.removeLastOrNull() ?: return@EditorBottomToolbar
                            redoStack.addLast(content)
                            content = prev
                        },
                        onRedo = {
                            val next = redoStack.removeLastOrNull() ?: return@EditorBottomToolbar
                            undoStack.addLast(content)
                            content = next
                        },
                        onHeading1 = { insertHeading(1) },
                        onHeading2 = { insertHeading(2) },
                        onHeading3 = { insertHeading(3) },
                        onBold = { wrapSelection("**", "**") },
                        onTask = {
                            val lineIndex = content.text.take(content.selection.start).count { it == '\n' }
                            val toggled = TaskSyntax.toggleTaskAtLine(content.text, lineIndex)
                            if (toggled != content.text) {
                                pushHistoryIfNeeded(content)
                                content = content.copy(text = toggled)
                            } else {
                                insertTaskLine()
                            }
                        },
                        onBullets = { insertUnorderedList() },
                        onNumbers = { insertOrderedList() },
                        onItalic = { wrapSelection("*", "*") },
                        onStrike = { wrapSelection("~~", "~~") },
                        onQuote = { insertQuote() },
                        onDivider = { insertDivider() },
                        onLink = { openLinkDialog() },
                        onImage = { openImageDialog() },
                        onCode = { openCodeDialog() },
                        onTable = { openTableDialog() },
                        onMoreFindReplace = { showFindReplace = true },
                        onMoreHeading4 = { insertHeading(4) },
                        onMoreHeading5 = { insertHeading(5) },
                        onMoreHeading6 = { insertHeading(6) },
                        onMoreHighlight = { wrapSelection("==", "==") },
                        onMoreUnderline = { wrapSelection("<u>", "</u>") },
                        onMoreTaskToggle = {
                            val lineIndex = content.text.take(content.selection.start).count { it == '\n' }
                            val toggled = TaskSyntax.toggleTaskAtLine(content.text, lineIndex)
                            if (toggled != content.text) {
                                pushHistoryIfNeeded(content)
                                content = content.copy(text = toggled)
                            } else {
                                insertTaskLine()
                            }
                        },
                        onMoreMermaid = { insertMermaidTemplate() },
                        onMoreMathInline = { insertInlineMath() },
                        onMoreMathBlock = { insertBlockMath() },
                        onHeightPxChanged = { h ->
                            if (h > 0 && h != toolbarHeightPx) toolbarHeightPx = h
                        },
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                    )
                }

            }
        }

        }
    }

    if (showOverflowSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOverflowSheet = false },
            sheetState = overflowSheetState,
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
        ) {
            val notImplemented: () -> Unit = {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.common_not_implemented)) }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SheetQuickAction(
                        title = stringResource(R.string.editor_overflow_generate_long_image),
                        iconRes = Ionicons.ImageOutline,
                        onClick = {
                            showOverflowSheet = false
                            onGenerateLongImage(
                                LongImageRequest(
                                    markdown = content.text,
                                    vaultRootUri = vaultRootUri,
                                    fontScale = (editorFontSizeSpValue / 16f).coerceIn(0.75f, 1.75f),
                                    title = title.ifBlank { originalFileName.removeSuffix(".md") },
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SheetQuickAction(
                        title = stringResource(R.string.editor_overflow_share),
                        iconRes = Ionicons.ShareSocialOutline,
                        onClick = {
                            showOverflowSheet = false
                            val subject =
                                title.ifBlank {
                                    originalFileName.removeSuffix(".md").ifBlank { "Zhixu" }
                                }
                            val intent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, subject)
                                    putExtra(Intent.EXTRA_TEXT, content.text)
                                }
                            runCatching {
                                context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.onFailure {
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.editor_share_failed)) }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.size(14.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        if (!isPdfDoc) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.editor_overflow_source_mode),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                ZhixuSwitch(
                                    checked = isSourceMode,
                                    onCheckedChange = { next ->
                                        isSourceMode = next
                                        persistEditorSourceMode(next)
                                    },
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                        }
                        SheetActionRow(
                            title = stringResource(R.string.editor_overflow_font_size),
                            iconRes = Ionicons.TextOutline,
                            onClick = {
                                showOverflowSheet = false
                                showFontSizeSheet = true
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                        SheetActionRow(
                            title = stringResource(R.string.common_copy),
                            iconRes = Ionicons.CopyOutline,
                            onClick = {
                                showOverflowSheet = false
                                clipboard.setText(AnnotatedString(content.text))
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.common_copied)) }
                            },
                        )
                        if (pluginFabActions.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 260.dp),
                            ) {
                                items(pluginFabActions, key = { it.id }) { action ->
                                    SheetActionRow(
                                        title = action.label,
                                        iconRes = Ionicons.ExtensionPuzzleOutline,
                                        onClick = {
                                            showOverflowSheet = false
                                            handlePluginAction(action)
                                        },
                                    )
                                    if (action.id != pluginFabActions.last().id) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                        SheetActionRow(
                            title = stringResource(R.string.action_delete),
                            iconRes = Ionicons.TrashOutline,
                            iconTint = MaterialTheme.colorScheme.error,
                            onClick = {
                                showOverflowSheet = false
                                showDeleteConfirm = true
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.size(8.dp))
            }
        }
    }

    /* if (showHistorySheet) {
        var isLoading by remember(showHistorySheet, latestDocUri) { mutableStateOf(true) }
        var errorText by remember(showHistorySheet, latestDocUri) { mutableStateOf<String?>(null) }
        var versions by remember(showHistorySheet, latestDocUri) { mutableStateOf<List<VaultVersionEntryV2>>(emptyList()) }
        var selected by remember(showHistorySheet, latestDocUri) { mutableStateOf<VaultVersionEntryV2?>(null) }
        var selectedText by remember(showHistorySheet, latestDocUri) { mutableStateOf("") }
        var historyRelPath by remember(showHistorySheet, latestDocUri) { mutableStateOf<String?>(null) }
        var historyAuth by remember(showHistorySheet, latestDocUri) { mutableStateOf<app.zhixu.sync.SyncServerAuth?>(null) }
        var showOnlyChanges by remember(showHistorySheet, latestDocUri) { mutableStateOf(true) }
        val currentTextSnapshot by rememberUpdatedState(latestContentText)
        val offlineText = stringResource(R.string.error_server_unreachable)
        val notLoggedInText = stringResource(R.string.official_sync_not_logged_in)

        suspend fun loadSelected(v: VaultVersionEntryV2, relPath: String, baseUrl: String, token: String) {
            if (v.deleted) return
            val r = SyncServerClient.downloadVaultVersionV2(baseUrl, token, relPath, v.rev)
            if (!r.ok) {
                errorText =
                    when {
                        r.statusCode == 0 || r.errorMessage == "NETWORK_UNREACHABLE" -> offlineText
                        !r.errorMessage.isNullOrBlank() -> r.errorMessage
                        else -> "加载版本失败"
                    }
                return
            }
            selected = v
            selectedText = runCatching { r.value?.bytes?.decodeToString().orEmpty() }.getOrDefault("")
        }

        LaunchedEffect(showHistorySheet, latestDocUri, vaultRootUri) {
            isLoading = true
            errorText = null
            versions = emptyList()
            selected = null
            selectedText = ""

            val root = vaultRootUri
            if (root == null) {
                errorText = "未选择 Vault"
                isLoading = false
                return@LaunchedEffect
            }

            val relPath = repository.computeRelativePath(root, latestDocUri)
            if (relPath.isNullOrBlank()) {
                errorText = "无法计算文档路径（仅支持 Vault 内文件）"
                isLoading = false
                return@LaunchedEffect
            }
            historyRelPath = relPath

            val auth = resolveSyncServerAuth(context)
            if (auth == null) {
                errorText = notLoggedInText
                isLoading = false
                return@LaunchedEffect
            }
            historyAuth = auth

            val r = SyncServerClient.vaultVersionsV2(auth.baseUrl, auth.token, relPath, limit = 50)
            if (!r.ok) {
                errorText =
                    when {
                        r.statusCode == 0 || r.errorMessage == "NETWORK_UNREACHABLE" -> offlineText
                        !r.errorMessage.isNullOrBlank() -> r.errorMessage
                        else -> "获取版本列表失败"
                    }
                isLoading = false
                return@LaunchedEffect
            }

            versions = r.value?.versions.orEmpty()
            val first = versions.firstOrNull { !it.deleted }
            if (first != null) loadSelected(first, relPath, auth.baseUrl, auth.token)
            isLoading = false
        }

        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = historySheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null,
        ) {
            val diffLines =
                remember(selectedText, currentTextSnapshot) {
                    val maxChars = 200_000
                    val rawOld = currentTextSnapshot
                    val rawNew = selectedText

                    val oldPreview =
                        if (rawOld.length > maxChars) {
                            rawOld.take(maxChars) + "\n…(truncated)"
                        } else {
                            rawOld
                        }
                    val newPreview =
                        if (rawNew.length > maxChars) {
                            rawNew.take(maxChars) + "\n…(truncated)"
                        } else {
                            rawNew
                        }

                    runCatching { LineDiff.diff(oldText = oldPreview, newText = newPreview) }
                        .getOrElse { listOf(DiffLine(DiffOp.Equal, "Diff failed: ${it.javaClass.simpleName}")) }
                }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(R.string.editor_history_title), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { showHistorySheet = false }) { Text(stringResource(R.string.action_close)) }
                }

                if (errorText != null) {
                    Text(text = errorText.orEmpty(), color = Color(0xFFFF4D4F))
                }

                if (isLoading) {
                    Text(text = stringResource(R.string.common_loading), style = MaterialTheme.typography.bodyMedium)
                } else {
                    val relPath = historyRelPath.orEmpty()
                    val auth = historyAuth
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 220.dp),
                    ) {
                        items(versions, key = { it.rev }) { v ->
                            val label =
                                buildString {
                                    append("r")
                                    append(v.rev)
                                    if (v.deleted) append(" (deleted)")
                                }
                            ListItem(
                                headlineContent = { Text(label) },
                                supportingContent = {
                                    val t =
                                        if (v.updatedAt > 0L) {
                                            runCatching { java.time.Instant.ofEpochMilli(v.updatedAt).toString() }.getOrNull()
                                        } else {
                                            null
                                        }
                                    Text(listOfNotNull(t, "size=${v.size}").joinToString(" · "))
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !v.deleted && auth != null && relPath.isNotBlank()) {
                                            scope.launch {
                                                val a = auth ?: return@launch
                                                loadSelected(v, relPath, a.baseUrl, a.token)
                                            }
                                        },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        }
                    }
                }

                val sel = selected
                val canReplace = sel != null && !sel.deleted
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (canReplace) {
                                scope.launch {
                                    val ok = persistNow(latestDocUri, selectedText)
                                    if (ok) showHistorySheet = false else snackbarHostState.showSnackbar(context.getString(R.string.editor_history_replace_failed))
                                }
                            }
                        },
                        enabled = canReplace,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.editor_history_replace))
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                val rel = historyRelPath ?: return@launch
                                val auth = historyAuth ?: return@launch
                                val r = SyncServerClient.vaultVersionsV2(auth.baseUrl, auth.token, rel, limit = 50)
                                if (r.ok) {
                                    versions = r.value?.versions.orEmpty()
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.action_refresh)) }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(R.string.editor_history_diff_title), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showOnlyChanges = !showOnlyChanges }) {
                        Text(if (showOnlyChanges) stringResource(R.string.editor_history_show_all) else stringResource(R.string.editor_history_show_changes))
                    }
                }
                val codeStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 420.dp)
                            .background(Color(0xFFF7F7F7), RoundedCornerShape(8.dp))
                            .padding(vertical = 6.dp),
                ) {
                    items(diffLines.size) { idx ->
                        val line = diffLines[idx]
                        if (showOnlyChanges && line.op == DiffOp.Equal) return@items
                        val bg =
                            when (line.op) {
                                DiffOp.Insert -> Color(0xFF52C41A).copy(alpha = 0.18f)
                                DiffOp.Delete -> Color(0xFFFF4D4F).copy(alpha = 0.18f)
                                DiffOp.Equal -> Color.Transparent
                            }
                        val prefix =
                            when (line.op) {
                                DiffOp.Insert -> "+ "
                                DiffOp.Delete -> "- "
                                DiffOp.Equal -> "  "
                            }
                        val style =
                            when (line.op) {
                                DiffOp.Delete -> codeStyle.copy(color = Color(0xFFFF4D4F), textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                                DiffOp.Insert -> codeStyle.copy(color = Color(0xFF237804))
                                DiffOp.Equal -> codeStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f))
                            }
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(bg)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(text = prefix + line.text, style = style)
                        }
                    }
                }
            }
        }
    } */

    if (showFontSizeSheet) {
        var tempSize by remember(showFontSizeSheet) { mutableStateOf(editorFontSizeSpValue) }
        ModalBottomSheet(
            onDismissRequest = { showFontSizeSheet = false },
            sheetState = fontSizeSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "调整字号", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "${tempSize.toInt()}sp", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                }

                Text(
                    text = "预览：这是一段示例文本 AaBb123",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = tempSize.sp, lineHeight = (tempSize * 1.5f).sp),
                )

                Slider(
                    value = tempSize,
                    onValueChange = { tempSize = it.coerceIn(12f, 28f) },
                    valueRange = 12f..28f,
                    steps = 15,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = { tempSize = 16f },
                        modifier = Modifier.weight(1f),
                    ) { Text("重置") }
                    TextButton(
                        onClick = { showFontSizeSheet = false },
                        modifier = Modifier.weight(1f),
                    ) { Text("取消") }
                    TextButton(
                        onClick = {
                            editorFontSizeSpValue = tempSize.coerceIn(12f, 28f)
                            persistEditorFontSize(editorFontSizeSpValue)
                            showFontSizeSheet = false
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("完成") }
                }
            }
        }
    }

    if (showPdfMenu) {
        val pdfMenuState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPdfMenu = false },
            sheetState = pdfMenuState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "PDF", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal))

                @Composable
                fun option(
                    label: String,
                    mode: PdfLayoutMode,
                ) {
                    ListItem(
                        headlineContent = { Text(label) },
                        supportingContent = { if (pdfLayoutMode == mode) Text("当前", color = MaterialTheme.colorScheme.primary) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pdfLayoutMode = mode
                                    pdfController.setLayoutMode(mode)
                                    showPdfMenu = false
                                },
                    )
                }

                option("适应宽度", PdfLayoutMode.FitWidth)
                option("适应高度", PdfLayoutMode.FitHeight)
                option("单页", PdfLayoutMode.SinglePage)
                option("双页奇数", PdfLayoutMode.SpreadOdd)
                option("双页偶数", PdfLayoutMode.SpreadEven)

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    if (showPdfThumbnails) {
        val pdfThumbState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPdfThumbnails = false },
            sheetState = pdfThumbState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Text(text = "缩略图", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                val total = pdfTotalPages.coerceAtLeast(0)
                if (total <= 0) {
                    Text(
                        text = "加载中…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                } else {
                    val density = LocalDensity.current
                    val thumbMaxWidthPx = remember(density) { with(density) { 160.dp.roundToPx() } }
                    val pages = remember(total) { (1..total).toList() }
                    val thumbs = remember(showPdfThumbnails, total) { androidx.compose.runtime.mutableStateMapOf<Int, ImageBitmap>() }
                    val requested = remember(showPdfThumbnails, total) { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(84.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(pages, key = { it }) { p ->
                            val img = thumbs[p]
                            if (img == null && requested[p] != true) {
                                requested[p] = true
                                pdfController.requestThumbnail(page = p, maxWidthPx = thumbMaxWidthPx) { dataUrl ->
                                    if (dataUrl != null) {
                                        decodeDataUrlToImageBitmap(dataUrl)?.let { thumbs[p] = it }
                                    }
                                }
                            }
                            Surface(
                                tonalElevation = 0.dp,
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pdfController.goToPage(p)
                                            pdfCurrentPage = p
                                            pdfPageInput = p.toString()
                                            showPdfThumbnails = false
                                        },
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                ) {
                                    if (img != null) {
                                        Image(
                                            bitmap = img,
                                            contentDescription = "Page $p",
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        Text(
                                            text = p.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                    if (p == pdfCurrentPage) {
                                        Text(
                                            text = "当前",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { showDeleteConfirm = false },
            properties = ZhixuDialogDefaults.properties,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.dialog_delete_doc_title)) },
            text = { Text(originalFileName.ifBlank { title }) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { repository.deleteDoc(latestDocUri) }
                            if (ok) {
                                runCatching {
                                    VaultAutoSync.maybeDeleteDoc(
                                        context = context,
                                        repository = repository,
                                        vaultRootUri = vaultRootUri,
                                        docUri = latestDocUri,
                                    )
                                }
                                onDocListMutated(DocListMutation.Deleted(docUri = latestDocUri))
                                latestOnBack()
                                Toast.makeText(context, context.getString(R.string.editor_deleted), Toast.LENGTH_SHORT).show()
                            } else {
                                snackbarHostState.showSnackbar(context.getString(R.string.editor_delete_failed))
                            }
                        }
                    },
                ) { Text(stringResource(R.string.action_delete), color = Color(0xFFFF4D4F)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showFindReplace) {
        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { showFindReplace = false },
            properties = ZhixuDialogDefaults.properties,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.dialog_find_replace_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZhixuTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        label = { Text(stringResource(R.string.field_find)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ZhixuTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text(stringResource(R.string.field_replace)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { insertTaskLine() }) { Text(stringResource(R.string.action_insert_task)) }
                    TextButton(onClick = { insertField("@due(YYYY-MM-DD HH:mm)") }) { Text(stringResource(R.string.action_insert_due)) }
                    TextButton(onClick = { insertField("@tag(xxx)") }) { Text(stringResource(R.string.action_insert_tag)) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        applyReplaceAll()
                        showFindReplace = false
                    },
                ) { Text(stringResource(R.string.action_replace_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showFindReplace = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showLinkDialog) {
        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { showLinkDialog = false },
            properties = ZhixuDialogDefaults.properties,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Insert link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZhixuTextField(
                        value = linkText,
                        onValueChange = { linkText = it },
                        label = { Text("Text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ZhixuTextField(
                        value = linkUrl,
                        onValueChange = { linkUrl = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        insertLinkMarkdown(linkText, linkUrl)
                        showLinkDialog = false
                    },
                ) { Text("Insert") }
            },
            dismissButton = { TextButton(onClick = { showLinkDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showImageDialog) {
        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { showImageDialog = false },
            properties = ZhixuDialogDefaults.properties,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Insert image") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZhixuTextField(
                        value = imageAlt,
                        onValueChange = { imageAlt = it },
                        label = { Text("Alt") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ZhixuTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { imagePickerLauncher.launch(arrayOf("image/*")) }) { Text("Choose local image") }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (imageUrl.isNotBlank()) insertImageMarkdown(imageAlt, imageUrl)
                        showImageDialog = false
                    },
                ) { Text("Insert") }
            },
            dismissButton = { TextButton(onClick = { showImageDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showCodeDialog) {
        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { showCodeDialog = false },
            properties = ZhixuDialogDefaults.properties,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Insert code block") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZhixuTextField(
                        value = codeLanguage,
                        onValueChange = { codeLanguage = it },
                        label = { Text(stringResource(R.string.editor_code_block_language)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        insertCodeBlock(codeLanguage.takeIf { it.isNotBlank() })
                        showCodeDialog = false
                    },
                ) { Text("Insert") }
            },
            dismissButton = { TextButton(onClick = { showCodeDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showTableDialog) {
        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { showTableDialog = false },
            properties = ZhixuDialogDefaults.properties,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Insert table") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZhixuTextField(
                        value = tableRowsText,
                        onValueChange = { tableRowsText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Rows") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ZhixuTextField(
                        value = tableColsText,
                        onValueChange = { tableColsText = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("Cols") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val r = tableRowsText.toIntOrNull() ?: 2
                        val c = tableColsText.toIntOrNull() ?: 2
                        insertTable(r, c)
                        showTableDialog = false
                    },
                ) { Text("Insert") }
            },
            dismissButton = { TextButton(onClick = { showTableDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private object EditorScreenCache {
    private const val MaxEntries = 8
    private val lock = Any()
    private val cache =
        object : LinkedHashMap<String, Entry>(MaxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MaxEntries
        }

    data class Entry(
        val text: String,
        val title: String,
        val originalFileName: String,
        val outline: List<OutlineItem>,
        val wikiLinks: List<String>,
    )

    fun get(uri: Uri): Entry? = synchronized(lock) { cache[uri.toString()] }

    fun put(
        uri: Uri,
        entry: Entry,
    ) {
        synchronized(lock) { cache[uri.toString()] = entry }
    }

    fun move(
        from: Uri,
        to: Uri,
    ) {
        synchronized(lock) {
            val removed = cache.remove(from.toString()) ?: return
            cache[to.toString()] = removed
        }
    }
}

private data class OutlineItem(
    val level: Int,
    val title: String,
    val offset: Int,
)

private data class JumpTarget(
    val start: Int,
    val end: Int,
)

private fun String.toHalfWidthEditorSymbols(): String {
    var needsNormalize = false
    for (ch in this) {
        if (
            ch == '\u3000' ||
                ch == '＃' ||
                ch == '＊' ||
                ch == '＿' ||
                ch == '｀' ||
                ch == '～' ||
                ch == '＞' ||
                ch == '｜' ||
                ch == '－' ||
                ch == '＋' ||
                ch == '＝'
        ) {
            needsNormalize = true
            break
        }
    }
    if (!needsNormalize) return this

    val out = StringBuilder(length)
    for (ch in this) {
        out.append(
            when (ch) {
                '\u3000' -> ' '
                '＃' -> '#'
                '＊' -> '*'
                '＿' -> '_'
                '｀' -> '`'
                '～' -> '~'
                '＞' -> '>'
                '｜' -> '|'
                '－' -> '-'
                '＋' -> '+'
                '＝' -> '='
                else -> ch
            },
        )
    }
    return out.toString()
}

private fun parseOutline(markdown: String): List<OutlineItem> {
    val out = ArrayList<OutlineItem>()
    var offset = 0
    val lines = markdown.split('\n')
    val regex = Regex("""^(#{1,6})\s+(.+)$""")
    for (line in lines) {
        val m = regex.matchEntire(line)
        if (m != null) {
            val level = m.groupValues[1].length
            val title = m.groupValues[2].trim()
            out += OutlineItem(level = level, title = title, offset = offset)
        }
        offset += line.length + 1
    }
    return out
}

private fun parseWikiLinks(markdown: String): List<String> {
    val regex = Regex("""\[\[([^\]]+)\]\]""")
    return regex.findAll(markdown)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
}

private fun buildPreviewSnippet(
    markdown: String,
    maxChars: Int = 180,
): String {
    val noFrontMatter =
        if (markdown.startsWith("---\n")) {
            val end = markdown.indexOf("\n---", startIndex = 4)
            if (end >= 0) markdown.substring((end + 4).coerceAtMost(markdown.length)) else markdown
        } else {
            markdown
        }

    val text =
        noFrontMatter
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("#") }
            .filterNot { it.startsWith(">") }
            .filterNot { it.startsWith("```") }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

    return if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "…"
}

private fun lineStartOffset(text: String, lineIndex: Int): Int {
    if (lineIndex <= 0) return 0
    var currentLine = 0
    var idx = 0
    while (idx < text.length && currentLine < lineIndex) {
        val next = text.indexOf('\n', startIndex = idx)
        if (next < 0) return text.length
        idx = next + 1
        currentLine++
    }
    return idx.coerceIn(0, text.length)
}

private fun lineEndOffset(text: String, startOffset: Int): Int {
    val safeStart = startOffset.coerceIn(0, text.length)
    val next = text.indexOf('\n', startIndex = safeStart)
    return if (next < 0) text.length else next.coerceIn(0, text.length)
}

private fun findFirstMatchOffset(text: String, query: String): Pair<Int, Int>? {
    val q = query.trim()
    if (q.isBlank()) return null
    val tokens = q.split(Regex("""\s+""")).filter { it.isNotBlank() }.distinct()
    if (tokens.isEmpty()) return null

    var bestStart: Int? = null
    var bestLen = 0
    for (token in tokens) {
        val idx = text.indexOf(token, ignoreCase = true)
        if (idx < 0) continue
        if (bestStart == null || idx < bestStart) {
            bestStart = idx
            bestLen = token.length
        }
    }
    val start = bestStart ?: return null
    val end = (start + bestLen).coerceAtMost(text.length)
    return start to end
}

@Composable
private fun EditorBottomToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onHeading1: () -> Unit,
    onHeading2: () -> Unit,
    onHeading3: () -> Unit,
    onBold: () -> Unit,
    onTask: () -> Unit,
    onBullets: () -> Unit,
    onNumbers: () -> Unit,
    onItalic: () -> Unit,
    onStrike: () -> Unit,
    onQuote: () -> Unit,
    onDivider: () -> Unit,
    onLink: () -> Unit,
    onImage: () -> Unit,
    onCode: () -> Unit,
    onTable: () -> Unit,
    onMoreFindReplace: () -> Unit,
    onMoreHeading4: () -> Unit,
    onMoreHeading5: () -> Unit,
    onMoreHeading6: () -> Unit,
    onMoreHighlight: () -> Unit,
    onMoreUnderline: () -> Unit,
    onMoreTaskToggle: () -> Unit,
    onMoreMermaid: () -> Unit,
    onMoreMathInline: () -> Unit,
    onMoreMathBlock: () -> Unit,
    onHeightPxChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showMore by remember { mutableStateOf(false) }
    val toolbarScrollState = rememberScrollState()
    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(0.dp),
        modifier =
            Modifier
                .then(modifier)
                .onSizeChanged { onHeightPxChanged(it.height) }
                .fillMaxWidth(),
    ) {
        val toolbarIconSize = 18.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(toolbarScrollState)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorToolIcon(enabled = canUndo, onClick = onUndo) { Icon(painter = painterResource(Heroicons.ArrowUturnLeft), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolIcon(enabled = canRedo, onClick = onRedo) { Icon(painter = painterResource(Heroicons.ArrowUturnRight), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolDivider()
            EditorToolIcon(onClick = onHeading1) { Icon(painter = painterResource(Heroicons.H1), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolIcon(onClick = onHeading2) { Icon(painter = painterResource(Heroicons.H2), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolIcon(onClick = onHeading3) { Icon(painter = painterResource(Heroicons.H3), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolIcon(onClick = onBold) { Icon(painter = painterResource(Heroicons.Bold), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolIcon(onClick = onItalic) { Icon(painter = painterResource(Heroicons.Italic), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolIcon(onClick = onStrike) { Icon(painter = painterResource(Heroicons.Strikethrough), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolDivider()
            EditorToolIcon(onClick = onTask) { Icon(painter = painterResource(Ionicons.CheckboxOutline), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolIcon(onClick = onBullets) { Icon(painter = painterResource(Heroicons.ListBullets), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolIcon(onClick = onNumbers) { Icon(painter = painterResource(Heroicons.ListNumbers), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolDivider()
            EditorToolIcon(onClick = onQuote) { Icon(painter = painterResource(Heroicons.Quote), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolIcon(onClick = onDivider) { Icon(painter = painterResource(Heroicons.Divider), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolDivider()
            EditorToolIcon(onClick = onLink) { Icon(painter = painterResource(Heroicons.Link), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolIcon(onClick = onImage) { Icon(painter = painterResource(Heroicons.Image), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolIcon(onClick = onCode) { Icon(painter = painterResource(Heroicons.Code), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolIcon(onClick = onTable) { Icon(painter = painterResource(Heroicons.Table), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
            EditorToolDivider()

            Box {
                EditorToolIcon(onClick = { showMore = true }) { Icon(painter = painterResource(Ionicons.EllipsisHorizontal), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) }
                DropdownMenu(
                    expanded = showMore,
                    onDismissRequest = { showMore = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_find_replace)) },
                        onClick = { showMore = false; onMoreFindReplace() },
                        leadingIcon = { Icon(Icons.Outlined.FindReplace, null, modifier = Modifier.size(toolbarIconSize)) },
                    )
                    DropdownMenuItem(text = { Text("Heading 4") }, onClick = { showMore = false; onMoreHeading4() })
                    DropdownMenuItem(text = { Text("Heading 5") }, onClick = { showMore = false; onMoreHeading5() })
                    DropdownMenuItem(text = { Text("Heading 6") }, onClick = { showMore = false; onMoreHeading6() })
                    DropdownMenuItem(text = { Text("Highlight") }, onClick = { showMore = false; onMoreHighlight() })
                    DropdownMenuItem(
                        text = { Text("Underline") },
                        onClick = { showMore = false; onMoreUnderline() },
                        leadingIcon = { Icon(painter = painterResource(Heroicons.Underline), contentDescription = null, modifier = Modifier.size(toolbarIconSize)) },
                    )
                    DropdownMenuItem(text = { Text("Toggle task done") }, onClick = { showMore = false; onMoreTaskToggle() })
                    DropdownMenuItem(text = { Text("Mermaid") }, onClick = { showMore = false; onMoreMermaid() })
                    DropdownMenuItem(text = { Text("Inline math") }, onClick = { showMore = false; onMoreMathInline() })
                    DropdownMenuItem(text = { Text("Block math") }, onClick = { showMore = false; onMoreMathBlock() })
                }
            }
        }
    }
}

@Composable
private fun EditorToolIcon(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    ZhixuIconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .width(30.dp)
            .height(30.dp),
    ) {
        content()
    }
}

@Composable
private fun EditorToolDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(16.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
    )
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
