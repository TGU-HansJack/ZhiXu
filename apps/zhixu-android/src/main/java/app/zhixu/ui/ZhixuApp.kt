package app.zhixu.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.os.LocaleListCompat
import coil.compose.AsyncImage
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.zhixu.R
import app.zhixu.data.AccountPreferences
import app.zhixu.data.AccountState
import app.zhixu.data.DocumentIndex
import app.zhixu.data.SearchResult
import app.zhixu.data.SyncPreferences
import app.zhixu.data.ThirdPartyServiceConfig
import app.zhixu.data.UiPreferences
import app.zhixu.data.VaultPreferences
import app.zhixu.data.VaultIndexUpdater
import app.zhixu.data.VaultRepository
import app.zhixu.data.VaultStorageLocation
import app.zhixu.data.VaultSyncConfig
import app.zhixu.data.VaultSyncPreferences
import app.zhixu.data.WebDavAutomationSettings
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
import app.zhixu.sync.WebDavAutoSyncStateStore
import app.zhixu.sync.WebDavSyncTaskManager
import app.zhixu.sync.WebDavSyncTaskTrigger
import app.zhixu.sync.OfficialSync
import app.zhixu.sync.SyncServerClient
import app.zhixu.sync.SyncServerSyncRuntime
import app.zhixu.sync.SyncServerStorageStats
import app.zhixu.ui.components.CreateDrawSheetContent
import app.zhixu.ui.components.CreateMenuSheetContent
import app.zhixu.ui.components.CreateQuickNewSheetContent
import app.zhixu.ui.components.HomeSubBar
import app.zhixu.ui.components.VaultSearchDialog
import app.zhixu.ui.components.ZhixuCompactDragHandle
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import app.zhixu.ui.components.ZhixuDialogDefaults
import app.zhixu.ui.screens.AccountManagementDialog
import app.zhixu.ui.screens.AccountScreen
import app.zhixu.ui.screens.AboutScreen
import app.zhixu.ui.screens.AiSettingsScreen
import app.zhixu.ui.screens.AuthScreen
import app.zhixu.ui.screens.CalendarTasksScreen
import app.zhixu.ui.screens.CameraCaptureScreen
import app.zhixu.ui.screens.DeviceManagementScreen
import app.zhixu.ui.screens.DocumentListScreen
import app.zhixu.ui.screens.DrawScreen
import app.zhixu.ui.screens.EditorScreen
import app.zhixu.ui.screens.HomeExtensionsScreen
import app.zhixu.ui.screens.ImagePreviewScreen
import app.zhixu.ui.screens.LongImageScreen
import app.zhixu.ui.screens.NewDocScreen
import app.zhixu.ui.screens.OpenSourceLicenseScreen
import app.zhixu.ui.screens.PlaceholderScreen
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class WebDavUiStatusLevel {
    DISABLED,
    UNKNOWN,
    OK,
    WARNING,
    ERROR,
}

private data class SyncResultSummary(
    val ok: Boolean,
    val endedAtMs: Long,
    val uploaded: Int,
    val downloaded: Int,
    val deletedRemote: Int,
    val deletedLocal: Int,
    val conflicts: Int,
    val failed: Int,
    val error: String?,
)

private data class WebDavUiStatusSnapshot(
    val level: WebDavUiStatusLevel,
    val text: String?,
    val endedAtMs: Long,
    val summary: SyncResultSummary?,
)

private enum class SyncServerUiStatusLevel {
    DISABLED,
    UNKNOWN,
    OK,
    WARNING,
    ERROR,
}

private data class SyncServerUiStatusSnapshot(
    val level: SyncServerUiStatusLevel,
    val text: String?,
    val endedAtMs: Long,
    val summary: SyncResultSummary?,
)

private enum class CloudSyncUiIconState {
    SYNCING,
    OK,
    WARNING,
    DISABLED,
}

private enum class CloudSyncDialogState {
    SYNCING,
    OK,
    CHANGED,
    ISSUE,
    FAILED,
    DISABLED,
    NOT_CONFIGURED,
    NOT_LOGGED_IN,
}

private data class WebDavAutoSyncStatusSnapshot(
    val enabled: Boolean,
    val nextAllowedAtMs: Long,
    val lastSucceededAtMs: Long,
    val lastAttemptedAtMs: Long,
    val lastError: String?,
    val message: String,
)

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
    val pluginRepo = remember(appContext) { app.zhixu.plugins.PluginRepository(appContext) }
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
        initial = AccountState(token = "", username = "", userId = 0L, email = "", avatarUri = "", avatarUpdatedAtMs = 0L),
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
                conflictStrategy = app.zhixu.data.WebDavConflictStrategy.KEEP_BOTH,
            ),
    )
    val webDavRemoteRootConfirmed by syncPrefs.webDavRemoteRootConfirmed.collectAsState(initial = false)
    val webDavAutomationSettings by syncPrefs.webDavAutomationSettings.collectAsState(initial = WebDavAutomationSettings.DEFAULT)
    val webDavAutoSyncEnabled by syncPrefs.webDavAutoSyncEnabled.collectAsState(initial = false)
    val officialSyncEnabled by syncPrefs.officialSyncEnabled.collectAsState(initial = false)
    var showCloudSyncStatusDialog by remember { mutableStateOf(false) }
    var webDavUiStatus by remember { mutableStateOf<WebDavUiStatusSnapshot?>(null) }
    var webDavAutoSyncStatus by remember { mutableStateOf<WebDavAutoSyncStatusSnapshot?>(null) }
    var syncServerUiStatus by remember { mutableStateOf<SyncServerUiStatusSnapshot?>(null) }
    var syncServerLatestLogLoading by remember { mutableStateOf(false) }
    var syncServerLatestLogText by remember { mutableStateOf<String?>(null) }
    var syncServerLatestLogAtMs by remember { mutableStateOf(0L) }
    var officialStorageStats by remember { mutableStateOf<SyncServerStorageStats?>(null) }
    var officialStorageStatsLoading by remember { mutableStateOf(false) }
    var officialStorageStatsError by remember { mutableStateOf<String?>(null) }

    var webDavSyncing by remember { mutableStateOf(false) }
    val syncServerRunningCount by SyncServerSyncRuntime.runningCount.collectAsState(initial = 0)
    val syncServerSyncing = syncServerRunningCount > 0

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
    val pinnedDocUris by
        remember(vaultRootUriString) {
            val root = vaultRootUri
            if (root == null) kotlinx.coroutines.flow.flowOf(emptyList()) else prefs.pinnedDocUris(root)
        }.collectAsState(initial = emptyList())

    fun formatEpochMs(ms: Long): String {
        if (ms <= 0L) return "-"
        return runCatching {
            Instant
                .ofEpochMilli(ms)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }.getOrElse { ms.toString() }
    }

    fun formatEpochMsSmart(ms: Long): String {
        if (ms <= 0L) return "-"
        return runCatching {
            val zone = ZoneId.systemDefault()
            val dt = Instant.ofEpochMilli(ms).atZone(zone)
            val today = LocalDate.now(zone)
            if (dt.toLocalDate() == today) {
                dt.format(DateTimeFormatter.ofPattern("HH:mm"))
            } else {
                dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            }
        }.getOrElse { ms.toString() }
    }

    fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L).toDouble()
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        var value = safe
        var idx = 0
        while (value >= 1024.0 && idx < units.size - 1) {
            value /= 1024.0
            idx++
        }
        return if (idx == 0) {
            "${value.toInt()} ${units[idx]}"
        } else {
            val dp = if (value < 10) 2 else 1
            String.format("%.${dp}f %s", value, units[idx])
        }
    }

    suspend fun reloadWebDavUiStatus() {
        val root = vaultRootUri ?: run {
            webDavUiStatus = null
            return
        }
        val uri = repository.resolveVaultFileUri(root, ".zhixu/sync/webdav_last_summary.json")
        val raw = uri?.let { runCatching { repository.readText(it) }.getOrNull().orEmpty().trim() }.orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrNull()
        val ok = obj?.optBoolean("ok", false) ?: false
        val endedAt = obj?.optLong("endedAt", 0L) ?: 0L
        val conflicts = obj?.optInt("conflicts", 0) ?: 0
        val failed = obj?.optInt("failed", 0) ?: 0
        val error = obj?.optString("error").orEmpty().trim().ifBlank { null }
        val uploaded = obj?.optInt("uploaded", 0) ?: 0
        val downloaded = obj?.optInt("downloaded", 0) ?: 0
        val deletedRemote = obj?.optInt("deletedRemote", 0) ?: 0
        val deletedLocal = obj?.optInt("deletedLocal", 0) ?: 0
        val summary =
            if (raw.isBlank()) {
                null
            } else {
                SyncResultSummary(
                    ok = ok,
                    endedAtMs = endedAt,
                    uploaded = if (ok) uploaded else 0,
                    downloaded = if (ok) downloaded else 0,
                    deletedRemote = if (ok) deletedRemote else 0,
                    deletedLocal = if (ok) deletedLocal else 0,
                    conflicts = conflicts,
                    failed = failed,
                    error = if (!ok) error else null,
                )
            }
        val text =
            if (raw.isBlank()) {
                null
            } else if (!ok) {
                context.getString(R.string.webdav_sync_failed, error ?: "-")
            } else {
                context.getString(R.string.webdav_sync_ok_v2, uploaded, downloaded, deletedRemote, deletedLocal, conflicts, failed) +
                    " · " +
                    formatEpochMs(endedAt)
            }
        val level =
            when {
                !webDavConfig.enabled -> WebDavUiStatusLevel.DISABLED
                text.isNullOrBlank() -> WebDavUiStatusLevel.UNKNOWN
                !ok -> WebDavUiStatusLevel.ERROR
                conflicts > 0 || failed > 0 -> WebDavUiStatusLevel.WARNING
                else -> WebDavUiStatusLevel.OK
            }
        webDavUiStatus = WebDavUiStatusSnapshot(level = level, text = text, endedAtMs = endedAt, summary = summary)
    }

    suspend fun reloadSyncServerUiStatus() {
        val root = vaultRootUri ?: run {
            syncServerUiStatus = null
            return
        }

        val cfg = vaultSyncConfig
        val enabled =
            when (cfg.location) {
                VaultStorageLocation.LOCAL -> officialSyncEnabled && accountState.token.isNotBlank()
                VaultStorageLocation.OFFICIAL_SERVER -> accountState.token.isNotBlank()
                VaultStorageLocation.THIRD_PARTY_SERVICE -> {
                    val tp = cfg.thirdParty
                    tp.url.trim().isNotBlank() && tp.username.trim().isNotBlank() && tp.password.isNotBlank()
                }
            }

        val uri = repository.resolveVaultFileUri(root, ".zhixu/sync/server_last_summary.json")
        val raw0 = uri?.let { runCatching { repository.readText(it) }.getOrNull().orEmpty().trim() }.orEmpty()
        var raw = raw0
        var obj = runCatching { JSONObject(raw) }.getOrNull()

        val expectedBaseUrl =
            when (cfg.location) {
                VaultStorageLocation.LOCAL, VaultStorageLocation.OFFICIAL_SERVER -> OfficialSync.BASE_URL
                VaultStorageLocation.THIRD_PARTY_SERVICE -> cfg.thirdParty.url.trim()
            }.trim()
        val recordBaseUrl = obj?.optString("baseUrl").orEmpty().trim()
        if (recordBaseUrl.isNotBlank() && expectedBaseUrl.isNotBlank() && !recordBaseUrl.equals(expectedBaseUrl, ignoreCase = true)) {
            raw = ""
            obj = null
        }
        val ok = obj?.optBoolean("ok", false) ?: false
        val endedAt = obj?.optLong("endedAt", 0L) ?: 0L
        val conflicts = obj?.optInt("conflicts", 0) ?: 0
        val failed = obj?.optInt("failed", 0) ?: 0
        val error = obj?.optString("error").orEmpty().trim().ifBlank { null }
        val uploaded = obj?.optInt("uploaded", 0) ?: 0
        val downloaded = obj?.optInt("downloaded", 0) ?: 0
        val deletedRemote = obj?.optInt("deletedRemote", 0) ?: 0
        val deletedLocal = obj?.optInt("deletedLocal", 0) ?: 0
        val summary =
            if (raw.isBlank()) {
                null
            } else {
                SyncResultSummary(
                    ok = ok,
                    endedAtMs = endedAt,
                    uploaded = if (ok) uploaded else 0,
                    downloaded = if (ok) downloaded else 0,
                    deletedRemote = if (ok) deletedRemote else 0,
                    deletedLocal = if (ok) deletedLocal else 0,
                    conflicts = conflicts,
                    failed = failed,
                    error = if (!ok) error else null,
                )
            }
        val text =
            if (raw.isBlank()) {
                null
            } else if (!ok) {
                context.getString(R.string.official_sync_failed, error ?: "-")
            } else {
                context.getString(R.string.official_sync_ok, uploaded, downloaded, deletedRemote, deletedLocal, conflicts, failed) +
                    " · " +
                    formatEpochMs(endedAt)
            }

        val enabledEffective =
            when (cfg.location) {
                VaultStorageLocation.LOCAL -> accountState.token.isNotBlank() && (officialSyncEnabled || raw.isNotBlank())
                else -> enabled
            }
        val level =
            when {
                !enabledEffective && raw.isBlank() -> SyncServerUiStatusLevel.DISABLED
                text.isNullOrBlank() -> SyncServerUiStatusLevel.UNKNOWN
                !ok -> SyncServerUiStatusLevel.ERROR
                conflicts > 0 || failed > 0 -> SyncServerUiStatusLevel.WARNING
                else -> SyncServerUiStatusLevel.OK
            }
        syncServerUiStatus = SyncServerUiStatusSnapshot(level = level, text = text, endedAtMs = endedAt, summary = summary)
    }

    suspend fun reloadSyncServerLatestLogForDialog() {
        // Account sync logs are only supported on the official server endpoint.
        if (vaultSyncConfig.location == VaultStorageLocation.THIRD_PARTY_SERVICE) {
            syncServerLatestLogLoading = false
            syncServerLatestLogText = null
            syncServerLatestLogAtMs = 0L
            return
        }

        val token = accountState.token.trim()
        if (token.isBlank()) {
            syncServerLatestLogLoading = false
            syncServerLatestLogText = null
            syncServerLatestLogAtMs = 0L
            return
        }

        syncServerLatestLogLoading = true
        try {
            val res = SyncServerClient.listSyncLogs(OfficialSync.BASE_URL, token = token, limit = 1)
            val log = res.value?.firstOrNull()
            if (log != null) {
                syncServerLatestLogText = SyncLogUi.formatLatestStatus(context, log)
                syncServerLatestLogAtMs = log.createdAtMs
                return
            }
            syncServerLatestLogText =
                when {
                    res.statusCode == 0 || res.errorMessage == "NETWORK_UNREACHABLE" -> context.getString(R.string.error_server_unreachable)
                    !res.errorMessage.isNullOrBlank() -> res.errorMessage
                    else -> null
                }
            syncServerLatestLogAtMs = 0L
        } catch (e: Throwable) {
            syncServerLatestLogText = e.message ?: e.javaClass.simpleName
            syncServerLatestLogAtMs = 0L
        } finally {
            syncServerLatestLogLoading = false
        }
    }

    suspend fun reloadOfficialStorageStatsForDialog() {
        val enabled =
            vaultSyncConfig.location == VaultStorageLocation.OFFICIAL_SERVER ||
                (vaultSyncConfig.location == VaultStorageLocation.LOCAL && officialSyncEnabled)
        if (!enabled || accountState.token.isBlank()) {
            officialStorageStats = null
            officialStorageStatsError = null
            officialStorageStatsLoading = false
            return
        }

        officialStorageStatsLoading = true
        officialStorageStatsError = null
        try {
            val res = SyncServerClient.storageStats(OfficialSync.BASE_URL, token = accountState.token)
            officialStorageStats = res.value
            if (!res.ok) officialStorageStatsError = res.errorMessage
        } catch (e: Throwable) {
            officialStorageStatsError = e.message ?: e.javaClass.simpleName
        } finally {
            officialStorageStatsLoading = false
        }
    }

    suspend fun reloadWebDavAutoSyncStatusForDialog() {
        val root = vaultRootUri ?: run {
            webDavAutoSyncStatus =
                WebDavAutoSyncStatusSnapshot(
                    enabled = false,
                    nextAllowedAtMs = 0L,
                    lastSucceededAtMs = 0L,
                    lastAttemptedAtMs = 0L,
                    lastError = null,
                    message = context.getString(R.string.webdav_autosync_status_no_vault),
                )
            return
        }
        if (!webDavConfig.enabled || !webDavAutoSyncEnabled) {
            webDavAutoSyncStatus =
                WebDavAutoSyncStatusSnapshot(
                    enabled = false,
                    nextAllowedAtMs = 0L,
                    lastSucceededAtMs = 0L,
                    lastAttemptedAtMs = 0L,
                    lastError = null,
                    message = context.getString(R.string.cloud_sync_dialog_autosync_off),
                )
            return
        }
        val baseUrl = webDavConfig.baseUrl.trim()
        val remoteRoot = webDavConfig.remoteRoot.trim().ifBlank { "/" }
        if (baseUrl.isBlank()) {
            webDavAutoSyncStatus =
                WebDavAutoSyncStatusSnapshot(
                    enabled = false,
                    nextAllowedAtMs = 0L,
                    lastSucceededAtMs = 0L,
                    lastAttemptedAtMs = 0L,
                    lastError = null,
                    message = context.getString(R.string.webdav_autosync_status_empty_url),
                )
            return
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            webDavAutoSyncStatus =
                WebDavAutoSyncStatusSnapshot(
                    enabled = false,
                    nextAllowedAtMs = 0L,
                    lastSucceededAtMs = 0L,
                    lastAttemptedAtMs = 0L,
                    lastError = null,
                    message = context.getString(R.string.webdav_autosync_status_invalid_url),
                )
            return
        }
        val key = "$baseUrl|$remoteRoot|$root"
        val store = WebDavAutoSyncStateStore(appContext)
        val state = store.get(key)
        val intervalMs = (webDavAutomationSettings.intervalMinutes.toLong() * 60_000L).coerceAtLeast(60_000L)
        val nextAllowed = state.lastAttemptedAtMs + intervalMs
        webDavAutoSyncStatus =
            WebDavAutoSyncStatusSnapshot(
                enabled = true,
                nextAllowedAtMs = nextAllowed,
                lastSucceededAtMs = state.lastSucceededAtMs,
                lastAttemptedAtMs = state.lastAttemptedAtMs,
                lastError = state.lastError,
                message = context.getString(R.string.cloud_sync_dialog_autosync_on_next_fmt, formatEpochMsSmart(nextAllowed)),
            )
    }

    LaunchedEffect(vaultRootUriString, webDavConfig.enabled) {
        reloadWebDavUiStatus()
    }

    LaunchedEffect(
        vaultRootUriString,
        vaultSyncConfig.location,
        accountState.token,
        officialSyncEnabled,
        vaultSyncConfig.thirdParty.url,
        vaultSyncConfig.thirdParty.username,
        vaultSyncConfig.thirdParty.password,
    ) {
        reloadSyncServerUiStatus()
    }

    LaunchedEffect(
        vaultRootUriString,
        vaultSyncConfig.location,
        accountState.token,
        officialSyncEnabled,
        vaultSyncConfig.thirdParty.url,
        vaultSyncConfig.thirdParty.username,
        vaultSyncConfig.thirdParty.password,
    ) {
        var wasSyncing = false
        SyncServerSyncRuntime.runningCount.collect { count ->
            val syncing = count > 0
            if (wasSyncing && !syncing) {
                reloadSyncServerUiStatus()
                // Avoid hitting the network unless the user is actively viewing the sync dialog.
                if (showCloudSyncStatusDialog) {
                    reloadSyncServerLatestLogForDialog()
                }
            }
            wasSyncing = syncing
        }
    }

    LaunchedEffect(
        showCloudSyncStatusDialog,
        vaultRootUriString,
        webDavConfig.enabled,
        webDavConfig.baseUrl,
        webDavConfig.remoteRoot,
        webDavAutomationSettings.intervalMinutes,
        webDavAutomationSettings.retryCount,
        webDavAutomationSettings.retryIntervalSeconds,
        vaultSyncConfig.location,
        accountState.token,
        officialSyncEnabled,
        vaultSyncConfig.thirdParty.url,
        vaultSyncConfig.thirdParty.username,
        vaultSyncConfig.thirdParty.password,
    ) {
        if (!showCloudSyncStatusDialog) return@LaunchedEffect
        reloadWebDavUiStatus()
        reloadWebDavAutoSyncStatusForDialog()
        reloadSyncServerUiStatus()
        reloadSyncServerLatestLogForDialog()
        reloadOfficialStorageStatsForDialog()
    }

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

    // Minimal "auto pull" for sync-server (official server) sync:
    // - Trigger a two-way sync when the app returns to foreground
    // - Trigger a two-way sync when network connectivity comes back
    //
    // This uses existing `serverCursor`/state in `OfficialVaultSyncEngine.syncVault()` (no protocol changes).
    val lifecycleOwner = LocalLifecycleOwner.current
    var syncServerForegroundPulse by remember { mutableLongStateOf(0L) }
    var syncServerNetworkPulse by remember { mutableLongStateOf(0L) }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event != Lifecycle.Event.ON_START) return@LifecycleEventObserver
                syncServerForegroundPulse += 1L
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(appContext) {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) return@DisposableEffect onDispose { }

        val handler = Handler(Looper.getMainLooper())
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // Network callbacks are not guaranteed to be on main; hop before mutating Compose state.
                    handler.post { syncServerNetworkPulse += 1L }
                }
            }

        val registered =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    cm.registerDefaultNetworkCallback(callback)
                } else {
                    cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
                }
            }.isSuccess

        onDispose {
            if (registered) runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }

    suspend fun maybeAutoPullSyncServer() {
        // `SyncServerSyncRuntime` is used by both per-file sync (auto upload/delete) and full sync.
        // Avoid starting a full sync if any sync-server operation is already running.
        if (syncServerSyncing) return

        // Keep current semantics: in LOCAL mode the user must explicitly enable official sync.
        if (vaultSyncConfig.location == VaultStorageLocation.LOCAL && !officialSyncEnabled) return
        // Minimal scope for now: only official server mode(s).
        if (vaultSyncConfig.location != VaultStorageLocation.LOCAL &&
            vaultSyncConfig.location != VaultStorageLocation.OFFICIAL_SERVER
        ) {
            return
        }

        // Official server auth currently depends on the account token.
        if (accountState.token.isBlank()) return

        val root = vaultRootUri ?: return
        try {
            runCatching {
                VaultAutoSync.maybeSyncVault(
                    context = context,
                    repository = repository,
                    vaultRootUri = root,
                    force = false,
                )
            }
        } finally {
            // Always refresh UI status snapshot after any attempt.
            reloadSyncServerUiStatus()
        }
    }

    LaunchedEffect(syncServerForegroundPulse) {
        if (syncServerForegroundPulse == 0L) return@LaunchedEffect
        maybeAutoPullSyncServer()
    }

    LaunchedEffect(syncServerNetworkPulse) {
        if (syncServerNetworkPulse == 0L) return@LaunchedEffect
        maybeAutoPullSyncServer()
    }

    LaunchedEffect(
        vaultRootUriString,
        vaultSyncConfig.location,
        accountState.token,
        officialSyncEnabled,
        vaultSyncConfig.thirdParty.url,
        vaultSyncConfig.thirdParty.username,
        vaultSyncConfig.thirdParty.password,
    ) {
        maybeAutoPullSyncServer()
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
        webDavAutoSyncEnabled,
        webDavAutomationSettings.intervalMinutes,
        webDavAutomationSettings.retryCount,
        webDavAutomationSettings.retryIntervalSeconds,
    ) {
        if (vaultSyncConfig.location != VaultStorageLocation.LOCAL) return@LaunchedEffect
        if (!webDavConfig.enabled) return@LaunchedEffect
        if (!webDavAutoSyncEnabled) return@LaunchedEffect
        val intervalMs = (webDavAutomationSettings.intervalMinutes.toLong() * 60_000L).coerceAtLeast(60_000L)
        while (true) {
            val root = vaultRootUri
            if (root != null) {
                webDavSyncing = true
                try {
                    runCatching {
                        WebDavAutoSync.maybeSyncVault(
                            context = context,
                            repository = repository,
                            vaultRootUri = root,
                            config = webDavConfig,
                            automation = webDavAutomationSettings,
                            force = false,
                        )
                    }
                } finally {
                    webDavSyncing = false
                    reloadWebDavUiStatus()
                }
            }
            delay(intervalMs)
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    if (showCloudSyncStatusDialog) {
        val dialogScope = rememberCoroutineScope()
        var manualSyncServerWorking by remember { mutableStateOf(false) }
        val syncServerPrimaryLocation =
            when (vaultSyncConfig.location) {
                VaultStorageLocation.THIRD_PARTY_SERVICE -> VaultStorageLocation.THIRD_PARTY_SERVICE
                else -> VaultStorageLocation.OFFICIAL_SERVER
            }
        val primaryLocation =
            when {
                webDavSyncing -> VaultStorageLocation.LOCAL
                syncServerSyncing -> syncServerPrimaryLocation
                vaultSyncConfig.location != VaultStorageLocation.LOCAL -> vaultSyncConfig.location
                webDavConfig.enabled -> VaultStorageLocation.LOCAL
                else -> if (officialSyncEnabled) VaultStorageLocation.OFFICIAL_SERVER else VaultStorageLocation.LOCAL
            }
        val isPrimaryWebDav = primaryLocation == VaultStorageLocation.LOCAL
        val primaryProviderName =
            when (primaryLocation) {
                VaultStorageLocation.LOCAL -> stringResource(R.string.settings_sync_provider_webdav)
                VaultStorageLocation.OFFICIAL_SERVER -> stringResource(R.string.official_sync_title)
                VaultStorageLocation.THIRD_PARTY_SERVICE -> stringResource(R.string.vault_settings_location_third_party)
            }

        val primarySummary = if (isPrimaryWebDav) webDavUiStatus?.summary else syncServerUiStatus?.summary
        val primarySyncing = if (isPrimaryWebDav) webDavSyncing else syncServerSyncing
        val primaryEnabled =
            if (isPrimaryWebDav) {
                webDavConfig.enabled
            } else {
                when (primaryLocation) {
                    VaultStorageLocation.LOCAL -> false
                    VaultStorageLocation.OFFICIAL_SERVER ->
                        accountState.token.isNotBlank() &&
                            (vaultSyncConfig.location == VaultStorageLocation.OFFICIAL_SERVER || officialSyncEnabled)
                    VaultStorageLocation.THIRD_PARTY_SERVICE -> {
                        val tp = vaultSyncConfig.thirdParty
                        tp.url.trim().isNotBlank() && tp.username.trim().isNotBlank() && tp.password.isNotBlank()
                    }
                }
            }

        val state: CloudSyncDialogState =
            when {
                primarySyncing -> CloudSyncDialogState.SYNCING
                !primaryEnabled && !isPrimaryWebDav && primaryLocation == VaultStorageLocation.OFFICIAL_SERVER && accountState.token.isBlank() ->
                    CloudSyncDialogState.NOT_LOGGED_IN
                !primaryEnabled -> CloudSyncDialogState.DISABLED
                primarySummary == null -> CloudSyncDialogState.NOT_CONFIGURED
                !primarySummary.ok -> CloudSyncDialogState.FAILED
                primarySummary.conflicts > 0 || primarySummary.failed > 0 -> CloudSyncDialogState.ISSUE
                primarySummary.uploaded + primarySummary.downloaded + primarySummary.deletedRemote + primarySummary.deletedLocal > 0 -> CloudSyncDialogState.CHANGED
                else -> CloudSyncDialogState.OK
            }

        val stateLabel =
            when (state) {
                CloudSyncDialogState.SYNCING -> stringResource(R.string.cloud_sync_state_syncing)
                CloudSyncDialogState.OK -> stringResource(R.string.cloud_sync_state_ok)
                CloudSyncDialogState.CHANGED -> stringResource(R.string.cloud_sync_state_changed)
                CloudSyncDialogState.ISSUE -> stringResource(R.string.cloud_sync_state_issue)
                CloudSyncDialogState.FAILED -> stringResource(R.string.cloud_sync_state_failed)
                CloudSyncDialogState.DISABLED -> stringResource(R.string.cloud_sync_status_disabled)
                CloudSyncDialogState.NOT_CONFIGURED -> stringResource(R.string.cloud_sync_status_not_configured)
                CloudSyncDialogState.NOT_LOGGED_IN -> stringResource(R.string.account_not_logged_in_short)
            }

        val chipColor =
            when (state) {
                CloudSyncDialogState.SYNCING -> Color(0xFF3B82F6)
                CloudSyncDialogState.OK -> Color(0xFF22C55E)
                CloudSyncDialogState.CHANGED -> Color(0xFF22C55E)
                CloudSyncDialogState.ISSUE -> Color(0xFFF59E0B)
                CloudSyncDialogState.FAILED -> Color(0xFFEF4444)
                CloudSyncDialogState.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
                CloudSyncDialogState.NOT_CONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant
                CloudSyncDialogState.NOT_LOGGED_IN -> MaterialTheme.colorScheme.onSurfaceVariant
            }

        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { showCloudSyncStatusDialog = false },
            properties = ZhixuDialogDefaults.properties,
            title = {
                Text(stringResource(R.string.cloud_sync_title))
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val root = vaultRootUri

                    val webDavBaseUrl = webDavConfig.baseUrl.trim()
                    val webDavReady =
                        root != null &&
                            webDavConfig.enabled &&
                            webDavRemoteRootConfirmed &&
                            webDavBaseUrl.isNotBlank() &&
                            (webDavBaseUrl.startsWith("http://") || webDavBaseUrl.startsWith("https://")) &&
                            !webDavSyncing

                    val officialEnabledForManual =
                        when (vaultSyncConfig.location) {
                            VaultStorageLocation.LOCAL -> officialSyncEnabled
                            VaultStorageLocation.OFFICIAL_SERVER -> true
                            else -> false
                        }

                    val officialReady =
                        root != null &&
                            accountState.token.isNotBlank() &&
                            officialEnabledForManual &&
                            !syncServerSyncing &&
                            !manualSyncServerWorking

                    val webDavStatus =
                        when {
                            root == null -> stringResource(R.string.cloud_sync_status_not_configured)
                            webDavSyncing -> stringResource(R.string.cloud_sync_state_syncing)
                            !webDavConfig.enabled -> stringResource(R.string.cloud_sync_status_disabled)
                            !webDavRemoteRootConfirmed -> stringResource(R.string.cloud_sync_status_not_configured)
                            !webDavUiStatus?.text.isNullOrBlank() -> webDavUiStatus?.text.orEmpty()
                            else -> stringResource(R.string.cloud_sync_dialog_no_record)
                        }

                    val officialStatus =
                        when {
                            root == null -> stringResource(R.string.cloud_sync_status_not_configured)
                            syncServerSyncing -> stringResource(R.string.cloud_sync_state_syncing)
                            accountState.token.isBlank() -> stringResource(R.string.account_not_logged_in_short)
                            vaultSyncConfig.location == VaultStorageLocation.THIRD_PARTY_SERVICE ->
                                syncServerUiStatus?.text?.ifBlank { null } ?: stringResource(R.string.cloud_sync_dialog_no_record)
                            syncServerLatestLogLoading -> stringResource(R.string.common_loading)
                            !syncServerLatestLogText.isNullOrBlank() -> syncServerLatestLogText!!
                            vaultSyncConfig.location == VaultStorageLocation.LOCAL && !officialSyncEnabled ->
                                stringResource(R.string.cloud_sync_dialog_official_hint_disabled)
                            else -> stringResource(R.string.cloud_sync_dialog_no_record)
                        }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.cloud_sync_dialog_webdav_status_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = webDavStatus.ifBlank { "-" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    enabled = webDavReady,
                                    onClick = {
                                        val r = root ?: return@TextButton
                                        dialogScope.launch {
                                            webDavSyncing = true
                                            try {
                                                val baseUrl = webDavConfig.baseUrl.trim()
                                                val remoteRoot = webDavConfig.remoteRoot.trim().ifBlank { "/" }
                                                val normalizedConfig =
                                                    webDavConfig.copy(
                                                        enabled = true,
                                                        baseUrl = baseUrl,
                                                        username = webDavConfig.username.trim(),
                                                        remoteRoot = remoteRoot,
                                                    )

                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        val manager = WebDavSyncTaskManager(context, repository)
                                                        manager.generateTask(
                                                            rootUri = r,
                                                            config = normalizedConfig,
                                                            trigger = WebDavSyncTaskTrigger.MANUAL,
                                                            onlyPaths = null,
                                                        )
                                                        val currentTask = manager.load(r).current
                                                        if (currentTask != null) {
                                                            manager.executeCurrentTask(r, normalizedConfig)
                                                        }
                                                    }
                                                } catch (_: Throwable) {
                                                }
                                            } finally {
                                                webDavSyncing = false
                                                reloadWebDavUiStatus()
                                                reloadWebDavAutoSyncStatusForDialog()
                                            }
                                        }
                                    },
                                ) { Text(stringResource(R.string.cloud_sync_dialog_sync_now)) }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.cloud_sync_dialog_official_status_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = officialStatus.ifBlank { "-" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    enabled = officialReady,
                                    onClick = {
                                        val r = root ?: return@TextButton
                                        dialogScope.launch {
                                            manualSyncServerWorking = true
                                            try {
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        VaultAutoSync.maybeSyncVault(
                                                            context = context,
                                                            repository = repository,
                                                            vaultRootUri = r,
                                                            force = true,
                                                        )
                                                    }
                                                }
                                            } finally {
                                                manualSyncServerWorking = false
                                                reloadSyncServerUiStatus()
                                            }
                                        }
                                    },
                                ) { Text(stringResource(R.string.cloud_sync_dialog_sync_now)) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloudSyncStatusDialog = false
                        navController.navigate("settingsMain")
                    },
                ) { Text(stringResource(R.string.cloud_sync_dialog_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showCloudSyncStatusDialog = false }) { Text(stringResource(R.string.cloud_sync_dialog_cancel)) }
            },
        )
    }

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
    var docSortFilterRequestToken by remember { mutableLongStateOf(0L) }
    var meRefreshToken by remember { mutableLongStateOf(0L) }
    var dirStructureMutationToken by remember { mutableLongStateOf(0L) }
    var dirStructureMutation by remember { mutableStateOf<DocListMutation?>(null) }

    var globalSearchOpen by remember { mutableStateOf(false) }
    var globalSearchQuery by remember { mutableStateOf("") }
    var globalSearchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var globalSearchJob by remember { mutableStateOf<Job?>(null) }
    val globalSearchHighlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    var selectedDocUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedTasks by remember { mutableStateOf<Set<TaskKey>>(emptySet()) }
    var selectedSpaceEntryUris by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun clearGlobalSearch() {
        globalSearchQuery = ""
        globalSearchResults = emptyList()
        globalSearchJob?.cancel()
        globalSearchJob = null
    }

    fun updateGlobalSearchQuery(newQuery: String) {
        globalSearchQuery = newQuery
        globalSearchJob?.cancel()
        globalSearchJob =
            scope.launch {
                delay(200)
                if (globalSearchQuery.isBlank()) {
                    globalSearchResults = emptyList()
                    return@launch
                }
                globalSearchResults =
                    withContext(Dispatchers.IO) {
                        repository.search(rootUri = null, query = globalSearchQuery).take(50)
                    }
            }
    }

    fun <T> toggleSelection(current: Set<T>, item: T): Set<T> = if (item in current) current - item else current + item

    fun onDocListMutated(mutation: DocListMutation) {
        dirStructureMutation = mutation
        dirStructureMutationToken += 1L
    }

    fun clearAllSelections() {
        selectedDocUris = emptySet()
        selectedTasks = emptySet()
        selectedSpaceEntryUris = emptySet()
    }

    val showTopBar = currentRoute in setOf("home", "tasks", "pomodoro")
    val showBottomBar = currentRoute in setOf("home", "tasks", "pomodoro", "me")
    val docsSelectionMode = currentRoute == "home" && selectedDocUris.isNotEmpty()
    val pinnedDocUriSet = remember(pinnedDocUris) { pinnedDocUris.toHashSet() }
    val docsSelectionAllPinned = docsSelectionMode && selectedDocUris.all { it in pinnedDocUriSet }

    val homePagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { 3 })
    val settledHomePage by remember { derivedStateOf { homePagerState.settledPage } }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val homeBarHeightPx = remember(density) { with(density) { ZhixuTopBarContentHeight.toPx() } }
    val homeBarDividerHeightPx = remember(density) { with(density) { 1.dp.toPx() } }
    // Negative = moved up (hidden). Range is [-(topBar + subBar + dividers), 0].
    // Collapse order: sub-bar first, then top bar (expand in reverse).
    var homeBarsOffsetPx by remember { mutableFloatStateOf(0f) }
    val homeCollapsingEnabledState = rememberUpdatedState(currentRoute == "home" && !docsSelectionMode)

    val homeBarsNestedScrollConnection =
        remember(homeBarHeightPx, homeBarDividerHeightPx) {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                private fun consume(deltaY: Float): Float {
                    if (!homeCollapsingEnabledState.value) return 0f
                    if (deltaY == 0f) return 0f

                    val minOffset = -(homeBarHeightPx * 2f + homeBarDividerHeightPx * 2f)
                    val prev = homeBarsOffsetPx
                    val next = (prev + deltaY).coerceIn(minOffset, 0f)
                    val consumed = next - prev
                    homeBarsOffsetPx = next
                    return consumed
                }

                override fun onPreScroll(
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                ): androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset(x = 0f, y = consume(available.y))

                override fun onPostScroll(
                    consumed: androidx.compose.ui.geometry.Offset,
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                ): androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset(x = 0f, y = consume(available.y))
            }
        }

    LaunchedEffect(currentRoute, docsSelectionMode) {
        // Keep Home app bars visible when leaving Home or entering selection mode.
        if (currentRoute != "home" || docsSelectionMode) {
            homeBarsOffsetPx = 0f
        }
    }

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

    fun navigateSpace() {
        navController.navigate("space") {
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

    val configuration = LocalConfiguration.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerWidth = configuration.screenWidthDp.dp * (2f / 3f)
    val showMainUi = currentRoute in setOf("home", "tasks", "pomodoro", "space")
    var docListUseGrid by rememberSaveable(vaultRootUriString) { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showMainUi,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(drawerWidth),
                windowInsets = WindowInsets(0, 0, 0, 0),
                drawerContainerColor = MaterialTheme.colorScheme.background,
                drawerTonalElevation = 0.dp,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 8.dp),
                ) {
                    val drawerLeadingSize = 42.dp
                    val drawerIconSize = 20.dp

                    ListItem(
                        leadingContent = {
                            val uri = accountState.avatarUri
                            Box(
                                modifier =
                                    Modifier
                                        .size(drawerLeadingSize)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (uri.isNotBlank()) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Text(
                                        text = accountState.username.firstOrNull()?.uppercase() ?: "Z",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        headlineContent = {
                            Text(
                                text =
                                    if (accountState.isLoggedIn) {
                                        accountState.username.ifBlank { stringResource(R.string.account_manage_title) }
                                    } else {
                                        stringResource(R.string.account_not_logged_in_short)
                                    },
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            val email =
                                if (accountState.isLoggedIn) {
                                    accountState.email.ifBlank { stringResource(R.string.account_login_register) }
                                } else {
                                    stringResource(R.string.account_login_register)
                                }
                            Text(
                                text = email,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Icon(
                                painter = painterResource(Ionicons.ChevronForward),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate("account")
                                    }
                                },
                    )
                    ListItem(
                        leadingContent = {
                            Box(
                                modifier = Modifier.size(drawerLeadingSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(Ionicons.Workshop),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(drawerIconSize),
                                )
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.settings_section_workshop)) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate("workshop")
                                    }
                                },
                    )
                    ListItem(
                        leadingContent = {
                            Box(
                                modifier = Modifier.size(drawerLeadingSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(Ionicons.SettingsOutline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(drawerIconSize),
                                )
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.nav_settings)) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate("settingsMain")
                                    }
                                },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))

                    ListItem(
                        leadingContent = {
                            Box(
                                modifier = Modifier.size(drawerLeadingSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(Ionicons.ChartPie),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(drawerIconSize),
                                )
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.drawer_stats)) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate("stats")
                                    }
                                },
                    )
                    ListItem(
                        leadingContent = {
                            Box(
                                modifier = Modifier.size(drawerLeadingSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(Ionicons.HelpCircleOutline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(drawerIconSize),
                                )
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.drawer_help_support)) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate("helpSupport")
                                    }
                                },
                    )
                    ListItem(
                        leadingContent = {
                            Box(
                                modifier = Modifier.size(drawerLeadingSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(Ionicons.InformationCircleOutline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(drawerIconSize),
                                )
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.settings_placeholder_about)) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate("about")
                                    }
                                },
                    )
                }
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    if (!showMainUi) return@Scaffold
                    val isHomeCollapsible = currentRoute == "home" && !docsSelectionMode
                    val openDrawerContentDescription = stringResource(R.string.action_open_drawer)
                    val homeBarsOffsetInt = homeBarsOffsetPx.roundToInt()
                    val homeSubBarCollapseHeightPx = homeBarHeightPx + homeBarDividerHeightPx
                    val homeTopBarOffsetInt = (homeBarsOffsetPx + homeSubBarCollapseHeightPx).coerceAtMost(0f).roundToInt()
                    val topBarColumnModifier =
                        if (isHomeCollapsible) {
                            Modifier
                                .fillMaxWidth()
                                .clipToBounds()
                        } else {
                            Modifier
                        }

                    Column(modifier = topBarColumnModifier) {
                        if (isHomeCollapsible) {
                            Spacer(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .windowInsetsPadding(WindowInsets.statusBars)
                                        .zIndex(2f),
                            )
                        }
                        ZhixuTopAppBar(
                            modifier =
                                if (isHomeCollapsible) {
                                    Modifier
                                        .zIndex(1f)
                                        .offset { IntOffset(x = 0, y = homeTopBarOffsetInt) }
                                } else {
                                    Modifier
                                },
                            windowInsets = if (isHomeCollapsible) WindowInsets(0, 0, 0, 0) else WindowInsets.statusBars,
                            containerColor = if (docsSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            title = {
                                if (docsSelectionMode) {
                                    Text(
                                        text = stringResource(R.string.docs_selected_count_fmt, selectedDocUris.size),
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    val showOnlySearch =
                                        currentRoute == "home" || currentRoute == "tasks" || currentRoute == "pomodoro" || currentRoute == "space"
                                    if (!showOnlySearch) {
                                        val sectionTitleRes =
                                            when (currentRoute) {
                                                "tasks" -> R.string.home_section_todo
                                                "pomodoro" -> R.string.home_section_pomodoro
                                                "space" -> R.string.home_section_space
                                                else -> R.string.home_section_docs_list
                                            }
                                        Text(
                                            text = stringResource(sectionTitleRes),
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                if (docsSelectionMode) {
                                    ZhixuIconButton(onClick = { selectedDocUris = emptySet() }) {
                                        Icon(
                                            painter = painterResource(Ionicons.X),
                                            contentDescription = stringResource(R.string.action_cancel),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(ZhixuTopBarIconSize),
                                        )
                                    }
                                } else {
                                    ZhixuIconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier.semantics { contentDescription = openDrawerContentDescription },
                                    ) {
                                        val uri = accountState.avatarUri
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (uri.isNotBlank()) {
                                                AsyncImage(
                                                    model = uri,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            } else {
                                                Text(
                                                    text = accountState.username.firstOrNull()?.uppercase() ?: "Z",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            actions = {
                                if (currentRoute == "home") {
                                    if (docsSelectionMode) {
                                        ZhixuIconButton(
                                            onClick =
                                                onClick@{
                                                    val root = vaultRootUri ?: return@onClick
                                                    scope.launch {
                                                        prefs.togglePinnedDocUris(root, selectedDocUris)
                                                    }
                                                },
                                        ) {
                                            Icon(
                                                painter = painterResource(if (docsSelectionAllPinned) Ionicons.PinOff else Ionicons.Pin),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(ZhixuTopBarIconSize),
                                            )
                                        }
                                        ZhixuIconButton(
                                            onClick =
                                                onClick@{
                                                val uriStrings = selectedDocUris.toList()
                                                if (uriStrings.isEmpty()) return@onClick

                                                val streamUris = ArrayList<Uri>(uriStrings.size)
                                                val textUris = ArrayList<String>(0)
                                                for (raw in uriStrings) {
                                                    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: continue
                                                    if (uri.scheme.equals("file", ignoreCase = true)) {
                                                        textUris += uri.toString()
                                                    } else {
                                                        streamUris += uri
                                                    }
                                                }

                                                val intent =
                                                    when {
                                                        streamUris.size >= 2 ->
                                                            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                                                type = "*/*"
                                                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, streamUris)
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                        streamUris.size == 1 ->
                                                            Intent(Intent.ACTION_SEND).apply {
                                                                type = "*/*"
                                                                putExtra(Intent.EXTRA_STREAM, streamUris.first())
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                        else ->
                                                            Intent(Intent.ACTION_SEND).apply {
                                                                type = "text/plain"
                                                            }
                                                    }

                                                if (textUris.isNotEmpty()) {
                                                    intent.putExtra(Intent.EXTRA_TEXT, textUris.joinToString("\n"))
                                                }

                                                runCatching {
                                                    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                                }.onFailure {
                                                    android.widget.Toast.makeText(context, context.getString(R.string.editor_share_failed), android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(Ionicons.Share2),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(ZhixuTopBarIconSize),
                                            )
                                        }
                                        ZhixuIconButton(onClick = { android.widget.Toast.makeText(context, "敬请期待", android.widget.Toast.LENGTH_SHORT).show() }) {
                                            Icon(
                                                painter = painterResource(Ionicons.EllipsisVertical),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(ZhixuTopBarIconSize),
                                            )
                                        }
                                    } else {
                                        val cloudState =
                                            when (vaultSyncConfig.location) {
                                                VaultStorageLocation.LOCAL -> {
                                                    val webDavEnabled = webDavConfig.enabled
                                                    val hasOfficialRecord = !syncServerUiStatus?.text.isNullOrBlank()
                                                    val officialEnabled =
                                                        accountState.token.isNotBlank() && (officialSyncEnabled || hasOfficialRecord)
                                                    when {
                                                        !webDavEnabled && !officialEnabled -> CloudSyncUiIconState.DISABLED
                                                        webDavSyncing || syncServerSyncing -> CloudSyncUiIconState.SYNCING
                                                        (webDavEnabled && webDavUiStatus?.level != WebDavUiStatusLevel.OK) ||
                                                            (officialEnabled && syncServerUiStatus?.level != SyncServerUiStatusLevel.OK) -> CloudSyncUiIconState.WARNING
                                                        else -> CloudSyncUiIconState.OK
                                                    }
                                                }
                                                VaultStorageLocation.OFFICIAL_SERVER -> {
                                                    when {
                                                        accountState.token.isBlank() -> CloudSyncUiIconState.DISABLED
                                                        syncServerSyncing -> CloudSyncUiIconState.SYNCING
                                                        syncServerUiStatus?.level == SyncServerUiStatusLevel.OK -> CloudSyncUiIconState.OK
                                                        else -> CloudSyncUiIconState.WARNING
                                                    }
                                                }
                                                VaultStorageLocation.THIRD_PARTY_SERVICE -> {
                                                    val tp = vaultSyncConfig.thirdParty
                                                    val enabled =
                                                        tp.url.trim().isNotBlank() && tp.username.trim().isNotBlank() && tp.password.isNotBlank()
                                                    when {
                                                        !enabled -> CloudSyncUiIconState.DISABLED
                                                        syncServerSyncing -> CloudSyncUiIconState.SYNCING
                                                        syncServerUiStatus?.level == SyncServerUiStatusLevel.OK -> CloudSyncUiIconState.OK
                                                        else -> CloudSyncUiIconState.WARNING
                                                    }
                                                }
                                            }

                                        val (cloudIconRes, cloudTint) =
                                            when (cloudState) {
                                                CloudSyncUiIconState.SYNCING -> R.drawable.ic_lucide_cloud_sync to Color(0xFF3B82F6)
                                                CloudSyncUiIconState.OK -> R.drawable.ic_lucide_cloud_check to Color(0xFF22C55E)
                                                CloudSyncUiIconState.WARNING -> R.drawable.ic_lucide_cloud_alert to Color(0xFFF59E0B)
                                                CloudSyncUiIconState.DISABLED -> R.drawable.ic_lucide_cloud_off to Color(0xFFEF4444)
                                            }

                                        ZhixuIconButton(onClick = { showCloudSyncStatusDialog = true }) {
                                            Icon(
                                                painter = painterResource(cloudIconRes),
                                                contentDescription = stringResource(R.string.cloud_sync_title),
                                                tint = cloudTint,
                                                modifier = Modifier.size(ZhixuTopBarIconSize),
                                            )
                                        }
                                        if (settledHomePage == 0) {
                                            ZhixuIconButton(onClick = { docSortFilterRequestToken += 1L }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_lucide_settings_2),
                                                    contentDescription = stringResource(R.string.action_sort_filter),
                                                    modifier = Modifier.size(ZhixuTopBarIconSize),
                                                )
                                            }
                                            ZhixuIconButton(onClick = { docListUseGrid = !docListUseGrid }) {
                                                val viewIcon =
                                                    if (docListUseGrid) {
                                                        R.drawable.ic_lucide_stretch_horizontal
                                                    } else {
                                                        R.drawable.ic_lucide_layout_dashboard
                                                    }
                                                Icon(
                                                    painter = painterResource(viewIcon),
                                                    contentDescription = stringResource(R.string.action_toggle_view),
                                                    modifier = Modifier.size(ZhixuTopBarIconSize),
                                                )
                                            }
                                        }
                                        ZhixuIconButton(onClick = { globalSearchOpen = true }) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_lucide_search),
                                                contentDescription = stringResource(R.string.action_search),
                                                modifier = Modifier.size(ZhixuTopBarIconSize),
                                            )
                                        }
                                    }
                                }
                                if (currentRoute == "tasks" || currentRoute == "pomodoro" || currentRoute == "space") {
                                    ZhixuIconButton(onClick = { globalSearchOpen = true }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lucide_search),
                                            contentDescription = stringResource(R.string.action_search),
                                            modifier = Modifier.size(ZhixuTopBarIconSize),
                                        )
                                    }
                                }
                            },
                        )
                        if (currentRoute == "home" && !docsSelectionMode) {
                            HorizontalDivider(
                                modifier =
                                    if (isHomeCollapsible) {
                                        Modifier
                                            .zIndex(1f)
                                            .offset { IntOffset(x = 0, y = homeTopBarOffsetInt) }
                                    } else {
                                        Modifier
                                    },
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        if (currentRoute == "home" && !docsSelectionMode) {
                            HomeSubBar(
                                modifier =
                                    if (isHomeCollapsible) {
                                        Modifier
                                            .zIndex(0f)
                                            .offset { IntOffset(x = 0, y = homeBarsOffsetInt) }
                                    } else {
                                        Modifier
                                    },
                                pagerState = homePagerState,
                                height = ZhixuTopBarContentHeight,
                                labels = listOf("最近访问页面", "空间列表", "可扩展列表"),
                            )
                        }
                        HorizontalDivider(
                            modifier =
                                if (isHomeCollapsible) {
                                    Modifier
                                        .zIndex(0f)
                                        .offset { IntOffset(x = 0, y = homeBarsOffsetInt) }
                                } else {
                                    Modifier
                                },
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                },
                floatingActionButton = {
                    if (!showMainUi || createSheetPage != null || docsSelectionMode) return@Scaffold
                    val fabShape = RoundedCornerShape(999.dp)
                    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    val fabBackground = if (isDark) Color(0xFF0B1B3A) else Color.White
                    val fabContent = if (isDark) Color(0xFFEAF1FF) else MaterialTheme.colorScheme.onSurface
                    val fabBorder = if (isDark) Color(0x33FFFFFF) else Color(0xFFE5E7EB)

                    Box(
                        modifier =
                            Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(bottom = 12.dp),
                    ) {
                        if (!isDark) {
                            Box(
                                modifier =
                                    Modifier
                                        .matchParentSize()
                                        .offset(y = 6.dp)
                                        .shadow(
                                            elevation = 20.dp,
                                            shape = fabShape,
                                            clip = false,
                                            ambientColor = Color(0x5585B9FF),
                                            spotColor = Color(0xCC85B9FF),
                                        ),
                            )
                        }

                        Surface(
                            color = fabBackground,
                            contentColor = fabContent,
                            shape = fabShape,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            border = BorderStroke(1.dp, fabBorder),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ZhixuIconButton(
                                    onClick = { navController.navigate("cameraCapture") },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lucide_camera),
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                ZhixuIconButton(
                                    onClick = { createSheetPage = CreateSheetPage.Draw },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lucide_pencil_ruler),
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                ZhixuIconButton(
                                    onClick = { android.widget.Toast.makeText(context, "录音：敬请期待", android.widget.Toast.LENGTH_SHORT).show() },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lucide_mic),
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                ZhixuIconButton(
                                    onClick = { createSheetPage = CreateSheetPage.QuickNew },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lucide_plus),
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
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
                                                        if (ok) {
                                                            onDocListMutated(DocListMutation.Deleted(docUri = uri))
                                                            runCatching {
                                                                VaultAutoSync.maybeDeleteDoc(
                                                                    context = context,
                                                                    repository = repository,
                                                                    vaultRootUri = root,
                                                                    docUri = uri,
                                                                )
                                                            }
                                                        } else {
                                                            failed += 1
                                                        }
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
                                            createSheetPage = CreateSheetPage.Draw
                                        },
                                        onNewTodo = { createSheetPage = CreateSheetPage.Todo },
                                        onNewNote = { createSheetPage = CreateSheetPage.Note },
                                    )
                                }

                                CreateSheetPage.QuickNew -> {
                                    CreateQuickNewSheetContent(
                                        onNewTodo = { createSheetPage = CreateSheetPage.Todo },
                                        onNewNote = { createSheetPage = CreateSheetPage.Note },
                                    )
                                }

                                CreateSheetPage.Draw -> {
                                    CreateDrawSheetContent(
                                        onBack = { createSheetPage = null },
                                        onClose = { createSheetPage = null },
                                        onCreate = { canvasParam, bgArgb ->
                                            val bgHex = String.format("%08X", bgArgb)
                                            createSheetPage = null
                                            navController.navigate("draw?uri=&canvas=${Uri.encode(canvasParam)}&bg=$bgHex")
                                        },
                                    )
                                }

                                CreateSheetPage.Todo -> {
                                    TodoComposerSheet(
                                        vaultRootUri = vaultRootUri,
                                        repository = repository,
                                        onBack = { createSheetPage = CreateSheetPage.QuickNew },
                                        onClose = { createSheetPage = null },
                                    )
                                }

                                CreateSheetPage.Note -> {
                                    NoteComposerSheet(
                                        sheetState = createSheetState,
                                        vaultRootUri = vaultRootUri,
                                        repository = repository,
                                        onBack = { createSheetPage = CreateSheetPage.QuickNew },
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
                                        onBack = { createSheetPage = CreateSheetPage.QuickNew },
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
                    val homeBarsOffsetDp = with(density) { homeBarsOffsetPx.toDp() }
                    val homeContentPadding: PaddingValues =
                        if (docsSelectionMode) {
                            padding
                        } else {
                            object : PaddingValues {
                                override fun calculateLeftPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection): androidx.compose.ui.unit.Dp =
                                    padding.calculateLeftPadding(layoutDirection)

                                override fun calculateTopPadding(): androidx.compose.ui.unit.Dp =
                                    (padding.calculateTopPadding() + homeBarsOffsetDp).coerceAtLeast(0.dp)

                                override fun calculateRightPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection): androidx.compose.ui.unit.Dp =
                                    padding.calculateRightPadding(layoutDirection)

                                override fun calculateBottomPadding(): androidx.compose.ui.unit.Dp =
                                    padding.calculateBottomPadding()
                            }
                        }
                    HorizontalPager(
                        state = homePagerState,
                        modifier =
                            if (docsSelectionMode) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier.fillMaxSize().nestedScroll(homeBarsNestedScrollConnection)
                            },
                    ) { page ->
                        when (page) {
                            0 ->
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .pointerInput(docsSelectionMode, settledHomePage) {
                                                if (docsSelectionMode || settledHomePage != 0) return@pointerInput

                                                val edgeWidthPx = with(density) { 24.dp.toPx() }
                                                val thresholdPx = with(density) { 72.dp.toPx() }
                                                val slop = viewConfig.touchSlop

                                                awaitEachGesture {
                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    val startX = down.position.x
                                                    val startY = down.position.y

                                                    // Only allow edge-swipe to open the left drawer on the "recent" page.
                                                    if (startX > edgeWidthPx) return@awaitEachGesture

                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                                        if (!change.pressed) break

                                                        val dx = change.position.x - startX
                                                        val dy = change.position.y - startY

                                                        // Give up if the gesture is clearly vertical or swiping left (pager).
                                                        if (abs(dy) > slop && abs(dy) > abs(dx)) break
                                                        if (dx < -slop) break

                                                        if (dx > thresholdPx) {
                                                            change.consume()
                                                            scope.launch { drawerState.open() }
                                                            break
                                                        }

                                                        // Once it looks like an edge swipe, keep consuming so pager doesn't steal it.
                                                        if (dx > slop) change.consume()
                                                    }
                                                }
                                            },
                                ) {
                                    DocumentListScreen(
                                        contentPadding = homeContentPadding,
                                        vaultRootUri = vaultRootUri,
                                        repository = repository,
                                        documentIndex = documentIndex,
                                        indexUpdater = indexUpdater,
                                        isActive = currentRoute == "home" && settledHomePage == 0,
                                        searchRequestToken = docSearchRequestToken,
                                        sortFilterRequestToken = docSortFilterRequestToken,
                                        useGridLayout = docListUseGrid,
                                        pinnedDocUris = pinnedDocUris,
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

                            1 ->
                                SpaceScreen(
                                    contentPadding = homeContentPadding,
                                    vaultRootUri = vaultRootUri,
                                    repository = repository,
                                    isActive = currentRoute == "home" && settledHomePage == 1,
                                    searchRequestToken = 0L,
                                    uploadRequestToken = 0L,
                                    newFolderRequestToken = 0L,
                                    allDirsExpanded = false,
                                    refreshToken = dirStructureMutationToken,
                                    mutation = dirStructureMutation,
                                    onDocListMutated = ::onDocListMutated,
                                    onOpenDoc = { uriStr, lineIndex -> openDoc(uriStr, null, lineIndex) },
                                    onChangeVault = {
                                        scope.launch { prefs.setVaultRootUri(null) }
                                        navController.navigate("vault") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    },
                                    selectedEntryUris = selectedSpaceEntryUris,
                                    onToggleEntrySelection = { uri -> selectedSpaceEntryUris = toggleSelection(selectedSpaceEntryUris, uri) },
                                    onClearEntrySelection = { selectedSpaceEntryUris = emptySet() },
                                )
                            2 ->
                                HomeExtensionsScreen(
                                    contentPadding = homeContentPadding,
                                    vaultRootUri = vaultRootUri,
                                    pluginRepo = pluginRepo,
                                    onOpenDoc = { uriStr, lineIndex -> openDoc(uriStr, null, lineIndex) },
                                    onOpenWorkshop = { navController.navigate("workshop") },
                                )
                            else -> Unit
                        }
                    }
                }
                composable("tasks") {
                    TodoPager(
                        contentPadding = padding,
                        vaultRootUri = vaultRootUri,
                        repository = repository,
                        indexUpdater = indexUpdater,
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
                composable("space") {
                    SpaceScreen(
                        contentPadding = padding,
                        vaultRootUri = vaultRootUri,
                        repository = repository,
                        isActive = currentRoute == "space",
                        searchRequestToken = 0L,
                        uploadRequestToken = 0L,
                        newFolderRequestToken = 0L,
                        allDirsExpanded = false,
                        refreshToken = dirStructureMutationToken,
                        mutation = dirStructureMutation,
                        onDocListMutated = ::onDocListMutated,
                        onOpenDoc = { uriStr, lineIndex -> openDoc(uriStr, null, lineIndex) },
                        onChangeVault = {
                            scope.launch { prefs.setVaultRootUri(null) }
                            navController.navigate("vault") {
                                popUpTo("home") { inclusive = true }
                            }
                        },
                        selectedEntryUris = selectedSpaceEntryUris,
                        onToggleEntrySelection = { uri -> selectedSpaceEntryUris = toggleSelection(selectedSpaceEntryUris, uri) },
                        onClearEntrySelection = { selectedSpaceEntryUris = emptySet() },
                    )
                }
                composable("settingsMain") {
                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        containerColor = MaterialTheme.colorScheme.background,
                        topBar = {
                            Column {
                                ZhixuTopAppBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium) },
                                    navigationIcon = {
                                        ZhixuIconButton(onClick = { navController.popBackStack() }) {
                                            Icon(
                                                painter = painterResource(Ionicons.ArrowBack),
                                                contentDescription = stringResource(R.string.action_back),
                                                modifier = Modifier.size(ZhixuTopBarIconSize),
                                            )
                                        }
                                    },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        },
                    ) { innerPadding ->
                        SettingsScreen(
                            contentPadding = innerPadding,
                            vaultRootUri = vaultRootUri,
                            showAccountManagement = false,
                            showWorkshop = false,
                            showAbout = false,
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
                }
                composable("stats") {
                    PlaceholderScreen(
                        title = stringResource(R.string.drawer_stats),
                        contentPadding = PaddingValues(0.dp),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("helpSupport") {
                    PlaceholderScreen(
                        title = stringResource(R.string.drawer_help_support),
                        contentPadding = PaddingValues(0.dp),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("about") {
                    AboutScreen(
                        contentPadding = PaddingValues(0.dp),
                        onBack = { navController.popBackStack() },
                        onOpenTermsOfUse = { navController.navigate("termsOfUse") },
                        onOpenPrivacyPolicy = { navController.navigate("privacyPolicy") },
                        onOpenOpenSourceLicense = { navController.navigate("openSourceLicense") },
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
                        contentPadding = PaddingValues(0.dp),
                        accountPrefs = accountPrefs,
                        onBack = { navController.popBackStack() },
                        onOpenDeviceManagement = { navController.navigate("deviceManagement") },
                        onOpenStorageManagement = { navController.navigate("accountStorage") },
                        onOpenSyncLogs = { navController.navigate("accountSyncLogs") },
                    )
                }
                composable("auth") {
                    val accountPrefs = remember(appContext) { app.zhixu.data.AccountPreferences(appContext) }
                    AuthScreen(
                        contentPadding = PaddingValues(0.dp),
                        accountPrefs = accountPrefs,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("deviceManagement") {
                    val accountPrefs = remember(appContext) { app.zhixu.data.AccountPreferences(appContext) }
                    DeviceManagementScreen(
                        contentPadding = PaddingValues(0.dp),
                        accountPrefs = accountPrefs,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("accountStorage") {
                    val accountPrefs = remember(appContext) { app.zhixu.data.AccountPreferences(appContext) }
                    app.zhixu.ui.screens.AccountStorageScreen(
                        contentPadding = PaddingValues(0.dp),
                        accountPrefs = accountPrefs,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("accountSyncLogs") {
                    val accountPrefs = remember(appContext) { app.zhixu.data.AccountPreferences(appContext) }
                    app.zhixu.ui.screens.AccountSyncLogsScreen(
                        contentPadding = PaddingValues(0.dp),
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
                composable("cameraCapture") {
                    val root = vaultRootUri
                    if (root == null) {
                        navController.navigate("vault") {
                            popUpTo("cameraCapture") { inclusive = true }
                        }
                        return@composable
                    }
                    CameraCaptureScreen(
                        vaultRootUri = root,
                        repository = repository,
                        onCreated = { created ->
                            val mutation = DocListMutation.Created(created)
                            dirStructureMutation = mutation
                            dirStructureMutationToken += 1L
                            navController.navigate("edit?uri=${Uri.encode(created.uri.toString())}") {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = "draw?uri={uri}&canvas={canvas}&bg={bg}",
                    arguments = listOf(
                        navArgument("uri") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("canvas") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("bg") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    val root = vaultRootUri
                    if (root == null) {
                        navController.navigate("vault") {
                            popUpTo("draw?uri={uri}&canvas={canvas}&bg={bg}") { inclusive = true }
                        }
                        return@composable
                    }
                    val uriParam = entry.arguments?.getString("uri") ?: ""
                    val docUri = parseNavUri(uriParam).takeIf { it != Uri.EMPTY }
                    val canvasParam = entry.arguments?.getString("canvas").orEmpty()
                    val bgParam = entry.arguments?.getString("bg").orEmpty()
                    DrawScreen(
                        vaultRootUri = root,
                        repository = repository,
                        docUri = docUri,
                        initialCanvasParam = canvasParam,
                        initialBackgroundHex = bgParam,
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

    if (globalSearchOpen) {
        VaultSearchDialog(
            query = globalSearchQuery,
            onQueryChange = ::updateGlobalSearchQuery,
            results = globalSearchResults,
            highlightBg = globalSearchHighlightBg,
            onDismiss = {
                globalSearchOpen = false
                clearGlobalSearch()
            },
            onOpenResult = { uriStr, lineIndex ->
                globalSearchOpen = false
                openDoc(uriStr, globalSearchQuery, lineIndex)
                clearGlobalSearch()
            },
        )
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
    indexUpdater: VaultIndexUpdater,
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
                    indexUpdater = indexUpdater,
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
    QuickNew,
    Draw,
    Todo,
    Note,
    Ocr,
}
