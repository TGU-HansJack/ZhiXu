package com.zhixu.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Save
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
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs
import com.zhixu.android.R
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.plugins.InstalledPlugin
import com.zhixu.android.plugins.FrontMatterParser
import com.zhixu.android.plugins.PluginRepository
import com.zhixu.android.plugins.typecho.TypechoPublishPayload
import com.zhixu.android.plugins.typecho.TypechoXmlRpcClient
import com.zhixu.android.plugins.typecho.TypechoXmlRpcConfig
import com.zhixu.android.ui.components.DraggableRadialFab
import com.zhixu.android.ui.components.MarkdownPreview
import com.zhixu.android.ui.components.RadialFabAction
import com.zhixu.core.tasks.TaskSyntax
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import org.json.JSONObject
import androidx.documentfile.provider.DocumentFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    docUri: Uri,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    onBack: () -> Unit,
    onOpenDoc: (String, String?, Int?) -> Unit,
    initialQuery: String?,
    initialLineIndex: Int?,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pluginRepo = remember(context) { PluginRepository(context) }
    var title by remember { mutableStateOf("") }
    var originalFileName by remember { mutableStateOf("") }
    var content by remember { mutableStateOf(TextFieldValue("")) }
    var isPreview by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val clipboard = LocalClipboardManager.current

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
    var isLoaded by remember { mutableStateOf(false) }

    val undoStack = remember { ArrayDeque<TextFieldValue>() }
    val redoStack = remember { ArrayDeque<TextFieldValue>() }
    var lastHistoryStampMs by remember { mutableStateOf(0L) }

    var showFindReplace by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    var pendingInitialJump by remember { mutableStateOf<JumpTarget?>(null) }
    var editorViewportHeightPx by remember { mutableStateOf(0) }
    var stableEditorViewportHeightPx by remember { mutableStateOf(0) }
    var currentDocUri by remember(docUri) { mutableStateOf(docUri) }
    var outline by remember { mutableStateOf<List<OutlineItem>>(emptyList()) }
    var wikiLinks by remember { mutableStateOf<List<String>>(emptyList()) }
    var pluginFabActions by remember { mutableStateOf<List<RadialFabAction>>(emptyList()) }

    fun iconForPluginAction(name: String?) =
        when (name?.lowercase()?.trim()) {
            "cloud_upload", "upload", "publish" -> Icons.Outlined.CloudUpload
            else -> Icons.Outlined.Extension
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
        val installed = runCatching { pluginRepo.listInstalled(root) }.getOrElse { emptyList() }
        pluginFabActions = buildPluginFabActions(installed)
    }

    fun typechoConfigFromJson(json: JSONObject?): TypechoXmlRpcConfig? {
        if (json == null) return null
        val endpoint = json.optString("endpointUrl").trim()
        val username = json.optString("username").trim()
        val password = json.optString("password")
        val blogId = json.optString("blogId").trim().ifBlank { "1" }
        val defaultCategories =
            json.optJSONArray("defaultCategories")?.let { arr ->
                (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).trim().takeIf { it.isNotBlank() } }
            } ?: json.optString("categories").split(',').map { it.trim() }.filter { it.isNotBlank() }
        val defaultTags =
            json.optJSONArray("defaultTags")?.let { arr ->
                (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).trim().takeIf { it.isNotBlank() } }
            } ?: json.optString("tags").split(',').map { it.trim() }.filter { it.isNotBlank() }

        if (endpoint.isBlank() || username.isBlank() || password.isBlank()) return null
        return TypechoXmlRpcConfig(
            endpointUrl = endpoint,
            username = username,
            password = password,
            blogId = blogId,
            defaultCategories = defaultCategories,
            defaultTags = defaultTags,
        )
    }

    LaunchedEffect(Unit) {
        snapshotFlow { editorViewportHeightPx }
            .distinctUntilChanged()
            .collectLatest {
                delay(80)
                stableEditorViewportHeightPx = it
            }
    }

    LaunchedEffect(docUri) {
        if (docUri.toString().isBlank()) {
            snackbarHostState.showSnackbar(context.getString(R.string.editor_load_failed_generic))
            onBack()
            return@LaunchedEffect
        }

        val fileName = DocumentFile.fromSingleUri(context, docUri)?.name.orEmpty()
        originalFileName = fileName
        title = fileName.removeSuffix(".md").ifBlank { context.getString(R.string.new_doc_default_title) }

        val loaded = runCatching { repository.readText(docUri) }
            .getOrElse { e ->
                val msg =
                    when (e) {
                        is SecurityException -> context.getString(R.string.editor_load_failed_permission)
                        is FileNotFoundException -> context.getString(R.string.editor_load_failed_not_found)
                        else -> context.getString(R.string.editor_load_failed_generic)
                    }
                snackbarHostState.showSnackbar(msg)
                ""
            }
        content = TextFieldValue(loaded)
        undoStack.clear()
        redoStack.clear()
        isLoaded = true

        val jump =
            when {
                initialLineIndex != null && initialLineIndex >= 0 -> {
                    val start = lineStartOffset(loaded, initialLineIndex)
                    val end = lineEndOffset(loaded, start)
                    JumpTarget(start = start, end = end)
                }

                !initialQuery.isNullOrBlank() -> {
                    val match = findFirstMatchOffset(loaded, initialQuery)
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
        delay(250)
        val parsed =
            withContext(Dispatchers.Default) {
                parseOutline(snapshot) to parseWikiLinks(snapshot)
            }
        outline = parsed.first
        wikiLinks = parsed.second
    }

    fun jumpToSelection(startOffset: Int, endOffset: Int = startOffset) {
        val text = content.text
        val safeStart = startOffset.coerceIn(0, text.length)
        val safeEnd = endOffset.coerceIn(0, text.length).coerceAtLeast(safeStart)
        content = content.copy(selection = TextRange(safeStart, safeEnd))
        val layout = textLayoutResult ?: return
        runCatching {
            val line = layout.getLineForOffset(safeStart)
            val y = layout.getLineTop(line).toInt().coerceAtLeast(0)
            scope.launch { scrollState.animateScrollTo(y) }
        }
    }

    LaunchedEffect(isLoaded, isPreview, textLayoutResult, pendingInitialJump) {
        val jump = pendingInitialJump ?: return@LaunchedEffect
        if (!isLoaded || isPreview) return@LaunchedEffect
        if (textLayoutResult == null) return@LaunchedEffect
        pendingInitialJump = null
        jumpToSelection(jump.start, jump.end)
    }

    LaunchedEffect(isLoaded, isPreview, textLayoutResult, content.selection, stableEditorViewportHeightPx) {
        if (!isLoaded || isPreview) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        val viewport = stableEditorViewportHeightPx
        if (viewport <= 0) return@LaunchedEffect

        val offset = content.selection.end.coerceIn(0, content.text.length)
        val line = runCatching { layout.getLineForOffset(offset) }.getOrNull() ?: return@LaunchedEffect
        val top = layout.getLineTop(line)
        val bottom = layout.getLineBottom(line)

        val margin = 48
        val visibleTop = scrollState.value.toFloat()
        val visibleBottom = (scrollState.value + viewport).toFloat()

        val targetY =
            when {
                top < visibleTop + margin -> (top - margin).toInt()
                bottom > visibleBottom - margin -> (bottom - viewport + margin).toInt()
                else -> null
            }?.coerceAtLeast(0)

        if (targetY != null && abs(targetY - scrollState.value) > 4) {
            scrollState.scrollTo(targetY)
        }
    }

    LaunchedEffect(content.text, isLoaded, isPreview, currentDocUri) {
        if (!isLoaded || isPreview) return@LaunchedEffect
        autosaveJob?.cancel()
        autosaveJob =
            scope.launch {
                delay(1200)
                runCatching { repository.writeText(currentDocUri, content.text) }
                    .onSuccess { repository.indexDocUri(currentDocUri) }
            }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
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
                                    scope.launch { drawerState.close() }
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
                                        scope.launch { drawerState.close() }
                                        val root = vaultRootUri ?: return@clickable
                                        scope.launch {
                                            val doc = repository.findDocByName(root, name) ?: return@launch
                                            onOpenDoc(doc.uri.toString(), null, null)
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
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        windowInsets = TopAppBarDefaults.windowInsets,
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        },
                        actions = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(imageVector = Icons.Outlined.Menu, contentDescription = stringResource(R.string.action_open_drawer))
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val desiredName = title.trim()
                                        if (desiredName.isNotBlank()) {
                                            val base = originalFileName.removeSuffix(".md")
                                            if (desiredName != base) {
                                                val renamedUri = repository.renameDoc(currentDocUri, desiredName)
                                                if (renamedUri != null) {
                                                    currentDocUri = renamedUri
                                                    originalFileName = DocumentFile.fromSingleUri(context, renamedUri)?.name.orEmpty()
                                                } else {
                                                    snackbarHostState.showSnackbar(context.getString(R.string.editor_rename_failed_generic))
                                                }
                                            }
                                        }

                                        val result =
                                            runCatching {
                                                val normalized = TaskSyntax.normalizeMarkdown(content.text)
                                                val toSave = normalized.markdown
                                                repository.writeText(currentDocUri, toSave)
                                                repository.indexDocUri(currentDocUri)
                                                content = TextFieldValue(toSave)
                                                normalized.insertedIds
                                            }

                                        result.fold(
                                            onSuccess = { insertedIds ->
                                                val msg =
                                                    if (insertedIds > 0) {
                                                        context.getString(R.string.snackbar_saved_with_ids, insertedIds)
                                                    } else {
                                                        context.getString(R.string.snackbar_saved)
                                                    }
                                                snackbarHostState.showSnackbar(msg)
                                            },
                                            onFailure = { e ->
                                                val msg =
                                                    when (e) {
                                                        is SecurityException -> context.getString(R.string.editor_save_failed_permission)
                                                        else -> context.getString(R.string.editor_save_failed_generic)
                                                    }
                                                snackbarHostState.showSnackbar(msg)
                                            },
                                        )
                                    }
                                },
                            ) {
                                Icon(imageVector = Icons.Outlined.Save, contentDescription = stringResource(R.string.action_save))
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
            bottomBar = {
                EditorBottomToolbar(
                    isPreview = isPreview,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                    onTogglePreview = { isPreview = !isPreview },
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
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        tonalElevation = 0.dp,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            BasicTextField(
                                value = title,
                                onValueChange = { title = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle =
                                    MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { inner ->
                                    if (title.isBlank()) {
                                        Text(
                                            text = originalFileName.removeSuffix(".md").ifBlank { stringResource(R.string.new_doc_default_title) },
                                            style = MaterialTheme.typography.headlineSmall.copy(color = Color.Gray, fontSize = 22.sp),
                                        )
                                    }
                                    inner()
                                },
                            )
                            HorizontalDivider(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp), thickness = 0.5.dp)

                            if (isPreview) {
                                Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                                    MarkdownPreview(
                                        markdown = content.text,
                                        onOpenWikiLink = { name ->
                                            val root = vaultRootUri ?: return@MarkdownPreview
                                            scope.launch {
                                                val doc = repository.findDocByName(root, name) ?: return@launch
                                                onOpenDoc(doc.uri.toString(), null, null)
                                            }
                                        },
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .onSizeChanged { editorViewportHeightPx = it.height },
                                ) {
                                    BasicTextField(
                                        value = content,
                                        onValueChange = { next ->
                                            val normalized =
                                                if (next.composition == null) {
                                                    next.copy(text = next.text.toHalfWidthAscii())
                                                } else {
                                                    next
                                                }
                                            if (normalized.text != content.text) pushHistoryIfNeeded(content)
                                            content = normalized
                                        },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .onPreviewKeyEvent { event ->
                                                if (event.type != KeyEventType.KeyDown || event.key != Key.Enter) return@onPreviewKeyEvent false
                                                if (isPreview) return@onPreviewKeyEvent false
                                                if (content.selection.start != content.selection.end) return@onPreviewKeyEvent false

                                                val t = content.text
                                                val cursor = content.selection.start.coerceIn(0, t.length)
                                                val lineStart = t.lastIndexOf('\n', startIndex = (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
                                                val lineEnd = t.indexOf('\n', startIndex = cursor).let { if (it < 0) t.length else it }
                                                val line = t.substring(lineStart, lineEnd)

                                                val taskMatch = Regex("""^(\s*)-\s+\[( |x|X)\]\s+""").find(line)
                                                val unorderedMatch = Regex("""^(\s*)([-*+])\s+""").find(line)
                                                val orderedMatch = Regex("""^(\s*)(\d+)\.\s+""").find(line)
                                                val quoteMatch = Regex("""^(\s*(?:>\s*)+)""").find(line)

                                                val currentPrefix: String?
                                                val nextPrefix: String?

                                                when {
                                                    taskMatch != null -> {
                                                        val indent = taskMatch.groupValues[1]
                                                        currentPrefix = taskMatch.value
                                                        nextPrefix = "$indent- [ ] "
                                                    }

                                                    orderedMatch != null -> {
                                                        val indent = orderedMatch.groupValues[1]
                                                        val n = orderedMatch.groupValues[2].toIntOrNull() ?: 1
                                                        currentPrefix = "$indent$n. "
                                                        nextPrefix = "$indent${n + 1}. "
                                                    }

                                                    unorderedMatch != null -> {
                                                        val indent = unorderedMatch.groupValues[1]
                                                        val bullet = unorderedMatch.groupValues[2]
                                                        currentPrefix = "$indent$bullet "
                                                        nextPrefix = currentPrefix
                                                    }

                                                    quoteMatch != null -> {
                                                        currentPrefix = quoteMatch.value
                                                        nextPrefix = currentPrefix
                                                    }

                                                    else -> return@onPreviewKeyEvent false
                                                }

                                                val remainder = line.drop(currentPrefix.length).trim()
                                                if (cursor == lineEnd && remainder.isBlank()) {
                                                    pushHistoryIfNeeded(content)
                                                    val removed = t.removeRange(lineStart, (lineStart + currentPrefix.length).coerceAtMost(t.length))
                                                    val newCursor = (cursor - currentPrefix.length).coerceIn(0, removed.length)
                                                    content =
                                                        content.copy(
                                                            text = removed.substring(0, newCursor) + "\n" + removed.substring(newCursor),
                                                            selection = TextRange(newCursor + 1),
                                                        )
                                                    return@onPreviewKeyEvent true
                                                }

                                                pushHistoryIfNeeded(content)
                                                content =
                                                    content.copy(
                                                        text = t.substring(0, cursor) + "\n" + nextPrefix + t.substring(cursor),
                                                        selection = TextRange(cursor + 1 + nextPrefix.length),
                                                    )
                                                true
                                            }
                                            .verticalScroll(scrollState),
                                        textStyle =
                                            TextStyle.Default.copy(
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        onTextLayout = { textLayoutResult = it },
                                        decorationBox = { inner ->
                                            if (content.text.isBlank()) {
                                                Text(
                                                    text = "输入内容或使用 / 快速插入",
                                                    color = Color.Gray,
                                                    style = TextStyle.Default.copy(fontSize = 16.sp),
                                                )
                                            }
                                            inner()
                                        },
                                    )
                                }
                            }
                        }
                    }

                }

                    DraggableRadialFab(
                        modifier = Modifier.fillMaxSize(),
                        primaryLabel = "Z",
                        onClickPrimary = { isPreview = !isPreview },
                        actions = pluginFabActions,
                        persistenceKey = "editor_fab",
                        edgePadding = 0.dp,
                        onClickAction = { action ->
                            when {
                                action.id == "plugins:none" -> {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("请到 设置 -> 创意工坊 启用/安装插件")
                                    }
                                }

                                action.id.startsWith("plugin:typecho-xmlrpc-publisher/") -> {
                                    val root = vaultRootUri
                                    if (root == null) {
                                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_vault_not_selected)) }
                                    } else {
                                        scope.launch {
                                            val cfgJson = pluginRepo.readPluginConfig(root, "typecho-xmlrpc-publisher")
                                            val cfg = typechoConfigFromJson(cfgJson)
                                            if (cfg == null) {
                                                snackbarHostState.showSnackbar("请先到 设置 -> 创意工坊 -> Typecho 插件 设置")
                                                return@launch
                                            }

                                            val fm = FrontMatterParser.parse(content.text)
                                            val fmTitle = fm.string("title")
                                            val fmSlug = fm.string("slug") ?: fm.string("permalink")
                                            val fmTags = fm.stringList("tags")
                                            val fmCategories = fm.stringList("categories")
                                            val fmPostId = fm.string("typechoPostId") ?: fm.string("postId")
                                            val draft = fm.bool("draft") == true

                                            val payload =
                                                TypechoPublishPayload(
                                                    title = (fmTitle ?: title).ifBlank { originalFileName.removeSuffix(".md") },
                                                    markdown = fm.body,
                                                    categories = fmCategories,
                                                    tags = fmTags,
                                                    slug = fmSlug,
                                                    publish = !draft,
                                                    postId = fmPostId,
                                                )

                                            snackbarHostState.showSnackbar("发布中…")
                                            val result =
                                                withContext(Dispatchers.IO) {
                                                    TypechoXmlRpcClient().publish(cfg, payload)
                                                }
                                            if (result.ok && !result.postId.isNullOrBlank()) {
                                                val updated = FrontMatterParser.upsert(content.text, mapOf("typechoPostId" to result.postId))
                                                repository.writeText(currentDocUri, updated)
                                                content = content.copy(text = updated)
                                            }
                                            snackbarHostState.showSnackbar(
                                                if (result.ok) {
                                                    if (result.postId != null) "${result.message} (postId=${result.postId})" else result.message
                                                } else {
                                                    result.message
                                                },
                                            )
                                        }
                                    }
                                }

                                action.id.startsWith("plugin:") -> {
                                    scope.launch { snackbarHostState.showSnackbar(action.label) }
                                }

                                else -> Unit
                            }
                        },
                    )
            }
        }
    }

    // Plugin publish actions are handled via FAB; plugin settings live in Workshop.

    if (showFindReplace) {
        AlertDialog(
            onDismissRequest = { showFindReplace = false },
            title = { Text(stringResource(R.string.dialog_find_replace_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        label = { Text(stringResource(R.string.field_find)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text(stringResource(R.string.field_replace)) },
                        singleLine = true,
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
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Insert link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = linkText,
                        onValueChange = { linkText = it },
                        label = { Text("Text") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = { linkUrl = it },
                        label = { Text("URL") },
                        singleLine = true,
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
            onDismissRequest = { showImageDialog = false },
            title = { Text("Insert image") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = imageAlt,
                        onValueChange = { imageAlt = it },
                        label = { Text("Alt") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("URL") },
                        singleLine = true,
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
            onDismissRequest = { showCodeDialog = false },
            title = { Text("Insert code block") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = codeLanguage,
                        onValueChange = { codeLanguage = it },
                        label = { Text("Language") },
                        singleLine = true,
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
            onDismissRequest = { showTableDialog = false },
            title = { Text("Insert table") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = tableRowsText,
                        onValueChange = { tableRowsText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Rows") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = tableColsText,
                        onValueChange = { tableColsText = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("Cols") },
                        singleLine = true,
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

private data class OutlineItem(
    val level: Int,
    val title: String,
    val offset: Int,
)

private data class JumpTarget(
    val start: Int,
    val end: Int,
)

private fun String.toHalfWidthAscii(): String {
    var hasFullWidth = false
    for (ch in this) {
        if (ch == '\u3000' || ch in '\uFF01'..'\uFF5E') {
            hasFullWidth = true
            break
        }
    }
    if (!hasFullWidth) return this

    val out = StringBuilder(length)
    for (ch in this) {
        when {
            ch == '\u3000' -> out.append(' ')
            ch in '\uFF01'..'\uFF5E' -> out.append((ch.code - 0xFEE0).toChar())
            else -> out.append(ch)
        }
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
    isPreview: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onTogglePreview: () -> Unit,
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
    modifier: Modifier = Modifier,
) {
    var showMore by remember { mutableStateOf(false) }
    val toolbarScrollState = rememberScrollState()
    Surface(
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .wrapContentHeight()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(toolbarScrollState)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorToolIcon(onClick = onTogglePreview) {
                Icon(
                    imageVector = if (isPreview) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = null,
                )
            }
            EditorToolDivider()
            EditorToolIcon(enabled = canUndo, onClick = onUndo) { Icon(Icons.Outlined.Undo, null) }
            EditorToolIcon(enabled = canRedo, onClick = onRedo) { Icon(Icons.Outlined.Redo, null) }
            EditorToolDivider()
            EditorToolIcon(onClick = onHeading1) { Text("H1", style = MaterialTheme.typography.labelLarge) }
            EditorToolIcon(onClick = onHeading2) { Text("H2", style = MaterialTheme.typography.labelLarge) }
            EditorToolIcon(onClick = onHeading3) { Text("H3", style = MaterialTheme.typography.labelLarge) }
            EditorToolIcon(onClick = onBold) { Icon(Icons.Outlined.FormatBold, null) }
            EditorToolIcon(onClick = onItalic) { Icon(Icons.Outlined.FormatItalic, null) }
            EditorToolIcon(onClick = onStrike) { Icon(Icons.Outlined.StrikethroughS, null) }
            EditorToolDivider()
            EditorToolIcon(onClick = onTask) { Icon(Icons.Outlined.CheckBox, null) }
            EditorToolIcon(onClick = onBullets) { Icon(Icons.Outlined.FormatListBulleted, null) }
            EditorToolIcon(onClick = onNumbers) { Icon(Icons.Outlined.FormatListNumbered, null) }
            EditorToolDivider()
            EditorToolIcon(onClick = onQuote) { Icon(Icons.Outlined.FormatQuote, null) }
            EditorToolIcon(onClick = onDivider) { Icon(Icons.Outlined.HorizontalRule, null) }
            EditorToolDivider()
            EditorToolIcon(onClick = onLink) { Icon(Icons.Outlined.Link, null) }
            EditorToolIcon(onClick = onImage) { Icon(Icons.Outlined.Image, null) }
            EditorToolIcon(onClick = onCode) { Icon(Icons.Outlined.Code, null) }
            EditorToolIcon(onClick = onTable) { Icon(Icons.Outlined.TableChart, null) }
            EditorToolDivider()

            Box {
                EditorToolIcon(onClick = { showMore = true }) { Icon(Icons.Outlined.MoreHoriz, null) }
                DropdownMenu(
                    expanded = showMore,
                    onDismissRequest = { showMore = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_find_replace)) },
                        onClick = { showMore = false; onMoreFindReplace() },
                        leadingIcon = { Icon(Icons.Outlined.FindReplace, null) },
                    )
                    DropdownMenuItem(text = { Text("Heading 4") }, onClick = { showMore = false; onMoreHeading4() })
                    DropdownMenuItem(text = { Text("Heading 5") }, onClick = { showMore = false; onMoreHeading5() })
                    DropdownMenuItem(text = { Text("Heading 6") }, onClick = { showMore = false; onMoreHeading6() })
                    DropdownMenuItem(text = { Text("Highlight") }, onClick = { showMore = false; onMoreHighlight() })
                    DropdownMenuItem(text = { Text("Underline") }, onClick = { showMore = false; onMoreUnderline() })
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
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .width(40.dp)
            .height(40.dp),
    ) {
        content()
    }
}

@Composable
private fun EditorToolDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(22.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    )
}
