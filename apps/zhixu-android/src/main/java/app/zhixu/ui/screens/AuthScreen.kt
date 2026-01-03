package app.zhixu.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import android.widget.Toast
import app.zhixu.R
import app.zhixu.data.AccountPreferences
import app.zhixu.data.AccountState
import app.zhixu.sync.OfficialSync
import app.zhixu.sync.SyncServerClient
import app.zhixu.sync.SyncServerResult
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuPasswordToggleIconButton
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    contentPadding: PaddingValues,
    accountPrefs: AccountPreferences,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, email = "", avatarUri = ""),
    )

    var mode by remember { mutableStateOf(AuthMode.Login) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val loginOkText = stringResource(R.string.account_login_ok)
    val registerOkText = stringResource(R.string.account_register_ok)
    val loginFailedText = stringResource(R.string.account_login_failed)
    val registerFailedText = stringResource(R.string.account_register_failed)
    val serverUnreachableText = stringResource(R.string.error_server_unreachable)

    fun <T> SyncServerResult<T>.toUiMessage(fallback: String): String {
        return when {
            statusCode == 0 || errorMessage == "NETWORK_UNREACHABLE" -> serverUnreachableText
            !errorMessage.isNullOrBlank() -> errorMessage!!
            else -> fallback
        }
    }

    LaunchedEffect(state.username, state.email) {
        if (username.isBlank() && state.username.isNotBlank()) username = state.username
        if (email.isBlank() && state.email.isNotBlank()) email = state.email
    }

    fun setBusy(on: Boolean) {
        busy = on
        if (on) status = null
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.account_login_register), style = MaterialTheme.typography.titleMedium) },
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
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .padding(contentPadding)
                    .fillMaxSize()
                    .imePadding()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    onClick = { mode = AuthMode.Login },
                ) {
                    Text(stringResource(R.string.account_login))
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    onClick = { mode = AuthMode.Register },
                ) {
                    Text(stringResource(R.string.account_register))
                }
            }

            ZhixuTextField(
                value = username,
                onValueChange = { username = it },
                enabled = !busy,
                label = { Text(stringResource(R.string.account_username)) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (mode == AuthMode.Register) {
                ZhixuTextField(
                    value = email,
                    onValueChange = { email = it },
                    enabled = !busy,
                    label = { Text(stringResource(R.string.account_email)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ZhixuTextField(
                value = password,
                onValueChange = { password = it },
                enabled = !busy,
                label = { Text(stringResource(R.string.account_password)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    ZhixuPasswordToggleIconButton(
                        show = showPassword,
                        onClick = { showPassword = !showPassword },
                    )
                },
            )

            if (mode == AuthMode.Register) {
                ZhixuTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    enabled = !busy,
                    label = { Text(stringResource(R.string.account_confirm_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && username.isNotBlank() && password.isNotBlank() && (mode == AuthMode.Login || confirmPassword.isNotBlank()),
                onClick = {
                    scope.launch {
                        setBusy(true)
                        try {
                            if (mode == AuthMode.Register && password != confirmPassword) {
                                status = context.getString(R.string.account_password_mismatch)
                                return@launch
                            }

                            if (mode == AuthMode.Register) {
                                val reg = SyncServerClient.register(OfficialSync.BASE_URL, username.trim(), password, email = email.trim())
                                if (!reg.ok) {
                                    val fallbackReg =
                                        if (email.isNotBlank()) SyncServerClient.register(OfficialSync.BASE_URL, username.trim(), password, email = "")
                                        else reg
                                    if (!fallbackReg.ok) {
                                        status = fallbackReg.toUiMessage(registerFailedText)
                                        return@launch
                                    }
                                }
                                status = registerOkText
                            }

                            val login = SyncServerClient.login(OfficialSync.BASE_URL, username.trim(), password)
                            if (!login.ok || login.value.isNullOrBlank()) {
                                status = login.toUiMessage(loginFailedText)
                                return@launch
                            }
                            val token = login.value!!
                            val me = SyncServerClient.me(OfficialSync.BASE_URL, token)
                            val userId = me.value?.userId ?: 0L
                            val resolvedUsername = me.value?.username?.ifBlank { username.trim() } ?: username.trim()
                            val resolvedEmail = me.value?.email?.ifBlank { email.trim() } ?: email.trim()
                            accountPrefs.setLoggedIn(token = token, username = resolvedUsername, userId = userId, email = resolvedEmail)
                            if (resolvedEmail.isNotBlank()) accountPrefs.setEmail(resolvedEmail)
                            Toast.makeText(context, loginOkText, Toast.LENGTH_SHORT).show()
                            onBack()
                        } finally {
                            setBusy(false)
                        }
                    }
                },
            ) {
                Text(if (mode == AuthMode.Login) stringResource(R.string.account_login) else stringResource(R.string.account_register))
            }

            if (!status.isNullOrBlank()) {
                Text(status!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.account_logged_in_as_fmt, state.username.ifBlank { "-" }),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                if (state.isLoggedIn) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                accountPrefs.logout()
                                Toast.makeText(context, context.getString(R.string.account_logged_out), Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.account_logout))
                    }
                }
            }
        }
    }
}

private enum class AuthMode { Login, Register }
