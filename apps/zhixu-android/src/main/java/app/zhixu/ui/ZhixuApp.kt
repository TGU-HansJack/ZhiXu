package app.zhixu.ui

import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.zhixu.R
import app.zhixu.data.AccountPreferences
import app.zhixu.data.AccountState
import app.zhixu.data.DocumentIndex
import app.zhixu.data.SyncPreferences
import app.zhixu.data.ThirdPartyServiceConfig
import app.zhixu.data.UiPreferences
import app.zhixu.data.VaultPreferences
import app.zhixu.data.VaultIndexUpdater
import app.zhixu.data.VaultRepository
import app.zhixu.data.VaultStorageLocation
import app.zhixu.data.VaultSyncConfig
import app.zhixu.data.VaultSyncPreferences
import app.zhixu.data.WebDavConfig
import app.zhixu.data.AiPreferences
import app.zhixu.data.AppLogRepository
import app.zhixu.data.LogPreferences
import app.zhixu.data.appManagedVaultRootUri
import app.zhixu.ai.AiOcrPostProcessor
import app.zhixu.draw.ZhixuDrawFormat
import app.zhixu.ocr.NoopOcrEngine
import app.zhixu.ocr.OcrEngineCache
import app.zhixu.ocr.OcrWorkflow
import app.zhixu.ocr.ppocrv5.PpOcrV5OcrEngine
import app.zhixu.sync.VaultAutoSync
import app.zhixu.sync.WebDavAutoSync
import app.zhixu.ui.components.CreateMenuSheetContent
import app.zhixu.ui.components.ZhixuCompactDragHandle
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import app.zhixu.ui.screens.AccountScreen
import app.zhixu.ui.screens.AboutScreen
import app.zhixu.ui.screens.AiSettingsScreen
import app.zhixu.ui.screens.AuthScreen
import app.zhixu.ui.screens.CalendarTasksScreen
import app.zhixu.ui.screens.DeviceManagementScreen
import app.zhixu.ui.screens.DocumentListScreen
import app.zhixu.ui.screens.DrawScreen
import app.zhixu.ui.screens.EditorScreen
import app.zhixu.ui.screens.ImagePreviewScreen
import app.zhixu.ui.screens.LongImageScreen
import app.zhixu.ui.screens.NewDocScreen
import app.zhixu.ui.screens.OpenSourceLicenseScreen
import app.zhixu.ui.screens.PrivacyPolicyScreen
import app.zhixu.ui.screens.DailyReminderSettingsScreen
import app.zhixu.ui.screens.NotificationSettingsScreen
import app.zhixu.ui.screens.PermissionsSettingsScreen
import app.zhixu.ui.screens.LogsScreen
import app.zhixu.ui.screens.PomodoroScreen
import app.zhixu.ui.screens.PomodoroSettingsScreen
import app.zhixu.ui.screens.QuadrantsScreen
import app.zhixu.ui.screens.ReminderPopupSettingsScreen
import app.zhixu.ui.screens.ReminderSoundSettingsScreen
import app.zhixu.ui.screens.ReminderVibrationSettingsScreen
import app.zhixu.ui.screens.SettingsScreen
import app.zhixu.ui.screens.SpaceScreen
import app.zhixu.ui.screens.SyncScreen
import app.zhixu.ui.screens.TasksScreen
import app.zhixu.ui.screens.TaskKey
import app.zhixu.ui.screens.TermsOfUseScreen
import app.zhixu.ui.screens.UiSettingsScreen
import app.zhixu.ui.screens.VaultGateScreen
import app.zhixu.ui.screens.VaultSettingsScreen
import app.zhixu.ui.screens.WorkshopScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZhixuApp(
    navIntent: android.content.Intent? = null,
    onNavIntentConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var uiReady by remember { mutableStateOf(false) }
    var createSheetPage by remember { mutableStateOf<CreateSheetPage?>(null) }
    val createSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    LaunchedEffect(Unit) {
        withFrameNanos { }
        uiReady = true
    }

    if (!uiReady) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize())
        }
        return
    }

    val prefs = remember(appContext) { VaultPreferences(appContext) }
    val vaultSyncPrefs = remember(appContext) { VaultSyncPreferences(appContext) }
    val repository = remember(appContext) { VaultRepository(appContext) }
    val aiPrefs = remember(appContext) { AiPreferences(appContext) }
    val logPrefs = remember(appContext) { LogPreferences(appContext) }
    val appLogs = remember(appContext) { AppLogRepository(appContext) }
    val aiOcrPostProcessor =
        remember(appContext) {
            AiOcrPostProcessor(
                prefs = aiPrefs,
                logPrefs = logPrefs,
                appLogs = appLogs,
            )
        }
    val ocrEngineCache =
        remember(appContext) {
            OcrEngineCache(
                factory = { vaultRootUri ->
                    runCatching { PpOcrV5OcrEngine(appContext, repository, vaultRootUri) }
                        .getOrElse { NoopOcrEngine(engineName = "unavailable") }
                },
                releaser = { engine ->
                    (engine as? PpOcrV5OcrEngine)?.release()
                },
            )
        }
    val ocrWorkflow =
        remember(appContext) {
            OcrWorkflow(
                context = appContext,
                repository = repository,
                engineProvider = { vaultRootUri -> ocrEngineCache.get(vaultRootUri) },
                aiPostProcessor = aiOcrPostProcessor,
                aiPrefs = aiPrefs,
            )
        }
    val scope = rememberCoroutineScope()

    val vaultSyncConfig by vaultSyncPrefs.config.collectAsState(
        initial =
            VaultSyncConfig(
                location = VaultStorageLocation.LOCAL,
                thirdParty =
                    ThirdPartyServiceConfig(
                        url = "",
                        username = "",
                        password = "",
                        e2eeEnabled = false,
                        e2eeMasterPassword = "",
                    ),
            ),
    )
    val accountPrefs = remember(appContext) { AccountPreferences(appContext) }
    val accountState by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, email = "", avatarUri = ""),
    )
    val syncPrefs = remember(appContext) { SyncPreferences(appContext) }
    val webDavConfig by syncPrefs.webDavConfig.collectAsState(
        initial =
            WebDavConfig(
                enabled = false,
                baseUrl = "",
                username = "",
                password = "",
                remoteRoot = "/",
                includeIndexSqlite = true,
            ),
    )

    LaunchedEffect(Unit) {
        withFrameNanos { }
        withContext(Dispatchers.Default) {
            val request =
                PeriodicWorkRequestBuilder<app.zhixu.reminders.TaskReminderWorker>(
                    java.time.Duration.ofMinutes(30),
                ).build()
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                "task_reminders",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    // Use a sentinel value to distinguish "not loaded" from "actually null/empty"
    // Empty string "" means "not loaded yet", null means "no vault set"
    val vaultRootUriString by prefs.vaultRootUri.collectAsState(initial = "")
    val isVaultLoaded = vaultRootUriString != ""
    val vaultRootUri = vaultRootUriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    val uiPrefs = remember(appContext) { UiPreferences(appContext) }
    val languageTagOrNull by uiPrefs.languageTagOrNull.collectAsState(initial = null)

    LaunchedEffect(languageTagOrNull) {
        val raw = languageTagOrNull ?: return@LaunchedEffect
        val trimmed = raw.trim()
        val desired =
            if (trimmed.isBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(trimmed)
            }
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.toLanguageTags() != desired.toLanguageTags()) {
            runCatching { AppCompatDelegate.setApplicationLocales(desired) }
        }
    }

    LaunchedEffect(
        vaultRootUriString,
        vaultSyncConfig.location,
        accountState.token,
        vaultSyncConfig.thirdParty.url,
        vaultSyncConfig.thirdParty.username,
        vaultSyncConfig.thirdParty.password,
    ) {
        val root = vaultRootUri ?: return@LaunchedEffect
        runCatching {
            VaultAutoSync.maybeSyncVault(
                context = context,
                repository = repository,
                vaultRootUri = root,
                force = false,
            )
        }
    }

    LaunchedEffect(
        vaultRootUriString,
        vaultSyncConfig.location,
        webDavConfig.enabled,
        webDavConfig.baseUrl,
        webDavConfig.username,
        webDavConfig.password,
        webDavConfig.remoteRoot,
        webDavConfig.includeIndexSqlite,
    ) {
        if (vaultSyncConfig.location != VaultStorageLocation.LOCAL) return@LaunchedEffect
        runCatching {
            WebDavAutoSync.maybeSyncVault(
                context = context,
                repository = repository,
                vaultRootUri = vaultRootUri,
                config = webDavConfig,
                force = false,
            )
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val indexUpdater = remember(appContext) { VaultIndexUpdater(appContext, repository) }
    val documentIndex = remember(repository) { DocumentIndex(repository) }
    SideEffect {
        indexUpdater.setCanRunHeavyWork(currentRoute == null || !currentRoute.startsWith("edit"))
    }
    LaunchedEffect(vaultRootUriString) {
        indexUpdater.setVaultRootUri(vaultRootUri)
    }

    LaunchedEffect(vaultRootUriString) {
        val root = vaultRootUri ?: return@LaunchedEffect
        // Build the directory index in the background so the drawer can be O(1) (DB read only).
        delay(1_500)
        withContext(Dispatchers.IO) { runCatching { repository.ensureDirIndexBuilt(root, force = false) } }
    }

    var docSearchRequestToken by remember { mutableLongStateOf(0L) }
    var meRefreshToken by remember { mutableLongStateOf(0L) }
    var dirStructureMutationToken by remember { mutableLongStateOf(0L) }
    var dirStructureMutation by remember { mutableStateOf<DocListMutation?>(null) }

    var selectedDocUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedTasks by remember { mutableStateOf<Set<TaskKey>>(emptySet()) }

    fun <T> toggleSelection(current: Set<T>, item: T): Set<T> = if (item in current) current - item else current + item

    fun onDocListMutated(mutation: DocListMutation) {
        dirStructureMutation = mutation
        dirStructureMutationToken += 1L
    }

    fun clearAllSelections() {
        selectedDocUris = emptySet()
        selectedTasks = emptySet()
    }

    val showTopBar = currentRoute in setOf("home", "tasks", "pomodoro")
    val showBottomBar = currentRoute in setOf("home", "tasks", "pomodoro", "me")

    val todoPagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { 3 })
    val settledTodoPage by remember { derivedStateOf { todoPagerState.settledPage } }
    var lastSettledTodoPage by remember { mutableStateOf(settledTodoPage) }

    var bulkDeleteTarget by remember { mutableStateOf<BulkDeleteTarget?>(null) }
    val bulkDeleteCount =
        when (currentRoute) {
            "home" -> selectedDocUris.size
            "tasks" -> selectedTasks.size
            else -> 0
        }

    LaunchedEffect(currentRoute) {
        clearAllSelections()
        bulkDeleteTarget = null
    }

    LaunchedEffect(currentRoute, settledTodoPage) {
        if (currentRoute != "tasks") return@LaunchedEffect
        if (lastSettledTodoPage == settledTodoPage) return@LaunchedEffect
        lastSettledTodoPage = settledTodoPage
        clearAllSelections()
        bulkDeleteTarget = null
    }

    fun navigateDocs() {
        navController.navigate("home") {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        }
    }

    fun navigateTasks() {
        navController.navigate("tasks") {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        }
        scope.launch {
            todoPagerState.animateScrollToPage(
                page = 0,
                animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
            )
        }
    }

    fun navigatePomodoro() {
        navController.navigate("pomodoro") {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        }
    }

    fun navigateMe() {
        meRefreshToken += 1L
        navController.navigate("me") {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        }
    }

    fun openDoc(rawUri: String, query: String? = null, lineIndex: Int? = null) {
        val maybeUri = runCatching { Uri.parse(rawUri) }.getOrNull()
        val isDrawing =
            maybeUri != null &&
                runCatching {
                    val name = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, maybeUri)?.name.orEmpty()
                    ZhixuDrawFormat.hasDrawingExtension(name) || ZhixuDrawFormat.hasDrawingExtension(rawUri)
                }.getOrDefault(false)
        if (isDrawing) {
            val uriParam = Uri.encode(rawUri)
            navController.navigate("draw?uri=$uriParam")
            return
        }
        val isImage =
            maybeUri?.scheme != null &&
                runCatching { context.contentResolver.getType(maybeUri)?.startsWith("image/") == true }.getOrDefault(false)
        if (isImage) {
            val uriParam = Uri.encode(rawUri)
            navController.navigate("image?uri=$uriParam")
            return
        }

        val uriParam = Uri.encode(rawUri)
        val qParam = Uri.encode(query ?: "")
        val lineParam = (lineIndex ?: -1).toString()
        navController.navigate("edit?uri=$uriParam&q=$qParam&line=$lineParam")
    }

    LaunchedEffect(navIntent, vaultRootUriString) {
        val intent = navIntent ?: return@LaunchedEffect
        val docUri = intent.getStringExtra(app.zhixu.reminders.TaskReminderWorker.EXTRA_DOC_URI).orEmpty()
        if (docUri.isBlank()) return@LaunchedEffect
        val lineIndex = intent.getIntExtra(app.zhixu.reminders.TaskReminderWorker.EXTRA_LINE_INDEX, -1).takeIf { it >= 0 }

        // Ensure we are in the main graph; openDoc will navigate to editor.
        if (vaultRootUriString == null) {
            navController.navigate("vault") {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            }
        } else {
            navController.navigate("home") {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            }
        }
        openDoc(docUri, null, lineIndex)
        onNavIntentConsumed()
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        if (!showTopBar) return@Scaffold
                        Column {
                            when (currentRoute) {
                                "home" -> {
                                    ZhixuTopAppBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        title = { Text(text = stringResource(R.string.nav_docs)) },
                                        actions = {
                                            ZhixuIconButton(onClick = { docSearchRequestToken += 1L }) {
                                                Icon(
                                                    painter = painterResource(Ionicons.Search),
                                                    contentDescription = stringResource(R.string.action_search),
                                                    modifier = Modifier.size(ZhixuTopBarIconSize),
                                                )
                                            }
                                        },
                                    )
                                }

                                "tasks" -> {
                                    ZhixuTopAppBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        title = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                val scroll = rememberScrollState()
                                                Row(
                                                    modifier = Modifier.weight(1f).horizontalScroll(scroll),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    TabText(
                                                        text = stringResource(R.string.nav_tasks),
                                                        selected = settledTodoPage == 0,
                                                        onClick = {
                                                            if (todoPagerState.currentPage == 0) return@TabText
                                                            scope.launch {
                                                                todoPagerState.animateScrollToPage(
                                                                    page = 0,
                                                                    animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
                                                                )
                                                            }
                                                        },
                                                    )
                                                    TabText(
                                                        text = stringResource(R.string.nav_calendar),
                                                        selected = settledTodoPage == 1,
                                                        onClick = {
                                                            if (todoPagerState.currentPage == 1) return@TabText
                                                            scope.launch {
                                                                todoPagerState.animateScrollToPage(
                                                                    page = 1,
                                                                    animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
                                                                )
                                                            }
                                                        },
                                                    )
                                                    TabText(
                                                        text = stringResource(R.string.nav_quadrants),
                                                        selected = settledTodoPage == 2,
                                                        onClick = {
                                                            if (todoPagerState.currentPage == 2) return@TabText
                                                            scope.launch {
                                                                todoPagerState.animateScrollToPage(
                                                                    page = 2,
                                                                    animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
                                                                )
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }

                                "pomodoro" -> {
                                    ZhixuTopAppBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        title = { Text(text = stringResource(R.string.nav_pomodoro)) },
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    },
                    bottomBar = {
                        if (!showBottomBar) return@Scaffold
                        val centerIconRes = if (bulkDeleteCount > 0) Ionicons.TrashOutline else Ionicons.Add
                        val onCenterClick = {
                            if (bulkDeleteCount > 0) {
                                bulkDeleteTarget =
                                    when (currentRoute) {
                                        "home" -> BulkDeleteTarget.Docs
                                        "tasks" -> BulkDeleteTarget.Tasks
                                        else -> null
                                    }
                            } else {
                                createSheetPage = CreateSheetPage.Menu
                            }
                        }
                        MainBottomBar(
                            currentRoute = currentRoute,
                            onDocs = ::navigateDocs,
                            onTasks = ::navigateTasks,
                            onPomodoro = ::navigatePomodoro,
                            centerIconRes = centerIconRes,
                            onCenter = onCenterClick,
                            onMe = ::navigateMe,
                        )
                    },
                ) { padding ->
                    val target = bulkDeleteTarget
                    if (target != null) {
                        val count =
                            when (target) {
                                BulkDeleteTarget.Docs -> selectedDocUris.size
                                BulkDeleteTarget.Tasks -> selectedTasks.size
                            }
                        AlertDialog(
                            onDismissRequest = { bulkDeleteTarget = null },
                            title = { Text(stringResource(R.string.dialog_delete_selected_title)) },
                            text = { Text(stringResource(R.string.dialog_delete_selected_message, count)) },
                            confirmButton = {
                                TextButton(
                                    enabled = count > 0,
                                    onClick = {
                                        val root = vaultRootUri
                                        scope.launch {
                                            var failed = 0
                                            when (target) {
                                                BulkDeleteTarget.Docs -> {
                                                    val toDelete = selectedDocUris.toList()
                                                    selectedDocUris = emptySet()
                                                    for (uriStr in toDelete) {
                                                        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: continue
                                                        val ok = withContext(Dispatchers.IO) { repository.deleteDoc(uri) }
                                                        if (ok) onDocListMutated(DocListMutation.Deleted(docUri = uri)) else failed += 1
                                                    }
                                                }

                                                BulkDeleteTarget.Tasks -> {
                                                    val toDelete = selectedTasks
                                                    selectedTasks = emptySet()
                                                    val byDoc = toDelete.groupBy { it.docUri }
                                                    for ((docUriStr, keys) in byDoc) {
                                                        val uri = runCatching { Uri.parse(docUriStr) }.getOrNull() ?: continue
                                                        val lineIndices = keys.map { it.lineIndex }.toSet()
                                                        val ok = withContext(Dispatchers.IO) { repository.deleteTasks(uri, lineIndices) }
                                                        if (!ok) failed += 1
                                                        runCatching {
                                                            VaultAutoSync.maybeUploadDoc(
                                                                context = context,
                                                                repository = repository,
                                                                vaultRootUri = root,
                                                                docUri = uri,
                                                                force = false,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            if (failed > 0) {
                                                android.widget.Toast
                                                    .makeText(context, context.getString(R.string.editor_delete_failed), android.widget.Toast.LENGTH_SHORT)
                                                    .show()
                                            }
                                            bulkDeleteTarget = null
                                        }
                                    },
                                ) { Text(stringResource(R.string.action_delete)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { bulkDeleteTarget = null }) { Text(stringResource(R.string.action_cancel)) }
                            },
                        )
                    }
                    if (createSheetPage != null) {
                        ModalBottomSheet(
                            onDismissRequest = { createSheetPage = null },
                            sheetState = createSheetState,
                            dragHandle = { ZhixuCompactDragHandle() },
                        ) {
                            when (createSheetPage) {
                                CreateSheetPage.Menu -> {
                                    CreateMenuSheetContent(
                                        onOcr = { createSheetPage = CreateSheetPage.Ocr },
                                        onRecord = { android.widget.Toast.makeText(context, "录音：敬请期待", android.widget.Toast.LENGTH_SHORT).show() },
                                        onCamera = { android.widget.Toast.makeText(context, "相机：敬请期待", android.widget.Toast.LENGTH_SHORT).show() },
                                        onDraw = {
                                            createSheetPage = null
                                            navController.navigate("draw?uri=")
                                        },
                                        onNewTodo = { createSheetPage = CreateSheetPage.Todo },
                                        onNewNote = { createSheetPage = CreateSheetPage.Note },
                                    )
                                }

                                CreateSheetPage.Todo -> {
                                    TodoComposerSheet(
                                        vaultRootUri = vaultRootUri,
                                        repository = repository,
                                        onBack = { createSheetPage = CreateSheetPage.Menu },
                                        onClose = { createSheetPage = null },
                                    )
                                }

                                CreateSheetPage.Note -> {
                                    NoteComposerSheet(
                                        sheetState = createSheetState,
                                        vaultRootUri = vaultRootUri,
                                        repository = repository,
                                        onBack = { createSheetPage = CreateSheetPage.Menu },
                                        onCreated = { created ->
                                            val mutation = DocListMutation.Created(created)
                                            dirStructureMutation = mutation
                                            dirStructureMutationToken += 1L
                                            createSheetPage = null
                                            navController.navigate("edit?uri=${Uri.encode(created.uri.toString())}") {
                                                popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                                            }
                                        },
                                    )
                                }

                                CreateSheetPage.Ocr -> {
                                    OcrComposerSheet(
                                        vaultRootUri = vaultRootUri,
                                        repository = repository,
                                        workflow = ocrWorkflow,
                                        onBack = { createSheetPage = CreateSheetPage.Menu },
                                        onClose = { createSheetPage = null },
                                        onCreated = { created ->
                                            val mutation = DocListMutation.Created(created)
                                            dirStructureMutation = mutation
                                            dirStructureMutationToken += 1L
                                            createSheetPage = null
                                            navController.navigate("edit?uri=${Uri.encode(created.uri.toString())}") {
                                                popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                                            }
                                        },
                                    )
                                }

                                null -> Unit
                            }
                        }
                    }
                    // Wait until vault URI is loaded before showing NavHost
                    if (!isVaultLoaded) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Show nothing while loading to avoid flash
                        }
                        return@Scaffold
                    }
                    NavHost(
                        navController = navController,
                        startDestination = if (vaultRootUri == null) "vault" else "home",
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None },
                        sizeTransform = null,
                    ) {
                composable("vault") {
                    VaultGateScreen(
                        onSelectLocalFolder = { uri ->
                            repository.ensureVaultStructure(uri)
                            vaultSyncPrefs.setLocation(VaultStorageLocation.LOCAL)
                            prefs.setVaultRootUri(uri.toString())
                            navController.navigate("home") {
                                popUpTo("vault") { inclusive = true }
                            }
                        },
                        onSelectOfficialServer = {
                            val uri = appManagedVaultRootUri(appContext)
                            repository.ensureVaultStructure(uri)
                            vaultSyncPrefs.setLocation(VaultStorageLocation.OFFICIAL_SERVER)
                            prefs.setVaultRootUri(uri.toString())
                            navController.navigate("home") {
                                popUpTo("vault") { inclusive = true }
                            }
                        },
                        onSelectThirdPartyService = {
                            val uri = appManagedVaultRootUri(appContext)
                            repository.ensureVaultStructure(uri)
                            vaultSyncPrefs.setLocation(VaultStorageLocation.THIRD_PARTY_SERVICE)
                            prefs.setVaultRootUri(uri.toString())
                            navController.navigate("home") {
                                popUpTo("vault") { inclusive = true }
                            }
                        },
                        onSelectLocalPrivateDir = {
                            val uri = appManagedVaultRootUri(appContext)
                            repository.ensureVaultStructure(uri)
                            vaultSyncPrefs.setLocation(VaultStorageLocation.LOCAL)
                            prefs.setVaultRootUri(uri.toString())
                            navController.navigate("home") {
                                popUpTo("vault") { inclusive = true }
                            }
                        },
                    )
                }
                composable("home") {
                    DocumentListScreen(
                        contentPadding = padding,
                        vaultRootUri = vaultRootUri,
                        repository = repository,
                        documentIndex = documentIndex,
                        indexUpdater = indexUpdater,
                        isActive = currentRoute == "home",
                        searchRequestToken = docSearchRequestToken,
                        onOpenDoc = ::openDoc,
                        onNewDoc = { navController.navigate("newDoc") },
                        onChangeVault = {
                            scope.launch { prefs.setVaultRootUri(null) }
                            navController.navigate("vault") {
                                popUpTo("home") { inclusive = true }
                            }
                        },
                        onDocListMutated = ::onDocListMutated,
                        selectedDocUris = selectedDocUris,
                        onToggleDocSelection = { uri -> selectedDocUris = toggleSelection(selectedDocUris, uri) },
                        onClearDocSelection = { selectedDocUris = emptySet() },
                    )
                }
                composable("tasks") {
                    TodoPager(
                        contentPadding = padding,
                        vaultRootUri = vaultRootUri,
                        repository = repository,
                        pagerState = todoPagerState,
                        selectedTasks = selectedTasks,
                        onToggleTaskSelection = { key -> selectedTasks = toggleSelection(selectedTasks, key) },
                        onClearTaskSelection = { selectedTasks = emptySet() },
                        onOpenDoc = ::openDoc,
                    )
                }
                composable("pomodoro") {
                    PomodoroScreen(
                        contentPadding = padding,
                    )
                }
                composable("me") {
                    SettingsScreen(
                        contentPadding = padding,
                        vaultRootUri = vaultRootUri,
                        onOpenAiSettings = { navController.navigate("aiSettings") },
                        onOpenWorkshop = { navController.navigate("workshop") },
                        onOpenPomodoroSettings = { navController.navigate("pomodoroSettings") },
                        onOpenNotificationSettings = { navController.navigate("notificationSettings") },
                        onOpenDailyReminder = { navController.navigate("dailyReminder") },
                        onOpenReminderSound = { navController.navigate("reminderSound") },
                        onOpenReminderVibration = { navController.navigate("reminderVibration") },
                        onOpenReminderPopup = { navController.navigate("reminderPopup") },
                        vaultPrefs = prefs,
                        vaultSyncPrefs = vaultSyncPrefs,
                        repository = repository,
                        onOpenTermsOfUse = { navController.navigate("termsOfUse") },
                        onOpenPrivacyPolicy = { navController.navigate("privacyPolicy") },
                        onOpenOpenSourceLicense = { navController.navigate("openSourceLicense") },
                    )
                }
                composable("pomodoroSettings") {
                    PomodoroSettingsScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("notificationSettings") {
                    NotificationSettingsScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                        onOpenDailyReminder = { navController.navigate("dailyReminder") },
                        onOpenReminderSound = { navController.navigate("reminderSound") },
                        onOpenReminderVibration = { navController.navigate("reminderVibration") },
                        onOpenReminderPopup = { navController.navigate("reminderPopup") },
                    )
                }
                composable("dailyReminder") {
                    DailyReminderSettingsScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("reminderSound") {
                    ReminderSoundSettingsScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("reminderVibration") {
                    ReminderVibrationSettingsScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("reminderPopup") {
                    ReminderPopupSettingsScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("aiSettings") {
                    AiSettingsScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("uiSettings") {
                    UiSettingsScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("termsOfUse") {
                    TermsOfUseScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("privacyPolicy") {
                    PrivacyPolicyScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("openSourceLicense") {
                    OpenSourceLicenseScreen(
                        contentPadding = padding,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("account") {
                    val accountPrefs = remember(appContext) { app.zhixu.data.AccountPreferences(appContext) }
                    AccountScreen(
                        contentPadding = padding,
                        accountPrefs = accountPrefs,
                        onBack = { navController.popBackStack() },
                        onOpenDeviceManagement = { navController.navigate("deviceManagement") },
                    )
                }
                composable("auth") {
                    val accountPrefs = remember(appContext) { app.zhixu.data.AccountPreferences(appContext) }
                    AuthScreen(
                        contentPadding = padding,
                        accountPrefs = accountPrefs,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("deviceManagement") {
                    val accountPrefs = remember(appContext) { app.zhixu.data.AccountPreferences(appContext) }
                    DeviceManagementScreen(
                        contentPadding = padding,
                        accountPrefs = accountPrefs,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("vaultSettings") {
                    VaultSettingsScreen(
                        contentPadding = padding,
                        vaultRootUri = vaultRootUri,
                        repository = repository,
                        vaultPrefs = prefs,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("workshop") {
                    WorkshopScreen(
                        contentPadding = padding,
                        vaultRootUri = vaultRootUri,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("longImage") {
                    val prev = navController.previousBackStackEntry
                    val markdown = prev?.savedStateHandle?.get<String>(KEY_LONG_IMAGE_MARKDOWN).orEmpty()
                    val vaultRootRaw = prev?.savedStateHandle?.get<String>(KEY_LONG_IMAGE_VAULT_ROOT)
                    val fontScale = prev?.savedStateHandle?.get<Float>(KEY_LONG_IMAGE_FONT_SCALE) ?: 1f
                    val title = prev?.savedStateHandle?.get<String>(KEY_LONG_IMAGE_TITLE).orEmpty()

                    if (markdown.isBlank()) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                        return@composable
                    }

                    LongImageScreen(
                        markdown = markdown,
                        vaultRootUri = vaultRootRaw?.takeIf { it.isNotBlank() }?.let(Uri::parse),
                        fontScale = fontScale,
                        title = title,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = "image?uri={uri}",
                    arguments = listOf(
                        navArgument("uri") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    val uriParam = entry.arguments?.getString("uri") ?: ""
                    ImagePreviewScreen(
                        docUri = parseNavUri(uriParam),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("newDoc") {
                    val root = vaultRootUri
                    if (root == null) {
                        navController.navigate("vault") {
                            popUpTo("newDoc") { inclusive = true }
                        }
                        return@composable
                    }
                    NewDocScreen(
                        vaultRootUri = root,
                        repository = repository,
                        onCreated = { created ->
                            val mutation = DocListMutation.Created(created)
                            dirStructureMutation = mutation
                            dirStructureMutationToken += 1L
                            navController.navigate("edit?uri=${Uri.encode(created.uri.toString())}") {
                                popUpTo("home") { inclusive = false }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = "draw?uri={uri}",
                    arguments = listOf(
                        navArgument("uri") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    val root = vaultRootUri
                    if (root == null) {
                        navController.navigate("vault") {
                            popUpTo("draw?uri=") { inclusive = true }
                        }
                        return@composable
                    }
                    val uriParam = entry.arguments?.getString("uri") ?: ""
                    val docUri = parseNavUri(uriParam).takeIf { it != Uri.EMPTY }
                    DrawScreen(
                        vaultRootUri = root,
                        repository = repository,
                        docUri = docUri,
                        onDocListMutated = ::onDocListMutated,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = "edit?uri={uri}&q={q}&line={line}",
                    arguments = listOf(
                        navArgument("uri") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("q") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("line") {
                            type = NavType.IntType
                            defaultValue = -1
                        },
                    ),
                    enterTransition = {
                        val fromEdit = initialState.destination.route?.startsWith("edit") == true
                        if (fromEdit) {
                            EnterTransition.None
                        } else {
                            slideInVertically(
                                animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing),
                                initialOffsetY = { fullHeight -> fullHeight },
                            ) +
                                fadeIn(animationSpec = tween(durationMillis = 100)) +
                                scaleIn(initialScale = 0.98f, animationSpec = tween(durationMillis = 160))
                        }
                    },
                    exitTransition = {
                        val toEdit = targetState.destination.route?.startsWith("edit") == true
                        if (toEdit) {
                            ExitTransition.None
                        } else {
                            slideOutVertically(
                                animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing),
                                targetOffsetY = { fullHeight -> fullHeight },
                            ) +
                                fadeOut(animationSpec = tween(durationMillis = 100)) +
                                scaleOut(targetScale = 0.98f, animationSpec = tween(durationMillis = 160))
                        }
                    },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = {
                        slideOutVertically(
                            animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing),
                            targetOffsetY = { fullHeight -> fullHeight },
                        ) +
                            fadeOut(animationSpec = tween(durationMillis = 100)) +
                            scaleOut(targetScale = 0.98f, animationSpec = tween(durationMillis = 160))
                    },
                ) { entry ->
                    val uriParam = entry.arguments?.getString("uri") ?: ""
                    val qParam = entry.arguments?.getString("q") ?: ""
                    val lineParam = entry.arguments?.getInt("line") ?: -1
                    EditorScreen(
                        docUri = parseNavUri(uriParam),
                        vaultRootUri = vaultRootUri,
                        repository = repository,
                        onBack = { navController.popBackStack() },
                        onDocListMutated = { mutation ->
                            dirStructureMutation = mutation
                            dirStructureMutationToken += 1L
                        },
                        onOpenDoc = ::openDoc,
                        onGenerateLongImage = { req ->
                            navController.currentBackStackEntry?.savedStateHandle?.apply {
                                set(KEY_LONG_IMAGE_MARKDOWN, req.markdown)
                                set(KEY_LONG_IMAGE_VAULT_ROOT, req.vaultRootUri?.toString().orEmpty())
                                set(KEY_LONG_IMAGE_FONT_SCALE, req.fontScale)
                                set(KEY_LONG_IMAGE_TITLE, req.title)
                            }
                            navController.navigate("longImage")
                        },
                        initialQuery = Uri.decode(qParam).takeIf { it.isNotBlank() },
                        initialLineIndex = lineParam.takeIf { it >= 0 },
                        dirStructureMutationToken = dirStructureMutationToken,
                        dirStructureMutation = dirStructureMutation,
                    )
                    }
                }

            }
        }

    }
@Composable
private fun TabText(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .height(32.dp)
                .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun MainBottomBar(
    currentRoute: String?,
    onDocs: () -> Unit,
    onTasks: () -> Unit,
    centerIconRes: Int,
    onCenter: () -> Unit,
    onPomodoro: () -> Unit,
    onMe: () -> Unit,
) {
    val selectedDocs = currentRoute == "home"
    val selectedTasks = currentRoute == "tasks"
    val selectedPomodoro = currentRoute == "pomodoro"
    val selectedMe = currentRoute == "me"
    val selectedTint = MaterialTheme.colorScheme.primary
    val unselectedTint = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(ZhixuBottomBarContentHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ZhixuIconButton(onClick = onDocs) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(Ionicons.DocumentOutline),
                            contentDescription = stringResource(R.string.nav_docs),
                            tint = if (selectedDocs) selectedTint else unselectedTint,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.nav_docs),
                            color = if (selectedDocs) selectedTint else unselectedTint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selectedDocs) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
                ZhixuIconButton(onClick = onTasks) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(Ionicons.CheckmarkCircle),
                            contentDescription = stringResource(R.string.nav_tasks),
                            tint = if (selectedTasks) selectedTint else unselectedTint,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.nav_tasks),
                            color = if (selectedTasks) selectedTint else unselectedTint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selectedTasks) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp),
                    modifier =
                        Modifier
                            .width(68.dp)
                            .height(38.dp)
                            .clickable(onClick = onCenter),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(centerIconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                ZhixuIconButton(onClick = onPomodoro) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(Ionicons.Target),
                            contentDescription = stringResource(R.string.nav_pomodoro),
                            tint = if (selectedPomodoro) selectedTint else unselectedTint,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.nav_pomodoro),
                            color = if (selectedPomodoro) selectedTint else unselectedTint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selectedPomodoro) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
                ZhixuIconButton(onClick = onMe) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(Ionicons.User),
                            contentDescription = stringResource(R.string.nav_me),
                            tint = if (selectedMe) selectedTint else unselectedTint,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.nav_me),
                            color = if (selectedMe) selectedTint else unselectedTint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selectedMe) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

internal const val KEY_LONG_IMAGE_MARKDOWN: String = "long_image_markdown"
internal const val KEY_LONG_IMAGE_VAULT_ROOT: String = "long_image_vault_root"
internal const val KEY_LONG_IMAGE_FONT_SCALE: String = "long_image_font_scale"
internal const val KEY_LONG_IMAGE_TITLE: String = "long_image_title"

@Composable
private fun TodoPager(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    pagerState: androidx.compose.foundation.pager.PagerState,
    selectedTasks: Set<TaskKey>,
    onToggleTaskSelection: (TaskKey) -> Unit,
    onClearTaskSelection: () -> Unit,
    onOpenDoc: (String, String?, Int?) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val isActive = !pagerState.isScrollInProgress && pagerState.settledPage == page
        when (page) {
            0 ->
                TasksScreen(
                    contentPadding = contentPadding,
                    vaultRootUri = vaultRootUri,
                    repository = repository,
                    isActive = isActive,
                    onOpenDoc = onOpenDoc,
                    selectedTasks = selectedTasks,
                    onToggleTaskSelection = onToggleTaskSelection,
                    onClearTaskSelection = onClearTaskSelection,
                )

            1 ->
                CalendarTasksScreen(
                    contentPadding = contentPadding,
                    vaultRootUri = vaultRootUri,
                    repository = repository,
                    isActive = isActive,
                    onOpenDoc = onOpenDoc,
                )

            2 ->
                QuadrantsScreen(
                    contentPadding = contentPadding,
                    vaultRootUri = vaultRootUri,
                    repository = repository,
                    isActive = isActive,
                    onOpenDoc = onOpenDoc,
                )
        }
    }
}

private fun parseNavUri(param: String): Uri {
    if (param.isBlank()) return Uri.EMPTY
    val direct = Uri.parse(param)
    if (direct.scheme != null) return direct

    val decoded = Uri.decode(param)
    val decodedUri = Uri.parse(decoded)
    if (decodedUri.scheme != null) return decodedUri

    return direct
}

private enum class BulkDeleteTarget {
    Docs,
    Tasks,
}

private enum class CreateSheetPage {
    Menu,
    Todo,
    Note,
    Ocr,
}
