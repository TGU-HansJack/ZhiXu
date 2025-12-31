package com.zhixu.android.ui

import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.view.MotionEvent
import kotlin.math.abs
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

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

    var docListRefreshToken by remember { mutableLongStateOf(0L) }
    var docSearchRequestToken by remember { mutableLongStateOf(0L) }
    var meRefreshToken by remember { mutableLongStateOf(0L) }

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

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerEnabled = currentRoute == "home" && vaultRootUri != null

    LaunchedEffect(currentRoute, vaultRootUri) {
        if (currentRoute != "home" || vaultRootUri == null) {
            drawerState.snapTo(DrawerValue.Closed)
        } else {
            drawerState.snapTo(DrawerValue.Closed)
        }
    }

    fun openDoc(rawUri: String, query: String? = null, lineIndex: Int? = null) {
        val uriParam = Uri.encode(rawUri)
        val qParam = Uri.encode(query ?: "")
        val lineParam = (lineIndex ?: -1).toString()
        navController.navigate("edit?uri=$uriParam&q=$qParam&line=$lineParam")
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        val appContent: @Composable () -> Unit = {
            val edgeSwipeEnabled = drawerEnabled && drawerState.isClosed
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .edgeSwipeToOpenDrawer(
                            enabled = edgeSwipeEnabled,
                            onOpen = { scope.launch { drawerState.open() } },
                        ),
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
                        docListRefreshToken = docListRefreshToken,
                        docSearchRequestToken = docSearchRequestToken,
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
                        onCreated = { rawUri ->
                            docListRefreshToken += 1L
                            navController.navigate("edit?uri=${Uri.encode(rawUri)}") {
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
                        onDocListMutated = { docListRefreshToken += 1L },
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

        if (drawerEnabled) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    VaultDrawer(
                        vaultRootUri = requireNotNull(vaultRootUri),
                        repository = repository,
                        onOpenDoc = { rawUri -> openDoc(rawUri) },
                        onCloseDrawer = { scope.launch { drawerState.close() } },
                    )
                },
            ) {
                appContent()
            }
        } else {
            appContent()
        }
    }
}

private fun Modifier.edgeSwipeToOpenDrawer(
    enabled: Boolean,
    onOpen: () -> Unit,
): Modifier {
    return composed {
        if (!enabled) return@composed this

        val density = LocalDensity.current
        val edgeWidthPx = with(density) { 32.dp.toPx() }
        val openThresholdPx = with(density) { 36.dp.toPx() }

        var tracking = false
        var downX = 0f
        var downY = 0f
        var opened = false

        pointerInteropFilter { ev ->
            if (!enabled) return@pointerInteropFilter false

            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    opened = false
                    tracking = ev.x <= edgeWidthPx
                    downX = ev.x
                    downY = ev.y
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!tracking || opened) return@pointerInteropFilter false
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dy) > abs(dx) && abs(dy) > 12f) {
                        tracking = false
                        return@pointerInteropFilter false
                    }
                    if (dx > openThresholdPx) {
                        opened = true
                        tracking = false
                        onOpen()
                        return@pointerInteropFilter true
                    }
                    false
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    tracking = false
                    opened = false
                    false
                }

                else -> false
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
    docListRefreshToken: Long,
    docSearchRequestToken: Long,
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
                    refreshToken = docListRefreshToken,
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
