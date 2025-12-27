package com.zhixu.android.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.zhixu.android.data.VaultPreferences
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.R
import com.zhixu.android.ui.screens.DocumentListScreen
import com.zhixu.android.ui.screens.EditorScreen
import com.zhixu.android.ui.screens.NewDocScreen
import com.zhixu.android.ui.screens.SettingsScreen
import com.zhixu.android.ui.screens.TasksScreen
import com.zhixu.android.ui.screens.VaultGateScreen
import com.zhixu.android.ui.screens.WorkshopScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZhixuApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val prefs = remember(appContext) { VaultPreferences(appContext) }
    val repository = remember(appContext) { VaultRepository(appContext) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val request = PeriodicWorkRequestBuilder<com.zhixu.android.reminders.TaskReminderWorker>(
            java.time.Duration.ofMinutes(30),
        ).build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            "task_reminders",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    val vaultRootUriString by prefs.vaultRootUri.collectAsState(initial = null)
    val vaultRootUri = vaultRootUriString?.let(Uri::parse)

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    val showTopBar = currentRoute in setOf("home", "settings")
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    fun navigateHome(pageIndex: Int) {
        navController.navigate("home") {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        }
        scope.launch {
            pagerState.animateScrollToPage(pageIndex.coerceIn(0, 1))
            drawerState.close()
        }
    }

    fun navigateSettings() {
        navController.navigate("settings") {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        }
        scope.launch { drawerState.close() }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled =
                currentRoute != "vault" &&
                    currentRoute != "settings" &&
                    !currentRoute.orEmpty().startsWith("edit"),
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Color.White) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = MaterialTheme.shapes.extraLarge,
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UserAvatarButton(
                                    size = 48.dp,
                                    onClick = { scope.launch { drawerState.close() } },
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Zhixu", style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        text = "ID: —",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = ::navigateSettings) {
                                    Icon(imageVector = Icons.Outlined.Settings, contentDescription = stringResource(R.string.nav_settings))
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = MaterialTheme.shapes.extraLarge,
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            val selectedBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            ListItem(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(if (currentRoute == "home" && pagerState.currentPage == 0) selectedBg else Color.Transparent, shape = MaterialTheme.shapes.extraLarge)
                                        .clickable { navigateHome(0) },
                                leadingContent = { Icon(imageVector = Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                headlineContent = { Text(stringResource(R.string.nav_docs)) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            ListItem(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(if (currentRoute == "home" && pagerState.currentPage == 1) selectedBg else Color.Transparent, shape = MaterialTheme.shapes.extraLarge)
                                        .clickable { navigateHome(1) },
                                leadingContent = { Icon(imageVector = Icons.Outlined.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                headlineContent = { Text(stringResource(R.string.nav_tasks)) },
                            )
                        }
                    }
                }
            },
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    if (!showTopBar) return@Scaffold

                    Column {
                        when (currentRoute) {
                            "home" -> {
                                TopAppBar(
                                    windowInsets = TopAppBarDefaults.windowInsets,
                                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                                    navigationIcon = {
                                        UserAvatarButton(
                                            onClick = { scope.launch { drawerState.open() } },
                                            modifier = Modifier.padding(start = 12.dp),
                                        )
                                    },
                                    title = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            val current = pagerState.currentPage
                                            TabText(
                                                text = stringResource(R.string.nav_docs),
                                                selected = current == 0,
                                                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                                            )
                                            TabText(
                                                text = stringResource(R.string.nav_tasks),
                                                selected = current == 1,
                                                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                                            )
                                        }
                                    },
                                )
                            }

                            "settings" -> {
                                TopAppBar(
                                    windowInsets = TopAppBarDefaults.windowInsets,
                                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                                    navigationIcon = {
                                        IconButton(
                                            onClick = {
                                                val popped = navController.popBackStack()
                                                if (!popped) navigateHome(0)
                                            },
                                        ) {
                                            Icon(imageVector = Icons.Outlined.Close, contentDescription = null)
                                        }
                                    },
                                    title = { Text(stringResource(R.string.nav_settings)) },
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
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
                            onVaultSelected = { uri ->
                                prefs.setVaultRootUri(uri.toString())
                                navController.navigate("home") {
                                    popUpTo("vault") { inclusive = true }
                                }
                            },
                            onEnsureVault = { uri -> repository.ensureVaultStructure(uri) },
                        )
                    }
                    composable("home") {
                        HomePager(
                            contentPadding = padding,
                            vaultRootUri = vaultRootUri,
                            prefs = prefs,
                            repository = repository,
                            pagerState = pagerState,
                            onOpenDoc = { rawUri, query, lineIndex ->
                                val uriParam = Uri.encode(rawUri)
                                val qParam = Uri.encode(query ?: "")
                                val lineParam = (lineIndex ?: -1).toString()
                                navController.navigate("edit?uri=$uriParam&q=$qParam&line=$lineParam")
                            },
                            onNewDoc = { navController.navigate("newDoc") },
                            onChangeVault = {
                                scope.launch { prefs.setVaultRootUri(null) }
                                navController.navigate("vault") {
                                    popUpTo("home") { inclusive = true }
                                }
                            },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            contentPadding = padding,
                            vaultRootUri = vaultRootUri,
                            repository = repository,
                            onChangeVault = {
                                scope.launch { prefs.setVaultRootUri(null) }
                                navController.navigate("vault") {
                                    popUpTo("settings") { inclusive = true }
                                }
                            },
                            onOpenWorkshop = {
                                navController.navigate("workshop")
                            },
                        )
                    }
                    composable("workshop") {
                        WorkshopScreen(
                            contentPadding = padding,
                            vaultRootUri = vaultRootUri,
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
                                    animationSpec =
                                        tween(
                                            durationMillis = 160,
                                            easing = LinearOutSlowInEasing,
                                        ),
                                    initialOffsetY = { fullHeight -> fullHeight },
                                ) +
                                    fadeIn(animationSpec = tween(durationMillis = 100)) +
                                    scaleIn(
                                        initialScale = 0.98f,
                                        animationSpec = tween(durationMillis = 160),
                                    )
                            }
                        },
                        exitTransition = {
                            val toEdit = targetState.destination.route?.startsWith("edit") == true
                            if (toEdit) {
                                ExitTransition.None
                            } else {
                                slideOutVertically(
                                    animationSpec =
                                        tween(
                                            durationMillis = 160,
                                            easing = FastOutLinearInEasing,
                                        ),
                                    targetOffsetY = { fullHeight -> fullHeight },
                                ) +
                                    fadeOut(animationSpec = tween(durationMillis = 100)) +
                                    scaleOut(
                                        targetScale = 0.98f,
                                        animationSpec = tween(durationMillis = 160),
                                    )
                            }
                        },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = {
                            slideOutVertically(
                                animationSpec =
                                    tween(
                                        durationMillis = 160,
                                        easing = FastOutLinearInEasing,
                                    ),
                                targetOffsetY = { fullHeight -> fullHeight },
                            ) +
                                fadeOut(animationSpec = tween(durationMillis = 100)) +
                                scaleOut(
                                    targetScale = 0.98f,
                                    animationSpec = tween(durationMillis = 160),
                                )
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
                            onOpenDoc = { rawUri, query, lineIndex ->
                                val uriParam2 = Uri.encode(rawUri)
                                val qParam2 = Uri.encode(query ?: "")
                                val lineParam2 = (lineIndex ?: -1).toString()
                                navController.navigate("edit?uri=$uriParam2&q=$qParam2&line=$lineParam2")
                            },
                            initialQuery = Uri.decode(qParam).takeIf { it.isNotBlank() },
                            initialLineIndex = lineParam.takeIf { it >= 0 },
                        )
                    }
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
private fun UserAvatarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 36.dp,
) {
    Surface(
        modifier = modifier.size(size).clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = "Z", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HomePager(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    prefs: VaultPreferences,
    repository: VaultRepository,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onOpenDoc: (String, String?, Int?) -> Unit,
    onNewDoc: () -> Unit,
    onChangeVault: () -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 0,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        when (page) {
            0 ->
                DocumentListScreen(
                    contentPadding = contentPadding,
                    vaultRootUri = vaultRootUri,
                    prefs = prefs,
                    repository = repository,
                    onOpenDoc = onOpenDoc,
                    onNewDoc = onNewDoc,
                    onChangeVault = onChangeVault,
                )

            1 ->
                TasksScreen(
                    contentPadding = contentPadding,
                    vaultRootUri = vaultRootUri,
                    repository = repository,
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
