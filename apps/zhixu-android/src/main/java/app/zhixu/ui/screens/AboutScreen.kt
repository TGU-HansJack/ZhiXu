package app.zhixu.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhixu.BuildConfig
import app.zhixu.R
import app.zhixu.data.UpdateCheckResult
import app.zhixu.data.UpdateClient
import app.zhixu.data.UpdateDownloader
import app.zhixu.data.UpdateInfo
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.launch

private const val OFFICIAL_SITE_URL = "https://zhixu.app"
private const val OFFICIAL_TOS_URL = "https://zhixu.app/tos"
private const val OFFICIAL_PRIVACY_URL = "https://zhixu.app/privacy"
private const val OFFICIAL_LICENSE_URL = "https://zhixu.app/license"
private const val QQ_GROUP_NUMBER = "892430777"
private const val QQ_GROUP_URI =
    "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$QQ_GROUP_NUMBER&card_type=group&source=external"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenTermsOfUse: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenOpenSourceLicense: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var updateExpanded by remember { mutableStateOf(false) }
    var updateUiState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            toast(context.getString(R.string.about_open_failed))
        }
    }

    fun openQqGroup() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(QQ_GROUP_URI)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            clipboard.setText(AnnotatedString(QQ_GROUP_NUMBER))
            toast(context.getString(R.string.about_qq_copied_fmt, QQ_GROUP_NUMBER))
        }
    }

    fun startDownloadAndInstall(latestVersion: String) {
        val url = UpdateClient.officialDownloadUrl(platform = "android", version = latestVersion)
        val downloadId = UpdateDownloader.downloadApkAndInstall(context, url = url, version = latestVersion)
        if (downloadId == null) {
            toast(context.getString(R.string.common_failed))
        } else {
            toast("Downloading…")
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ZhixuTopAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text(stringResource(R.string.settings_placeholder_about), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    ZhixuIconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Ionicons.ArrowBack),
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier.size(ZhixuTopBarIconSize),
                        )
                    }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Text(
                    text = stringResource(R.string.settings_update_title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    AboutUpdateSection(
                        expanded = updateExpanded,
                        uiState = updateUiState,
                        onToggleExpanded = {
                            val nextExpanded = !updateExpanded
                            updateExpanded = nextExpanded
                            if (nextExpanded) startUpdateCheck()
                        },
                        onRetry = { startUpdateCheck() },
                        onOpenUpdatePage = { url -> openUrl(url) },
                        onDownloadAndInstall = ::startDownloadAndInstall,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    AboutNavRow(
                        iconRes = Ionicons.InformationCircleOutline,
                        title = stringResource(R.string.about_visit_website),
                        value = "zhixu.app",
                        onClick = { openUrl(OFFICIAL_SITE_URL) },
                    )
                    HorizontalDivider(color = dividerColor)
                    AboutNavRow(
                        iconRes = Ionicons.User,
                        title = stringResource(R.string.about_join_qq_group),
                        value = QQ_GROUP_NUMBER,
                        onClick = ::openQqGroup,
                    )
                    HorizontalDivider(color = dividerColor)
                    AboutNavRow(
                        iconRes = Ionicons.DocumentText,
                        title = stringResource(R.string.terms_of_use_title),
                        value = OFFICIAL_TOS_URL.removePrefix("https://"),
                        onClick = onOpenTermsOfUse,
                    )
                    HorizontalDivider(color = dividerColor)
                    AboutNavRow(
                        iconRes = Ionicons.DocumentText,
                        title = stringResource(R.string.privacy_policy_title),
                        value = OFFICIAL_PRIVACY_URL.removePrefix("https://"),
                        onClick = onOpenPrivacyPolicy,
                    )
                    HorizontalDivider(color = dividerColor)
                    AboutNavRow(
                        iconRes = Ionicons.DocumentText,
                        title = stringResource(R.string.open_source_license_title),
                        value = OFFICIAL_LICENSE_URL.removePrefix("https://"),
                        onClick = onOpenOpenSourceLicense,
                    )
                }
            }
        }
    }
}

private sealed class UpdateUiState {
    data object Idle : UpdateUiState()

    data object Loading : UpdateUiState()

    data class Success(val info: UpdateInfo, val hasUpdate: Boolean) : UpdateUiState()

    data class Error(val message: String) : UpdateUiState()
}

@Composable
private fun AboutUpdateSection(
    expanded: Boolean,
    uiState: UpdateUiState,
    onToggleExpanded: () -> Unit,
    onRetry: () -> Unit,
    onOpenUpdatePage: (String) -> Unit,
    onDownloadAndInstall: (String) -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(durationMillis = 180),
        label = "updateArrow",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded),
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_hi_arrow_path_outline),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            },
            headlineContent = { Text(stringResource(R.string.settings_update_title)) },
            supportingContent = {
                when (uiState) {
                    UpdateUiState.Idle -> Unit
                    UpdateUiState.Loading ->
                        Text(
                            text = stringResource(R.string.settings_update_checking),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    is UpdateUiState.Error ->
                        Text(
                            text = uiState.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    is UpdateUiState.Success -> {
                        Text(
                            text = "当前 ${BuildConfig.VERSION_NAME} · 最新 ${uiState.info.latestVersion}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        if (uiState.hasUpdate) {
                            Text(
                                text = stringResource(R.string.settings_update_available),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.settings_update_latest),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            trailingContent = {
                Icon(
                    painter = painterResource(Ionicons.ChevronDown),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).rotate(arrowRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        if (!expanded) return@Column

        when (uiState) {
            UpdateUiState.Idle, UpdateUiState.Loading -> Unit
            is UpdateUiState.Error -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
            is UpdateUiState.Success -> {
                val log = uiState.info.changelog.trim()
                if (log.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                        ) {
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                    Spacer(Modifier.size(16.dp))
                    if (uiState.hasUpdate) {
                        if (uiState.info.sourceUrl.endsWith(".apk", ignoreCase = true)) {
                            TextButton(
                                onClick = { onDownloadAndInstall(uiState.info.latestVersion) },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(stringResource(R.string.settings_update_open_download))
                            }
                        } else {
                            TextButton(onClick = { onOpenUpdatePage(uiState.info.sourceUrl) }, contentPadding = PaddingValues(0.dp)) {
                                Text(stringResource(R.string.settings_update_open_page))
                            }
                        }
                    } else {
                        TextButton(onClick = { onOpenUpdatePage(uiState.info.sourceUrl) }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.settings_update_open_page))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutNavRow(
    iconRes: Int,
    title: String,
    value: String?,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = { Text(title) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!value.isNullOrBlank()) {
                    Text(
                        text = value,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Icon(
                    painter = painterResource(Ionicons.ChevronForward),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}
