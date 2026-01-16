package app.zhixu.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.data.AccountPreferences
import app.zhixu.sync.OfficialSync
import app.zhixu.sync.SyncServerClient
import app.zhixu.sync.SyncServerResult
import app.zhixu.ui.components.ZhixuPasswordToggleIconButton
import app.zhixu.ui.components.ZhixuTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AuthForm(
    accountPrefs: AccountPreferences,
    mode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
    modifier: Modifier = Modifier,
    onAuthed: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var sendCooldown by remember { mutableIntStateOf(0) }

    val loginOkText = stringResource(R.string.account_login_ok)
    val registerOkText = stringResource(R.string.account_register_ok)
    val loginFailedText = stringResource(R.string.account_login_failed)
    val registerFailedText = stringResource(R.string.account_register_failed)
    val sendCodeFailedText = stringResource(R.string.account_send_code_failed)
    val serverUnreachableText = stringResource(R.string.error_server_unreachable)

    fun <T> SyncServerResult<T>.toUiMessage(fallback: String): String {
        return when {
            statusCode == 0 || errorMessage == "NETWORK_UNREACHABLE" -> serverUnreachableText
            !errorMessage.isNullOrBlank() -> errorMessage!!
            else -> fallback
        }
    }

    LaunchedEffect(sendCooldown) {
        if (sendCooldown <= 0) return@LaunchedEffect
        delay(1000)
        sendCooldown -= 1
    }

    fun setBusy(on: Boolean) {
        busy = on
        if (on) status = null
    }

    @Composable
    fun FlatOutlinedField(
        label: String,
        value: String,
        onValueChange: (String) -> Unit,
        enabled: Boolean = true,
        keyboardType: KeyboardType = KeyboardType.Text,
        visualTransformation: VisualTransformation = VisualTransformation.None,
        trailing: (@Composable () -> Unit)? = null,
        modifier: Modifier = Modifier,
    ) {
        ZhixuTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            label = { Text(label) },
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            trailingIcon = trailing,
            modifier = modifier,
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (mode == AuthMode.Register) {
            FlatOutlinedField(
                label = stringResource(R.string.account_username),
                value = username,
                onValueChange = { username = it },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            FlatOutlinedField(
                label = stringResource(R.string.account_email),
                value = email,
                onValueChange = { email = it },
                enabled = !busy,
                keyboardType = KeyboardType.Email,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.account_email_code),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZhixuTextField(
                        value = emailCode,
                        onValueChange = { emailCode = it },
                        enabled = !busy,
                        label = null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(
                        enabled = !busy && sendCooldown == 0 && email.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        onClick = {
                            scope.launch {
                                setBusy(true)
                                try {
                                    val sent =
                                        SyncServerClient.sendEmailCode(
                                            baseUrl = OfficialSync.BASE_URL,
                                            email = email.trim(),
                                            purpose = "register",
                                        )
                                    if (!sent.ok) {
                                        status = sent.toUiMessage(sendCodeFailedText)
                                        return@launch
                                    }
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.account_email_code_sent_placeholder),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    sendCooldown = 60
                                } finally {
                                    setBusy(false)
                                }
                            }
                        },
                    ) {
                        Text(
                            if (sendCooldown > 0) "${sendCooldown}s" else stringResource(R.string.account_send_code),
                        )
                    }
                }
            }
            FlatOutlinedField(
                label = stringResource(R.string.account_password),
                value = password,
                onValueChange = { password = it },
                enabled = !busy,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    ZhixuPasswordToggleIconButton(
                        show = showPassword,
                        onClick = { showPassword = !showPassword },
                        enabled = !busy,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && username.isNotBlank() && email.isNotBlank() && emailCode.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
                onClick = {
                    scope.launch {
                        setBusy(true)
                        try {
                            val reg =
                                SyncServerClient.register(
                                    baseUrl = OfficialSync.BASE_URL,
                                    username = username.trim(),
                                    password = password,
                                    email = email.trim(),
                                    emailCode = emailCode.trim(),
                                )
                            if (!reg.ok) {
                                status = reg.toUiMessage(registerFailedText)
                                return@launch
                            }
                            Toast.makeText(context, registerOkText, Toast.LENGTH_SHORT).show()

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

                            onAuthed()
                        } finally {
                            setBusy(false)
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.account_create_account))
            }

            TextButton(enabled = !busy, onClick = { onModeChange(AuthMode.Login) }) {
                Text(stringResource(R.string.account_have_account_login))
            }
        } else {
            FlatOutlinedField(
                label = stringResource(R.string.account_username),
                value = username,
                onValueChange = { username = it },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            FlatOutlinedField(
                label = stringResource(R.string.account_password),
                value = password,
                onValueChange = { password = it },
                enabled = !busy,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    ZhixuPasswordToggleIconButton(
                        show = showPassword,
                        onClick = { showPassword = !showPassword },
                        enabled = !busy,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
                onClick = {
                    scope.launch {
                        setBusy(true)
                        try {
                            val login = SyncServerClient.login(OfficialSync.BASE_URL, username.trim(), password)
                            if (!login.ok || login.value.isNullOrBlank()) {
                                status = login.toUiMessage(loginFailedText)
                                return@launch
                            }
                            val token = login.value!!
                            val me = SyncServerClient.me(OfficialSync.BASE_URL, token)
                            val userId = me.value?.userId ?: 0L
                            val resolvedUsername = me.value?.username?.ifBlank { username.trim() } ?: username.trim()
                            val resolvedEmail = me.value?.email.orEmpty()
                            accountPrefs.setLoggedIn(token = token, username = resolvedUsername, userId = userId, email = resolvedEmail)
                            if (resolvedEmail.isNotBlank()) accountPrefs.setEmail(resolvedEmail)
                            Toast.makeText(context, loginOkText, Toast.LENGTH_SHORT).show()
                            onAuthed()
                        } finally {
                            setBusy(false)
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.account_login))
            }
            TextButton(enabled = !busy, onClick = { onModeChange(AuthMode.Register) }) {
                Text(stringResource(R.string.account_no_account_create))
            }
        }

        if (!status.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(status!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

enum class AuthMode { Login, Register }
