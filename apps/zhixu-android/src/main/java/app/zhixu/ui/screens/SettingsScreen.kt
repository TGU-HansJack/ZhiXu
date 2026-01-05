package app.zhixu.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhixu.BuildConfig
import app.zhixu.R
import app.zhixu.data.AccountPreferences
import app.zhixu.data.AccountState
import app.zhixu.data.DailyContrib
import app.zhixu.data.ProPreferences
import app.zhixu.data.UpdateDownloader
import app.zhixu.data.UpdateCheckResult
import app.zhixu.data.UpdateClient
import app.zhixu.data.UpdateInfo
import app.zhixu.data.VaultRepository
import app.zhixu.ui.Ionicons
import app.zhixu.ui.components.ContribCalendarDialog
import app.zhixu.ui.components.MarkdownPreview
import app.zhixu.ui.components.ZhixuDialogDefaults
import app.zhixu.ui.components.ZhixuSwitch
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    refreshToken: Long,
    repository: VaultRepository,
    onOpenDoc: (String, String?, Int?) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenVaultSettings: () -> Unit,
    onOpenWorkshop: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenUiSettings: () -> Unit,
    onOpenPomodoroSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current
    val accountPrefs = remember(context) { AccountPreferences(context.applicationContext) }
    val accountState by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, email = "", avatarUri = ""),
    )

    val proPrefs = remember(context) { ProPreferences(context.applicationContext) }
    val isProEnabled by proPrefs.isProEnabled.collectAsState(initial = false)

    var contribPerDay by remember { mutableStateOf<Map<LocalDate, DailyContrib>?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }
    var updateUiState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }

    LaunchedEffect(refreshToken) {
        val year = LocalDate.now().year
        contribPerDay =
            runCatching { repository.getDailyContribForYear(year = year) }
                .getOrNull()
                ?: emptyMap()
    }

    fun openUrl(url: String) {
        val intent =
            android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, context.getString(R.string.about_open_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun startDownloadAndInstall(latestVersion: String) {
        val url = UpdateClient.officialDownloadUrl(platform = "android", version = latestVersion)
        val downloadId = UpdateDownloader.downloadApkAndInstall(context, url = url, version = latestVersion)
        if (downloadId == null) {
            Toast.makeText(context, "Failed to start download.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Downloading…", Toast.LENGTH_SHORT).show()
        }
    }

    fun startUpdateCheck() {
        updateUiState = UpdateUiState.Loading
        scope.launch {
            updateUiState =
                when (val res = runCatching { UpdateClient.check(currentVersion = BuildConfig.VERSION_NAME, platform = "android") }.getOrNull()) {
                    is UpdateCheckResult.Success -> UpdateUiState.Success(res.info, res.hasUpdate)
                    is UpdateCheckResult.Failure -> UpdateUiState.Error(res.message)
                    null -> UpdateUiState.Error("Failed to check updates.")
                }
        }
    }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    LazyColumn(
        modifier =
            Modifier
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .imePadding(),
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAccount() }
                        .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val avatarUri = accountState.avatarUri
                            if (avatarUri.isNotBlank()) {
                                AsyncImage(model = avatarUri, contentDescription = null, modifier = Modifier.fillMaxSize())
                            } else {
                                Text(text = accountState.username.firstOrNull()?.uppercase() ?: "Z", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val name =
                            if (accountState.isLoggedIn) accountState.username.ifBlank { "Zhixu" }
                            else stringResource(R.string.account_not_logged_in_short)
                        Text(text = name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (accountState.isLoggedIn) "ID: ${accountState.userId}" else "ID: -",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            HorizontalDivider(color = dividerColor)
        }

        item {
            ContribHeatmapCard(
                contribPerDay = contribPerDay ?: emptyMap(),
                repository = repository,
                onOpenDoc = onOpenDoc,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            HorizontalDivider(color = dividerColor)
        }

        item {
            // 知序 PRO section
            ProSettingsRow(
                isProEnabled = isProEnabled,
                onClick = { showProDialog = true },
            )
            HorizontalDivider(color = dividerColor)
        }

        item {
            SettingsNavRow(
                iconRes = Ionicons.Vault,
                title = stringResource(R.string.settings_section_vault),
                onClick = onOpenVaultSettings,
            )
            HorizontalDivider(color = dividerColor)
            SettingsNavRow(
                iconRes = Ionicons.Workshop,
                title = stringResource(R.string.settings_section_workshop),
                enabled = vaultRootUri != null && isProEnabled,
                onClick = onOpenWorkshop,
            )
            HorizontalDivider(color = dividerColor)
        }

        item {
            SettingsNavRow(
                iconRes = Ionicons.Sync,
                title = stringResource(R.string.settings_section_sync),
                enabled = vaultRootUri != null,
                onClick = onOpenSync,
            )
            HorizontalDivider(color = dividerColor)
        }

        item {
            fun comingSoon() {
                Toast.makeText(context, context.getString(R.string.settings_placeholder_coming_soon), Toast.LENGTH_SHORT).show()
            }

            SettingsNavRow(
                iconRes = R.drawable.ic_hi_sparkles_outline,
                title = "AI 设置",
                enabled = isProEnabled,
                onClick = onOpenAiSettings,
            )
            HorizontalDivider(color = dividerColor)

            SettingsNavRow(
                iconRes = Ionicons.LayersOutline,
                title = stringResource(R.string.settings_placeholder_ui),
                onClick = onOpenUiSettings,
            )
            HorizontalDivider(color = dividerColor)

            SettingsNavRow(
                iconRes = Ionicons.TimeOutline,
                title = "番茄设置",
                onClick = onOpenPomodoroSettings,
            )
            HorizontalDivider(color = dividerColor)

            SettingsNavRow(
                iconRes = Ionicons.AccessibilityOutline,
                title = stringResource(R.string.settings_placeholder_accessibility),
                onClick = ::comingSoon,
            )
            HorizontalDivider(color = dividerColor)

            SettingsNavRow(
                iconRes = Ionicons.NotificationsOutline,
                title = stringResource(R.string.settings_placeholder_notifications),
                onClick = onOpenNotifications,
            )
            HorizontalDivider(color = dividerColor)

            SettingsNavRow(
                iconRes = R.drawable.ic_hi_arrow_path_outline,
                title = stringResource(R.string.settings_update_title),
                onClick = {
                    showUpdateDialog = true
                    startUpdateCheck()
                },
            )
            HorizontalDivider(color = dividerColor)

            SettingsNavRow(
                iconRes = Ionicons.HelpCircleOutline,
                title = stringResource(R.string.settings_placeholder_about),
                onClick = onOpenAbout,
            )
            HorizontalDivider(color = dividerColor)
        }
    }

    if (showUpdateDialog) {
        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { showUpdateDialog = false },
            properties = ZhixuDialogDefaults.properties,
            title = { Text(stringResource(R.string.settings_update_title)) },
            text = {
                when (val s = updateUiState) {
                    UpdateUiState.Idle, UpdateUiState.Loading ->
                        Text(stringResource(R.string.settings_update_checking))
                    is UpdateUiState.Error ->
                        Text(s.message)
                    is UpdateUiState.Success ->
                        UpdateResultBody(
                            info = s.info,
                            hasUpdate = s.hasUpdate,
                            currentVersion = BuildConfig.VERSION_NAME,
                        )
                }
            },
            confirmButton = {
                when (val s = updateUiState) {
                    UpdateUiState.Idle, UpdateUiState.Loading ->
                        TextButton(onClick = { showUpdateDialog = false }) { Text(stringResource(R.string.action_close)) }
                    is UpdateUiState.Error -> {
                        TextButton(onClick = { startUpdateCheck() }) { Text(stringResource(R.string.action_retry)) }
                    }
                    is UpdateUiState.Success -> {
                        if (s.hasUpdate) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { openUrl(s.info.sourceUrl) }) {
                                    Text(stringResource(R.string.settings_update_open_page))
                                }
                                TextButton(
                                    onClick = {
                                        startDownloadAndInstall(s.info.latestVersion)
                                        showUpdateDialog = false
                                    },
                                ) { Text(stringResource(R.string.settings_update_open_download)) }
                            }
                        } else {
                            TextButton(onClick = { openUrl(s.info.sourceUrl) }) { Text(stringResource(R.string.settings_update_open_page)) }
                        }
                    }
                }
            },
            dismissButton = {
                when (updateUiState) {
                    UpdateUiState.Idle, UpdateUiState.Loading ->
                        null
                    else ->
                        TextButton(onClick = { showUpdateDialog = false }) { Text(stringResource(R.string.action_close)) }
                }
            },
        )
    }

    if (showProDialog) {
        ProPaymentDialog(
            isProEnabled = isProEnabled,
            onDismiss = { showProDialog = false },
            onActivatePro = {
                scope.launch {
                    proPrefs.setProEnabled(true)
                }
            },
            onDeactivatePro = {
                scope.launch {
                    proPrefs.setProEnabled(false)
                }
            },
        )
    }
}

private sealed class UpdateUiState {
    data object Idle : UpdateUiState()

    data object Loading : UpdateUiState()

    data class Success(val info: UpdateInfo, val hasUpdate: Boolean) : UpdateUiState()

    data class Error(val message: String) : UpdateUiState()
}

@Composable
private fun UpdateResultBody(
    info: UpdateInfo,
    hasUpdate: Boolean,
    currentVersion: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_update_current_fmt, currentVersion))
        Text(stringResource(R.string.settings_update_latest_fmt, info.latestVersion))
        Text(
            text =
                if (hasUpdate) {
                    stringResource(R.string.settings_update_available)
                } else {
                    stringResource(R.string.settings_update_latest)
                },
            color = if (hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val log = info.changelog.trim()
        if (log.isNotBlank()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Text(stringResource(R.string.settings_update_changelog))
            MarkdownPreview(
                markdown = log,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
            )
        }
    }
}

@Composable
private fun SettingsNavRow(
    iconRes: Int,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(alpha)
                .let { m -> if (enabled) m.clickable(onClick = onClick) else m },
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = { Text(title) },
        supportingContent = {
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            Icon(
                painter = painterResource(Ionicons.ChevronForward),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

@Composable
private fun ProSettingsRow(
    isProEnabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_hi_sparkles_outline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = {
            Text(
                text = "知序PRO",
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (isProEnabled) {
                Icon(
                    painter = painterResource(Ionicons.CheckmarkCircle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    painter = painterResource(Ionicons.ChevronForward),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}

@Composable
private fun ProPaymentDialog(
    isProEnabled: Boolean,
    onDismiss: () -> Unit,
    onActivatePro: () -> Unit,
    onDeactivatePro: () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    var enlargedImageRes by remember { mutableStateOf<Int?>(null) }

    // Enlarged image dialog
    if (enlargedImageRes != null) {
        AlertDialog(
            onDismissRequest = { enlargedImageRes = null },
            properties = ZhixuDialogDefaults.properties,
            confirmButton = {
                TextButton(onClick = { enlargedImageRes = null }) {
                    Text("关闭")
                }
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(enlargedImageRes!!),
                        contentDescription = "付款码",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            },
        )
    }

    AlertDialog(
        modifier = ZhixuDialogDefaults.modifier().fillMaxWidth(),
        onDismissRequest = onDismiss,
        properties = ZhixuDialogDefaults.properties,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "¥ 18.00",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "若你喜欢知序，可以尝试为其付费哦~",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "采取诚信授权模式，灵感来自 WakeUp 课程表",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "理论上是可以直接使用的，付费后再使用是诚信的表现",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "可通过扫描二维码以支付宝或微信的方式支付",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tab row for selecting payment method
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("支付宝") },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("微信支付") },
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Hint text
                Text(
                    text = "点击放大截图支付",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // QR code pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) { page ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val imageRes = if (page == 0) R.drawable.qr_alipay else R.drawable.qr_wechat
                        Image(
                            painter = painterResource(imageRes),
                            contentDescription = if (page == 0) "支付宝付款码" else "微信付款码",
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { enlargedImageRes = imageRes },
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "请仔细考虑后再付款",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Activate PRO switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "我已诚信付款，启用知序PRO",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    ZhixuSwitch(
                        checked = isProEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                onActivatePro()
                            } else {
                                onDeactivatePro()
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun ContribHeatmapCard(
    contribPerDay: Map<LocalDate, DailyContrib>,
    repository: VaultRepository,
    onOpenDoc: (String, String?, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = 7
    val today = LocalDate.now()
    val year = today.year
    val yearStart = LocalDate.of(year, 1, 1)
    val yearEnd = LocalDate.of(year, 12, 31)

    val totalsPerDay = remember(contribPerDay) { contribPerDay.mapValues { (_, v) -> v.total } }
    val max =
        totalsPerDay
            .asSequence()
            .filter { (d, _) -> !d.isBefore(yearStart) && !d.isAfter(yearEnd) }
            .map { it.value }
            .maxOrNull()
            ?: 0

    var showCalendar by remember { mutableStateOf(false) }
    if (showCalendar) {
        ContribCalendarDialog(
            repository = repository,
            onOpenDoc = onOpenDoc,
            onDismiss = { showCalendar = false },
        )
    }

    val cellSize = 10.dp
    val gap = 2.dp
    val weekPitch = cellSize + gap
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    fun HeatmapHalf(
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        monthRange: IntRange,
    ) {
        val startSunday = rangeStart.minusDays((rangeStart.dayOfWeek.value % 7).toLong())
        val endSaturday = rangeEnd.plusDays((6 - (rangeEnd.dayOfWeek.value % 7)).toLong())
        val cols = ((ChronoUnit.DAYS.between(startSunday, endSaturday) + 1L) / 7L).toInt().coerceAtLeast(1)
        val levels =
            remember(totalsPerDay, rangeStart, rangeEnd, cols, max) {
                buildGitHubYearHeatmapLevels(
                    countsPerDay = totalsPerDay,
                    yearStart = rangeStart,
                    yearEnd = rangeEnd,
                    startSunday = startSunday,
                    rows = rows,
                    cols = cols,
                    max = max,
                )
            }

        val monthStarts =
            remember(year, cols, startSunday, monthRange) {
                monthRange.mapNotNull { month ->
                    val date = LocalDate.of(year, month, 1)
                    val col = (ChronoUnit.DAYS.between(startSunday, date) / 7L).toInt()
                    if (col !in 0 until cols) return@mapNotNull null
                    col to "${month}月"
                }
            }

        val gridWidth = (cellSize * cols) + (gap * (cols - 1))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.width(gridWidth).height(18.dp)) {
                for ((col, label) in monthStarts) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.75f),
                        modifier = Modifier.offset(x = weekPitch * col, y = 1.dp),
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.width(gridWidth),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (col in 0 until cols) {
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        for (row in 0 until rows) {
                            val idx = col * rows + row
                            val level = levels.getOrNull(idx)?.coerceIn(0, 4) ?: 0
                            val date = startSunday.plusDays((col * rows + row).toLong())
                            if (date.isBefore(rangeStart) || date.isAfter(rangeEnd)) {
                                Spacer(Modifier.size(cellSize))
                            } else {
                                ContributionCell(level = level, size = cellSize)
                            }
                        }
                    }
                }
            }
        }
    }

    val (halfStart, halfEnd, monthRange) =
        if (today.monthValue <= 6) {
            Triple(yearStart, LocalDate.of(year, 6, 30), 1..6)
        } else {
            Triple(LocalDate.of(year, 7, 1), yearEnd, 7..12)
        }

    Surface(
        modifier = modifier.clickable { showCalendar = true },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            HeatmapHalf(rangeStart = halfStart, rangeEnd = halfEnd, monthRange = monthRange)

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("更少", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.75f))
                    Spacer(Modifier.width(6.dp))
                    for (level in 0..4) {
                        ContributionCell(level = level, size = 10.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("更多", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@Composable
private fun ContribHeatmapCardLegacy(
    contribPerDay: Map<LocalDate, DailyContrib>,
    modifier: Modifier = Modifier,
) {
    val rows = 7
    val today = LocalDate.now()
    val year = today.year
    val yearStart = LocalDate.of(year, 1, 1)
    val yearEnd = LocalDate.of(year, 12, 31)

    val startSunday = yearStart.minusDays((yearStart.dayOfWeek.value % 7).toLong())
    val endSaturday = yearEnd.plusDays((6 - (yearEnd.dayOfWeek.value % 7)).toLong())
    val cols = ((ChronoUnit.DAYS.between(startSunday, endSaturday) + 1L) / 7L).toInt().coerceAtLeast(1)

    val totalsPerDay = remember(contribPerDay) { contribPerDay.mapValues { (_, v) -> v.total } }

    val max =
        totalsPerDay
            .asSequence()
            .filter { (d, _) -> !d.isBefore(yearStart) && !d.isAfter(yearEnd) }
            .map { it.value }
            .maxOrNull()
            ?: 0

    val levels =
        remember(totalsPerDay, year) {
            buildGitHubYearHeatmapLevels(
                countsPerDay = totalsPerDay,
                yearStart = yearStart,
                yearEnd = yearEnd,
                startSunday = startSunday,
                rows = rows,
                cols = cols,
                max = max,
            )
        }

    val dayLabelWidth = 44.dp
    val cellSize = 11.dp
    val gap = 4.dp
    val weekPitch = cellSize + gap
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dayLabelStyle =
        MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            lineHeight = 9.sp,
        )

    val monthStarts =
        remember(year, cols, startSunday) {
            (1..12).mapNotNull { month ->
                val date = LocalDate.of(year, month, 1)
                val col = (ChronoUnit.DAYS.between(startSunday, date) / 7L).toInt()
                if (col !in 0 until cols) return@mapNotNull null
                col to "${month}月"
            }
        }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            val scrollState = rememberScrollState()
            LaunchedEffect(levels.size) {
                scrollState.scrollTo(scrollState.maxValue)
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Spacer(Modifier.width(dayLabelWidth))
                Box(modifier = Modifier.horizontalScroll(scrollState)) {
                    Box(modifier = Modifier.width(weekPitch * cols).height(18.dp)) {
                        for ((col, label) in monthStarts) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.75f),
                                modifier = Modifier.offset(x = weekPitch * col, y = 1.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.width(dayLabelWidth),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    for (row in 0 until rows) {
                        val label =
                            when (row) {
                                1 -> "周一"
                                3 -> "周三"
                                5 -> "周五"
                                else -> ""
                            }
                        Box(
                            modifier = Modifier.height(cellSize).fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (label.isNotBlank()) {
                                Text(
                                    text = label,
                                    style = dayLabelStyle,
                                    color = textColor.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.horizontalScroll(scrollState)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (col in 0 until cols) {
                            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                                for (row in 0 until rows) {
                                    val idx = col * rows + row
                                    val level = levels.getOrNull(idx)?.coerceIn(0, 4) ?: 0
                                    val date = startSunday.plusDays((col * rows + row).toLong())
                                    if (date.isBefore(yearStart) || date.isAfter(yearEnd)) {
                                        Spacer(Modifier.size(cellSize))
                                    } else {
                                        ContributionCell(level = level, size = cellSize)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("更少的", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.75f))
                    Spacer(Modifier.width(6.dp))
                    for (level in 0..4) {
                        ContributionCell(level = level, size = 10.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("更多的", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@Composable
private fun ContributionCell(
    level: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val colors =
        listOf(
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
        )
    val safeLevel = level.coerceIn(0, 4)
    Box(
        modifier =
            modifier
                .size(size)
                .background(color = colors[safeLevel], shape = shape),
    )
}

private fun buildGitHubYearHeatmapLevels(
    countsPerDay: Map<LocalDate, Int>,
    yearStart: LocalDate,
    yearEnd: LocalDate,
    startSunday: LocalDate,
    rows: Int,
    cols: Int,
    max: Int,
): IntArray {
    val out = IntArray(rows * cols) { 0 }
    if (rows <= 0 || cols <= 0) return out

    fun levelFor(v: Int): Int {
        if (v <= 0) return 0
        if (max <= 1) return 1
        return (1 + ((v - 1) * 3 / (max - 1))).coerceIn(1, 4)
    }

    for (col in 0 until cols) {
        val weekStart = startSunday.plusDays((col * rows).toLong())
        for (row in 0 until rows) {
            val date = weekStart.plusDays(row.toLong())
            if (date.isBefore(yearStart) || date.isAfter(yearEnd)) continue
            val v = countsPerDay[date] ?: 0
            out[col * rows + row] = levelFor(v)
        }
    }

    return out
}
