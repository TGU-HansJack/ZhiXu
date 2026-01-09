package app.zhixu.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.data.AccountPreferences
import app.zhixu.data.AccountState
import app.zhixu.ui.components.ZhixuDialogDefaults
import app.zhixu.ui.components.ZhixuPasswordToggleIconButton
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.sync.OfficialSync
import app.zhixu.sync.SyncServerClient
import app.zhixu.sync.SyncServerResult
import kotlinx.coroutines.launch

@Composable
fun AccountManagementDialog(
    accountPrefs: AccountPreferences,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, email = "", avatarUri = ""),
    )

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val loginOkText = stringResource(R.string.account_login_ok)
    val registerOkText = stringResource(R.string.account_register_ok)
    val loggedOutText = stringResource(R.string.account_logged_out)
    val loginFailedText = stringResource(R.string.account_login_failed)
    val fetchProfileFailedText = stringResource(R.string.account_fetch_profile_failed)
    val registerFailedText = stringResource(R.string.account_register_failed)
    val serverUnreachableText = stringResource(R.string.error_server_unreachable)
    val syncTitle = stringResource(R.string.account_sync_title)
    val syncDesc = stringResource(R.string.account_sync_desc)
    val registerHintText = stringResource(R.string.account_register_hint)

    fun <T> SyncServerResult<T>.toUiMessage(fallback: String): String {
        return when {
            statusCode == 0 || errorMessage == "NETWORK_UNREACHABLE" -> serverUnreachableText
            !errorMessage.isNullOrBlank() -> errorMessage!!
            else -> fallback
        }
    }

    LaunchedEffect(state.username) {
        if (username.isBlank() && state.username.isNotBlank()) username = state.username
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = syncTitle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = syncDesc,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                        ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ZhixuTextField(
                            value = username,
                            onValueChange = { username = it },
                            enabled = !busy,
                            label = { Text(stringResource(R.string.account_username)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ZhixuTextField(
                            value = password,
                            onValueChange = { password = it },
                            enabled = !busy,
                            label = { Text(stringResource(R.string.account_password)) },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                ZhixuPasswordToggleIconButton(
                                    show = showPassword,
                                    enabled = !busy,
                                    onClick = { showPassword = !showPassword },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !busy && username.trim().isNotBlank() && password.isNotBlank(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        onClick = {
                            val u = username.trim()
                            val p = password
                            scope.launch {
                                setBusy(true)
                                val login = SyncServerClient.login(OfficialSync.BASE_URL, u, p)
                                if (!login.ok || login.value.isNullOrBlank()) {
                                    status = login.toUiMessage(loginFailedText)
                                    setBusy(false)
                                    return@launch
                                }
                                val token = login.value!!
                                val me = SyncServerClient.me(OfficialSync.BASE_URL, token)
                                if (!me.ok || me.value == null) {
                                    status = me.toUiMessage(fetchProfileFailedText)
                                    setBusy(false)
                                    return@launch
                                }
                                accountPrefs.setLoggedIn(token = token, username = me.value.username, userId = me.value.userId)
                                status = loginOkText
                                setBusy(false)
                            }
                        },
                    ) { Text(stringResource(R.string.account_login)) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        TextButton(
                            enabled = !busy && username.trim().isNotBlank() && password.isNotBlank(),
                            onClick = {
                                val u = username.trim()
                                val p = password
                                scope.launch {
                                    setBusy(true)
                                    val reg = SyncServerClient.register(OfficialSync.BASE_URL, u, p)
                                    status = if (reg.ok) registerOkText else reg.toUiMessage(registerFailedText)
                                    setBusy(false)
                                }
                            },
                        ) { Text(registerHintText) }

                        Spacer(Modifier.weight(1f))

                        if (state.isLoggedIn) {
                            TextButton(
                                enabled = !busy,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
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
