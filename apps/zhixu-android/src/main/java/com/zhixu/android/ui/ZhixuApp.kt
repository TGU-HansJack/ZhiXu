package com.zhixu.android.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
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
import androidx.compose.ui.res.stringResource
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

    val showTopBar = currentRoute in setOf("docs", "tasks", "settings")

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (!showTopBar) return@Scaffold

                val items = listOf(
                    TopNavItem("docs", R.string.nav_docs),
                    TopNavItem("tasks", R.string.nav_tasks),
                    TopNavItem("settings", R.string.nav_settings),
                )
                TopAppBar(
                    windowInsets = TopAppBarDefaults.windowInsets,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (item in items) {
                                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(item.route) {
                                                launchSingleTop = true
                                                restoreState = true
                                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            }
                                        },
                                        label = { Text(stringResource(item.labelResId)) },
                                        colors =
                                            FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            ),
                                    )
                                }
                            }
                        }
                    },
                )
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = if (vaultRootUri == null) "vault" else "docs",
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
                            navController.navigate("docs") {
                                popUpTo("vault") { inclusive = true }
                            }
                        },
                        onEnsureVault = { uri -> repository.ensureVaultStructure(uri) },
                    )
                }
                composable("docs") {
                    DocumentListScreen(
                        contentPadding = padding,
                        vaultRootUri = vaultRootUri,
                        repository = repository,
                        onOpenDoc = { rawUri, query, lineIndex ->
                            val uriParam = Uri.encode(rawUri)
                            val qParam = Uri.encode(query ?: "")
                            val lineParam = (lineIndex ?: -1).toString()
                            navController.navigate("edit?uri=$uriParam&q=$qParam&line=$lineParam")
                        },
                        onNewDoc = {
                            navController.navigate("newDoc")
                        },
                        onChangeVault = {
                            scope.launch { prefs.setVaultRootUri(null) }
                            navController.navigate("vault") {
                                popUpTo("docs") { inclusive = true }
                            }
                        },
                    )
                }
                composable("tasks") {
                    TasksScreen(
                        contentPadding = padding,
                        vaultRootUri = vaultRootUri,
                        repository = repository,
                        onOpenDoc = { rawUri, query, lineIndex ->
                            val uriParam = Uri.encode(rawUri)
                            val qParam = Uri.encode(query ?: "")
                            val lineParam = (lineIndex ?: -1).toString()
                            navController.navigate("edit?uri=$uriParam&q=$qParam&line=$lineParam")
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
                                popUpTo("docs") { inclusive = false }
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

private data class TopNavItem(
    val route: String,
    val labelResId: Int,
)

private fun parseNavUri(param: String): Uri {
    if (param.isBlank()) return Uri.EMPTY
    val direct = Uri.parse(param)
    if (direct.scheme != null) return direct

    val decoded = Uri.decode(param)
    val decodedUri = Uri.parse(decoded)
    if (decodedUri.scheme != null) return decodedUri

    return direct
}
