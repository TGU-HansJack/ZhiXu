package com.zhixu.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zhixu.android.R
import com.zhixu.android.data.AccountPreferences
import com.zhixu.android.data.AccountState
import com.zhixu.android.sync.OfficialSync
import com.zhixu.android.sync.SyncServerClient
import com.zhixu.android.sync.SyncServerResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    contentPadding: PaddingValues,
    accountPrefs: AccountPreferences,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, deviceId = ""),
    )

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var devicesRemote by remember { mutableStateOf<List<String>>(emptyList()) }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val loginOkText = stringResource(R.string.account_login_ok)
    val registerOkText = stringResource(R.string.account_register_ok)
    val loggedOutText = stringResource(R.string.account_logged_out)
    val serverUnreachableText = stringResource(R.string.error_server_unreachable)

    fun <T> SyncServerResult<T>.toUiMessage(fallback: String): String {
        return when {
            statusCode == 0 || errorMessage == "NETWORK_UNREACHABLE" -> serverUnreachableText
            !errorMessage.isNullOrBlank() -> errorMessage!!
            else -> fallback
        }
    }

    LaunchedEffect(Unit) {
        runCatching { accountPrefs.ensureDeviceId() }
    }

    LaunchedEffect(state.username) {
        if (username.isBlank() && state.username.isNotBlank()) username = state.username
    }

    LaunchedEffect(state.token) {
        if (!state.isLoggedIn) {
            devicesRemote = emptyList()
            return@LaunchedEffect
        }
        val ensured = runCatching { accountPrefs.ensureDeviceId() }.getOrNull().orEmpty()
        val result = SyncServerClient.listDevices(OfficialSync.BASE_URL, state.token)
        devicesRemote = result.value ?: emptyList()
        if (ensured.isNotBlank() && result.ok && (result.value?.contains(ensured) != true)) {
            SyncServerClient.bindDevice(OfficialSync.BASE_URL, state.token, ensured)
            val list = SyncServerClient.listDevices(OfficialSync.BASE_URL, state.token)
            devicesRemote = list.value ?: devicesRemote
        }
    }

    fun setBusy(on: Boolean) {
        busy = on
        if (on) status = null
    }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    windowInsets = TopAppBarDefaults.windowInsets,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    title = { Text(stringResource(R.string.account_manage_title)) },
                    navigationIcon = {
                        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(if (isRtl) com.zhixu.android.ui.Ionicons.ArrowForward else com.zhixu.android.ui.Ionicons.ArrowBack),
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.account_server_fmt, OfficialSync.BASE_URL),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            item {
                Text(
                    text =
                        if (state.isLoggedIn) {
                            stringResource(R.string.account_logged_in_as_fmt, state.username.ifBlank { "-" })
                        } else {
                            stringResource(R.string.account_not_logged_in)
                        },
                )
            }

            item { HorizontalDivider(color = dividerColor) }

            item {
                TextField(
                    value = username,
                    onValueChange = { username = it },
                    enabled = !busy,
                    label = { Text(stringResource(R.string.account_username)) },
                    singleLine = true,
                    colors = transparentTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !busy,
                    label = { Text(stringResource(R.string.account_password)) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(enabled = !busy, onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) stringResource(R.string.action_hide) else stringResource(R.string.action_show))
                        }
                    },
                    colors = transparentTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy && username.trim().isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val u = username.trim()
                            val p = password
                            scope.launch {
                                setBusy(true)
                                val login = SyncServerClient.login(OfficialSync.BASE_URL, u, p)
                                if (!login.ok || login.value.isNullOrBlank()) {
                                    status = login.toUiMessage("Login failed")
                                    setBusy(false)
                                    return@launch
                                }
                                val token = login.value!!
                                val me = SyncServerClient.me(OfficialSync.BASE_URL, token)
                                if (!me.ok || me.value == null) {
                                    status = me.toUiMessage("Fetch profile failed")
                                    setBusy(false)
                                    return@launch
                                }
                                accountPrefs.setLoggedIn(token = token, username = me.value.username, userId = me.value.userId)
                                val id = runCatching { accountPrefs.ensureDeviceId() }.getOrNull().orEmpty()
                                if (id.isNotBlank()) {
                                    SyncServerClient.bindDevice(OfficialSync.BASE_URL, token, id)
                                    val list = SyncServerClient.listDevices(OfficialSync.BASE_URL, token)
                                    devicesRemote = list.value ?: devicesRemote
                                }
                                status = loginOkText
                                setBusy(false)
                            }
                        },
                    ) { Text(stringResource(R.string.account_login)) }

                    OutlinedButton(
                        enabled = !busy && username.trim().isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val u = username.trim()
                            val p = password
                            scope.launch {
                                setBusy(true)
                                val reg = SyncServerClient.register(OfficialSync.BASE_URL, u, p)
                                status = if (reg.ok) registerOkText else reg.toUiMessage("Register failed")
                                setBusy(false)
                            }
                        },
                    ) { Text(stringResource(R.string.account_register)) }
                }
            }

            if (state.isLoggedIn) {
                item {
                    OutlinedButton(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                setBusy(true)
                                accountPrefs.logout()
                                status = loggedOutText
                                setBusy(false)
                            }
                        },
                    ) { Text(stringResource(R.string.account_logout)) }
                }
            }

            item { HorizontalDivider(color = dividerColor) }

            item {
                Text(text = stringResource(R.string.account_device_title), style = MaterialTheme.typography.titleSmall)
            }

            item {
                TextField(
                    value = state.deviceId,
                    onValueChange = {},
                    enabled = false,
                    label = { Text(stringResource(R.string.account_device_id)) },
                    singleLine = true,
                    colors = transparentTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                OutlinedButton(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            setBusy(true)
                            val id = runCatching { accountPrefs.regenerateDeviceId() }.getOrNull().orEmpty()
                            if (state.isLoggedIn && id.isNotBlank()) {
                                SyncServerClient.bindDevice(OfficialSync.BASE_URL, state.token, id)
                                val list = SyncServerClient.listDevices(OfficialSync.BASE_URL, state.token)
                                devicesRemote = list.value ?: devicesRemote
                            }
                            status = context.getString(R.string.account_device_regenerated)
                            setBusy(false)
                        }
                    },
                ) { Text(stringResource(R.string.account_device_regenerate)) }
            }

            if (state.isLoggedIn) {
                item {
                    Text(
                        text = stringResource(R.string.account_device_remote_list),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                val devices = devicesRemote
                if (devices.isEmpty()) {
                    item { Text(stringResource(R.string.account_device_remote_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(devices.size) { idx ->
                        val deviceId = devices[idx]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(deviceId, modifier = Modifier.weight(1f), maxLines = 1)
                            TextButton(
                                enabled = !busy && deviceId != state.deviceId,
                                onClick = {
                                    scope.launch {
                                        setBusy(true)
                                        val r = SyncServerClient.unbindDevice(OfficialSync.BASE_URL, state.token, deviceId)
                                        if (r.ok) {
                                            val list = SyncServerClient.listDevices(OfficialSync.BASE_URL, state.token)
                                            devicesRemote = list.value ?: emptyList()
                                            status = context.getString(R.string.account_device_unbound)
                                        } else {
                                            status = r.errorMessage ?: "Unbind failed"
                                        }
                                        setBusy(false)
                                    }
                                },
                            ) { Text(stringResource(R.string.account_device_unbind)) }
                        }
                    }
                }
            }

            if (!status.isNullOrBlank()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(status!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun transparentTextFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
