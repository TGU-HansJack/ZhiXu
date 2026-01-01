package com.zhixu.android.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.graphics.Rect
import kotlin.math.abs
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.core.view.ViewCompat
import androidx.compose.ui.platform.LocalView
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
import com.zhixu.android.R
import com.zhixu.android.data.AccountPreferences
import com.zhixu.android.data.AccountState
import com.zhixu.android.data.SyncPreferences
import com.zhixu.android.data.ThirdPartyServiceConfig
import com.zhixu.android.data.VaultStorageLocation
import com.zhixu.android.data.VaultPreferences
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.data.VaultSyncConfig
import com.zhixu.android.data.VaultSyncPreferences
import com.zhixu.android.data.WebDavConfig
import com.zhixu.android.data.appManagedVaultRootUri
import com.zhixu.android.data.UiPreferences
import com.zhixu.android.sync.VaultAutoSync
import com.zhixu.android.sync.WebDavAutoSync
import com.zhixu.android.ui.DocListMutation
import com.zhixu.android.ui.components.VaultDrawer
import com.zhixu.android.ui.screens.DocumentListScreen
import com.zhixu.android.ui.screens.EditorScreen
import com.zhixu.android.ui.screens.LongImageScreen
import com.zhixu.android.ui.screens.NewDocScreen
import com.zhixu.android.ui.screens.AccountScreen
import com.zhixu.android.ui.screens.SettingsScreen
import com.zhixu.android.ui.screens.SyncScreen
import com.zhixu.android.ui.screens.TasksScreen
import com.zhixu.android.ui.screens.VaultGateScreen
import com.zhixu.android.ui.screens.VaultSettingsScreen
import com.zhixu.android.ui.screens.WorkshopScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZhixuApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var uiReady by remember { mutableStateOf(false) }

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
        initial = AccountState(token = "", username = "", userId = 0L),
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
                PeriodicWorkRequestBuilder<com.zhixu.android.reminders.TaskReminderWorker>(
                    java.time.Duration.ofMinutes(30),
                ).build()
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                "task_reminders",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    val vaultRootUriString by prefs.vaultRootUri.collectAsState(initial = null)
    val vaultRootUri = vaultRootUriString?.let(Uri::parse)

    val uiPrefs = remember(appContext) { UiPreferences(appContext) }
    val languageTag by uiPrefs.languageTag.collectAsState(initial = "")

    LaunchedEffect(languageTag) {
        val trimmed = languageTag.trim()
        val locales = if (trimmed.isBlank()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(trimmed)
        runCatching { AppCompatDelegate.setApplicationLocales(locales) }
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

    LaunchedEffect(vaultRootUriString) {
        val root = vaultRootUri ?: return@LaunchedEffect
        // Build the directory index in the background so the drawer can be O(1) (DB read only).
        delay(1_500)
        withContext(Dispatchers.IO) { runCatching { repository.ensureDirIndexBuilt(root, force = false) } }
    }

    LaunchedEffect(vaultRootUriString) {
        val root = vaultRootUri ?: return@LaunchedEffect
        // Keep expensive full-scan index builds out of normal UI paths: build only on cold start / idle.
        delay(15_000)
        val alreadyReady = runCatching { withContext(Dispatchers.IO) { repository.hasAnyIndexedDocs() } }.getOrDefault(true)
        if (alreadyReady) return@LaunchedEffect

        snapshotFlow { currentRoute }
            .filter { route -> route == null || !route.startsWith("edit") }
            .first()

        withContext(Dispatchers.IO) {
            if (!repository.hasAnyIndexedDocs()) {
                runCatching { repository.rebuildIndex(root) }
            }
        }
    }

    var docSearchRequestToken by remember { mutableLongStateOf(0L) }
    var meRefreshToken by remember { mutableLongStateOf(0L) }
    var docListMutationToken by remember { mutableLongStateOf(0L) }
    var docListMutation by remember { mutableStateOf<DocListMutation?>(null) }
    var dirStructureMutationToken by remember { mutableLongStateOf(0L) }
    var dirStructureMutation by remember { mutableStateOf<DocListMutation?>(null) }

    val showTopBar = currentRoute in setOf("home")
    val showBottomBar = currentRoute in setOf("home", "me")
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { 2 })
    val settledPage by remember { derivedStateOf { pagerState.settledPage } }

    fun navigateHome(pageIndex: Int) {
        val targetPage = pageIndex.coerceIn(0, 1)
        navController.navigate("home") {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        }
        scope.launch {
            if (pagerState.currentPage != targetPage) {
                pagerState.animateScrollToPage(
                    page = targetPage,
                    animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
                )
            }
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

    val drawerEnabled = currentRoute == "home" && vaultRootUri != null && settledPage == 0

    fun openDoc(rawUri: String, query: String? = null, lineIndex: Int? = null) {
        val uriParam = Uri.encode(rawUri)
        val qParam = Uri.encode(query ?: "")
        val lineParam = (lineIndex ?: -1).toString()
        navController.navigate("edit?uri=$uriParam&q=$qParam&line=$lineParam")
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        val appContent: @Composable () -> Unit = {
            var homeSize by remember { mutableStateOf(IntSize.Zero) }
            val view = LocalView.current
            val density = LocalDensity.current
            val exclusionWidthPx = with(density) { 24.dp.toPx() }.toInt().coerceAtLeast(1)

            DisposableEffect(drawerEnabled, homeSize) {
                if (drawerEnabled && homeSize.height > 0) {
                    val rect = Rect(0, 0, exclusionWidthPx, homeSize.height)
                    ViewCompat.setSystemGestureExclusionRects(view, listOf(rect))
                } else {
                    ViewCompat.setSystemGestureExclusionRects(view, emptyList())
                }
                onDispose {
                    ViewCompat.setSystemGestureExclusionRects(view, emptyList())
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { homeSize = it }
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        if (!showTopBar) return@Scaffold
                        Column {
                            when (currentRoute) {
                                "home" -> {
                                    TopAppBar(
                                        windowInsets = TopAppBarDefaults.windowInsets,
                                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                                        title = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                TabText(
                                                    text = stringResource(R.string.nav_docs),
                                                    selected = settledPage == 0,
                                                    onClick = {
                                                        if (pagerState.currentPage == 0) return@TabText
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(
                                                                page = 0,
                                                                animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
                                                            )
                                                        }
                                                    },
                                                )
                                                TabText(
                                                    text = stringResource(R.string.nav_tasks),
                                                    selected = settledPage == 1,
                                                    onClick = {
                                                        if (pagerState.currentPage == 1) return@TabText
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(
                                                                page = 1,
                                                                animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
                                                            )
                                                        }
                                                    },
                                                )
                                            }
                                        },
                                        actions = {
                                            if (settledPage == 0) {
                                                IconButton(onClick = { docSearchRequestToken += 1L }) {
                                                    Icon(
                                                        painter = painterResource(Ionicons.Search),
                                                        contentDescription = stringResource(R.string.action_search),
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }

                                "me" -> {
                                    TopAppBar(
                                        windowInsets = TopAppBarDefaults.windowInsets,
                                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                                        title = { Text(stringResource(R.string.nav_me)) },
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    },
                    bottomBar = {
                        if (!showBottomBar) return@Scaffold
                        MainBottomBar(
                            currentRoute = currentRoute,
                            onHome = { navigateHome(0) },
                            onPlus = { navController.navigate("newDoc") },
                            onMe = ::navigateMe,
                        )
                    },
                ) { padding ->
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
                    )
                }
                composable("home") {
                    HomePager(
                        contentPadding = padding,
                        vaultRootUri = vaultRootUri,
                        prefs = prefs,
                        repository = repository,
                        pagerState = pagerState,
                        docSearchRequestToken = docSearchRequestToken,
                        docListMutationToken = docListMutationToken,
                        docListMutation = docListMutation,
                        onOpenDoc = ::openDoc,
                        onNewDoc = { navController.navigate("newDoc") },
                        onChangeVault = {
                            scope.launch { prefs.setVaultRootUri(null) }
                            navController.navigate("vault") {
                                popUpTo("home") { inclusive = true }
                            }
                        },
                    )
                }
                composable("me") {
                    SettingsScreen(
                        contentPadding = padding,
                        vaultRootUri = vaultRootUri,
                        refreshToken = meRefreshToken,
                        repository = repository,
                        onOpenDoc = ::openDoc,
                        onOpenAccount = { navController.navigate("account") },
                        onOpenVaultSettings = { navController.navigate("vaultSettings") },
                        onOpenWorkshop = { navController.navigate("workshop") },
                        onOpenSync = { navController.navigate("sync") },
                    )
                }
                composable("account") {
                    val accountPrefs = remember(appContext) { com.zhixu.android.data.AccountPreferences(appContext) }
                    AccountScreen(
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
                composable("sync") {
                    SyncScreen(
                        contentPadding = padding,
                        vaultRootUri = vaultRootUri,
                        repository = repository,
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
                            docListMutation = mutation
                            docListMutationToken += 1L
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
                            docListMutation = mutation
                            docListMutationToken += 1L
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
                    )
                    }
                }

            }
        }
        }

        ZhixuSwipeModalDrawer(
            enabled = drawerEnabled,
            resetKey = "$currentRoute|$vaultRootUriString",
            drawerContent = { modifier, closeDrawer, isOpen ->
                VaultDrawer(
                    vaultRootUri = requireNotNull(vaultRootUri),
                    repository = repository,
                    onOpenDoc = { rawUri -> openDoc(rawUri) },
                    onCloseDrawer = closeDrawer,
                    isActive = isOpen,
                    refreshToken = dirStructureMutationToken,
                    mutation = dirStructureMutation,
                    modifier = modifier,
                )
            },
        ) {
            appContent()
        }
    }
}

@Composable
private fun ZhixuSwipeModalDrawer(
    enabled: Boolean,
    resetKey: Any?,
    drawerContent: @Composable (modifier: Modifier, closeDrawer: () -> Unit, isOpen: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current

    val thresholdPx = with(density) { 96.dp.toPx() }
    val scrimMaxAlpha = 0.36f

    var drawerWidthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var offsetTargetPx by remember { mutableFloatStateOf(0f) }
    val offsetPx by animateFloatAsState(
        targetValue = offsetTargetPx,
        animationSpec = if (dragging) snap() else tween(durationMillis = 220, easing = LinearOutSlowInEasing),
        label = "drawerOffsetPx",
    )

    val progress =
        remember(drawerWidthPx, offsetPx) { if (drawerWidthPx <= 0f) 0f else (offsetPx / drawerWidthPx).coerceIn(0f, 1f) }
    val isOpen = progress >= 0.999f

    fun closeDrawer() {
        dragging = false
        offsetTargetPx = 0f
    }

    LaunchedEffect(enabled, resetKey) {
        dragging = false
        offsetTargetPx = 0f
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(enabled, drawerWidthPx) {
                    if (!enabled) return@pointerInput
                    if (drawerWidthPx <= 0f) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        val startOffset = offsetPx

                        var gestureDragging = false
                        var cancelled = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue

                            if (!change.pressed) break

                            val dx = change.position.x - startX
                            val dy = change.position.y - startY

                            if (!gestureDragging) {
                                val absDx = abs(dx)
                                val absDy = abs(dy)
                                val slop = viewConfig.touchSlop

                                if (absDy > slop && absDy > absDx) {
                                    cancelled = true
                                    break
                                }

                                if (absDx > slop && absDx > absDy * 1.15f) {
                                    val opening = dx > 0f
                                    val closing = dx < 0f
                                    val canStart =
                                        (startOffset <= 1f && opening) ||
                                            (startOffset >= drawerWidthPx - 1f && closing) ||
                                            (startOffset > 1f && startOffset < drawerWidthPx - 1f)

                                    if (!canStart) {
                                        cancelled = true
                                        break
                                    }

                                    gestureDragging = true
                                    dragging = true
                                } else {
                                    continue
                                }
                            }

                            if (gestureDragging) {
                                val newOffset = (startOffset + dx).coerceIn(0f, drawerWidthPx)
                                offsetTargetPx = newOffset
                                change.consume()
                            }
                        }

                        if (!gestureDragging || cancelled) return@awaitEachGesture

                        val current = offsetTargetPx
                        val openDistance = current
                        val closeDistance = drawerWidthPx - current

                        val shouldOpen =
                            when {
                                startOffset <= 1f -> openDistance >= thresholdPx
                                startOffset >= drawerWidthPx - 1f -> closeDistance < thresholdPx
                                else -> openDistance >= drawerWidthPx / 2f
                            }

                        dragging = false
                        if (shouldOpen) {
                            offsetTargetPx = drawerWidthPx
                        } else {
                            offsetTargetPx = 0f
                        }
                    }
                },
    ) {
        content()

        if (progress > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimMaxAlpha * progress))
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
                                if (!moved) closeDrawer()
                            }
                        },
            )
        }

        val drawerOffsetX = if (drawerWidthPx <= 0f) -100000 else (-drawerWidthPx + offsetPx).roundToInt()
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .offset { IntOffset(drawerOffsetX, 0) },
        ) {
            drawerContent(
                Modifier.onSizeChanged { drawerWidthPx = it.width.toFloat() },
                ::closeDrawer,
                isOpen,
            )
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
                .height(36.dp)
                .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun MainBottomBar(
    currentRoute: String?,
    onHome: () -> Unit,
    onPlus: () -> Unit,
    onMe: () -> Unit,
) {
    val selectedHome = currentRoute == "home"
    val selectedMe = currentRoute == "me"
    val selectedTint = MaterialTheme.colorScheme.onSurface
    val unselectedTint = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(color = Color.White, tonalElevation = 2.dp) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(70.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                IconButton(onClick = onHome) {
                    Icon(
                        painter = painterResource(if (selectedHome) Ionicons.HomeFilled else Ionicons.Home),
                        contentDescription = null,
                        tint = if (selectedHome) selectedTint else unselectedTint,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(
                    color = Color(0xFF141516),
                    shape = RoundedCornerShape(19.dp),
                    modifier =
                        Modifier
                            .width(68.dp)
                            .height(38.dp)
                            .clickable(onClick = onPlus),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(Ionicons.Add),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                IconButton(onClick = onMe) {
                    Icon(
                        painter = painterResource(if (selectedMe) Ionicons.UserFilled else Ionicons.User),
                        contentDescription = null,
                        tint = if (selectedMe) selectedTint else unselectedTint,
                        modifier = Modifier.size(24.dp),
                    )
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
private fun HomePager(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    prefs: VaultPreferences,
    repository: VaultRepository,
    pagerState: androidx.compose.foundation.pager.PagerState,
    docSearchRequestToken: Long,
    docListMutationToken: Long,
    docListMutation: DocListMutation?,
    onOpenDoc: (String, String?, Int?) -> Unit,
    onNewDoc: () -> Unit,
    onChangeVault: () -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 0,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val isActive = !pagerState.isScrollInProgress && pagerState.settledPage == page
        when (page) {
            0 ->
                DocumentListScreen(
                    contentPadding = contentPadding,
                    vaultRootUri = vaultRootUri,
                    prefs = prefs,
                    repository = repository,
                    isActive = isActive,
                    docListMutationToken = docListMutationToken,
                    docListMutation = docListMutation,
                    searchRequestToken = docSearchRequestToken,
                    onOpenDoc = onOpenDoc,
                    onNewDoc = onNewDoc,
                    onChangeVault = onChangeVault,
                )

            1 ->
                TasksScreen(
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
