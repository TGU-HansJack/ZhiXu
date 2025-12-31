package com.zhixu.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zhixu.android.R
import com.zhixu.android.data.AccountPreferences
import com.zhixu.android.data.AccountState
import com.zhixu.android.ui.components.ZhixuDialogDefaults
import com.zhixu.android.sync.OfficialSync
import com.zhixu.android.sync.SyncServerClient
import com.zhixu.android.sync.SyncServerResult
import kotlinx.coroutines.launch

@Composable
fun AccountManagementDialog(
    accountPrefs: AccountPreferences,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, deviceId = ""),
    )

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var deviceId by remember { mutableStateOf("") }
    var devicesRemote by remember { mutableStateOf<List<String>>(emptyList()) }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val loginOkText = stringResource(R.string.account_login_ok)
    val registerOkText = stringResource(R.string.account_register_ok)
    val loggedOutText = stringResource(R.string.account_logged_out)
    val deviceBoundText = stringResource(R.string.account_device_bound)
    val deviceUnboundText = stringResource(R.string.account_device_unbound)
    val deviceRegeneratedText = stringResource(R.string.account_device_regenerated)
    val serverUnreachableText = stringResource(R.string.error_server_unreachable)

    fun <T> SyncServerResult<T>.toUiMessage(fallback: String): String {
        return when {
            statusCode == 0 || errorMessage == "NETWORK_UNREACHABLE" -> serverUnreachableText
            !errorMessage.isNullOrBlank() -> errorMessage!!
            else -> fallback
        }
    }

    LaunchedEffect(Unit) {
        val ensured = runCatching { accountPrefs.ensureDeviceId() }.getOrNull().orEmpty()
        if (deviceId.isBlank() && ensured.isNotBlank()) deviceId = ensured
    }

    LaunchedEffect(state.username) {
        if (username.isBlank() && state.username.isNotBlank()) username = state.username
    }

    LaunchedEffect(state.deviceId) {
        if (deviceId.isBlank() && state.deviceId.isNotBlank()) deviceId = state.deviceId
    }

    LaunchedEffect(state.token) {
        if (!state.isLoggedIn) {
            devicesRemote = emptyList()
            return@LaunchedEffect
        }
        val ensured = runCatching { accountPrefs.ensureDeviceId() }.getOrNull().orEmpty()
        if (ensured.isNotBlank() && deviceId.isBlank()) deviceId = ensured

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

    AlertDialog(
        modifier = ZhixuDialogDefaults.modifier(),
        onDismissRequest = { if (!busy) onDismiss() },
        properties = ZhixuDialogDefaults.properties,
        title = { Text(stringResource(R.string.account_manage_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.account_server_fmt, OfficialSync.BASE_URL),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )

                if (state.isLoggedIn) {
                    Text(
                        text = stringResource(R.string.account_logged_in_as_fmt, state.username.ifBlank { "-" }),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.account_user_id_fmt, state.userId),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.account_not_logged_in),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                TextField(
                    value = username,
                    onValueChange = { username = it },
                    enabled = !busy,
                    label = { Text(stringResource(R.string.account_username)) },
                    singleLine = true,
                    colors = transparentTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
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
                                    deviceId = id
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
                                status =
                                    if (reg.ok) {
                                        registerOkText
                                    } else {
                                        reg.toUiMessage("Register failed")
                                    }
                                setBusy(false)
                            }
                        },
                    ) { Text(stringResource(R.string.account_register)) }
                }

                if (state.isLoggedIn) {
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

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                Text(
                    text = stringResource(R.string.account_device_title),
                    style = MaterialTheme.typography.titleSmall,
                )

                TextField(
                    value = deviceId,
                    onValueChange = {},
                    enabled = false,
                    label = { Text(stringResource(R.string.account_device_id)) },
                    singleLine = true,
                    colors = transparentTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedButton(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            setBusy(true)
                            val id = runCatching { accountPrefs.regenerateDeviceId() }.getOrNull().orEmpty()
                            if (id.isNotBlank()) {
                                deviceId = id
                                if (state.isLoggedIn) {
                                    val r = SyncServerClient.bindDevice(OfficialSync.BASE_URL, state.token, id)
                                    if (r.ok) {
                                        val list = SyncServerClient.listDevices(OfficialSync.BASE_URL, state.token)
                                        devicesRemote = list.value ?: devicesRemote
                                    }
                                }
                            }
                            status = deviceRegeneratedText
                            setBusy(false)
                        }
                    },
                ) { Text(stringResource(R.string.account_device_regenerate)) }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy && state.isLoggedIn && deviceId.trim().isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val id = deviceId.trim()
                            scope.launch {
                                setBusy(true)
                                accountPrefs.setDeviceId(id)
                                val r = SyncServerClient.bindDevice(OfficialSync.BASE_URL, state.token, id)
                                if (r.ok) {
                                    status = deviceBoundText
                                    val list = SyncServerClient.listDevices(OfficialSync.BASE_URL, state.token)
                                    devicesRemote = list.value ?: devicesRemote
                                } else {
                                    status = r.errorMessage ?: "Bind failed"
                                }
                                setBusy(false)
                            }
                        },
                    ) { Text(stringResource(R.string.account_device_bind)) }
                    OutlinedButton(
                        enabled = !busy && state.isLoggedIn && deviceId.trim().isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val id = deviceId.trim()
                            scope.launch {
                                setBusy(true)
                                val r = SyncServerClient.unbindDevice(OfficialSync.BASE_URL, state.token, id)
                                if (r.ok) {
                                    status = deviceUnboundText
                                    val list = SyncServerClient.listDevices(OfficialSync.BASE_URL, state.token)
                                    devicesRemote = list.value ?: emptyList()
                                } else {
                                    status = r.errorMessage ?: "Unbind failed"
                                }
                                setBusy(false)
                            }
                        },
                    ) { Text(stringResource(R.string.account_device_unbind)) }
                }

                if (state.isLoggedIn) {
                    val text =
                        if (devicesRemote.isEmpty()) stringResource(R.string.account_device_remote_empty)
                        else devicesRemote.joinToString(separator = "\n")
                    Text(
                        text = stringResource(R.string.account_device_remote_list) + "\n" + text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                if (!status.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(status!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
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
