package app.zhixu.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.data.AccountPreferences
import app.zhixu.data.AccountState
import app.zhixu.sync.OfficialSync
import app.zhixu.sync.SyncServerClient
import app.zhixu.sync.SyncServerResult
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceManagementScreen(
    contentPadding: PaddingValues,
    accountPrefs: AccountPreferences,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, email = "", avatarUri = ""),
    )

    var loading by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf<List<SyncServerClient.AccountSession>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmLogoutSessionId by remember { mutableStateOf<String?>(null) }

    val serverUnreachableText = stringResource(R.string.error_server_unreachable)

    fun <T> SyncServerResult<T>.toUiMessage(fallback: String): String {
        return when {
            statusCode == 0 || errorMessage == "NETWORK_UNREACHABLE" -> serverUnreachableText
            !errorMessage.isNullOrBlank() -> errorMessage!!
            else -> fallback
        }
    }

    suspend fun refresh() {
        if (!state.isLoggedIn) {
            sessions = emptyList()
            error = context.getString(R.string.account_login_required)
            return
        }
        loading = true
        error = null
        try {
            val res = SyncServerClient.listSessions(OfficialSync.BASE_URL, token = state.token)
            sessions = res.value ?: emptyList()
            if (!res.ok) error = res.toUiMessage(context.getString(R.string.device_list_failed))
        } finally {
            loading = false
        }
    }

    LaunchedEffect(state.token) { refresh() }

    val sessionToLogout = confirmLogoutSessionId
    if (sessionToLogout != null) {
        AlertDialog(
            onDismissRequest = { confirmLogoutSessionId = null },
            title = { Text(stringResource(R.string.device_logout_confirm_title)) },
            text = { Text(stringResource(R.string.device_logout_confirm_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogoutSessionId = null
                        scope.launch {
                            if (!state.isLoggedIn) return@launch
                            val res = SyncServerClient.revokeSession(OfficialSync.BASE_URL, token = state.token, sessionId = sessionToLogout)
                            if (res.ok) {
                                Toast.makeText(context, context.getString(R.string.device_logged_out), Toast.LENGTH_SHORT).show()
                                refresh()
                            } else {
                                Toast.makeText(context, res.toUiMessage(context.getString(R.string.device_logout_failed)), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                ) { Text(stringResource(R.string.device_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogoutSessionId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.device_management_title), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(Ionicons.ArrowBack),
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                            Text(stringResource(R.string.action_refresh))
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { innerPadding ->
        if (loading && sessions.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .padding(contentPadding)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .padding(contentPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxSize()
                    .imePadding(),
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (!error.isNullOrBlank()) {
                item {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (sessions.isEmpty() && error.isNullOrBlank()) {
                item {
                    Text(
                        text = stringResource(R.string.device_list_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            items(sessions.size) { idx ->
                val s = sessions[idx]
                val isMobile = s.client.contains("android", ignoreCase = true) || s.client.contains("mobile", ignoreCase = true)
                val subtitleParts =
                    listOfNotNull(
                        s.client.ifBlank { null },
                        s.lastSeenText.ifBlank { null },
                        s.ip.ifBlank { null },
                        s.location.ifBlank { null },
                    )
                val subtitle = subtitleParts.joinToString("，")

                ListItem(
                    modifier = Modifier.fillMaxWidth(),
                    leadingContent = {
                        Icon(
                            imageVector = if (isMobile) Icons.Outlined.PhoneAndroid else Icons.Outlined.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = s.name.ifBlank { "-" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (s.isCurrent) {
                                Text(
                                    text = stringResource(R.string.device_current_tag),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    },
                    supportingContent = {
                        if (subtitle.isNotBlank()) Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        if (!s.isCurrent) {
                            TextButton(onClick = { confirmLogoutSessionId = s.sessionId }) {
                                Text(stringResource(R.string.device_logout))
                            }
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}
