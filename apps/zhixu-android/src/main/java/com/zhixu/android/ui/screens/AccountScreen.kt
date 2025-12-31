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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.zhixu.android.ui.components.ZhixuPasswordToggleIconButton
import com.zhixu.android.ui.components.ZhixuTextField
import com.zhixu.android.data.AccountPreferences
import com.zhixu.android.data.AccountState
import com.zhixu.android.sync.OfficialSync
import com.zhixu.android.sync.SyncServerClient
import com.zhixu.android.sync.SyncServerResult
import com.zhixu.android.ui.Ionicons
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
        initial = AccountState(token = "", username = "", userId = 0L),
    )

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var currentPlanCode by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(state.username) {
        if (username.isBlank() && state.username.isNotBlank()) username = state.username
    }

    fun setBusy(on: Boolean) {
        busy = on
        if (on) status = null
    }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val plan512Title = stringResource(R.string.account_storage_512m_title)
    val plan512Price = stringResource(R.string.account_storage_512m_price)
    val plan512Desc = stringResource(R.string.account_storage_512m_desc)
    val plan1Title = stringResource(R.string.account_storage_1g_title)
    val plan1Price = stringResource(R.string.account_storage_1g_price)
    val plan1Desc = stringResource(R.string.account_storage_1g_desc)
    val plan2Title = stringResource(R.string.account_storage_2g_title)
    val plan2Price = stringResource(R.string.account_storage_2g_price)
    val plan2Desc = stringResource(R.string.account_storage_2g_desc)

    LaunchedEffect(state.token) {
        if (!state.isLoggedIn) {
            currentPlanCode = null
            return@LaunchedEffect
        }
        val me = SyncServerClient.me(OfficialSync.BASE_URL, state.token)
        if (me.ok) currentPlanCode = me.value?.plan?.code
    }

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
        val layoutDirection = LocalLayoutDirection.current
        val outerPadding =
            PaddingValues(
                start = contentPadding.calculateLeftPadding(layoutDirection),
                top = 0.dp,
                end = contentPadding.calculateRightPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding(),
            )
        LazyColumn(
            modifier =
                Modifier
                    .padding(outerPadding)
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
                ZhixuTextField(
                    value = username,
                    onValueChange = { username = it },
                    enabled = !busy,
                    label = { Text(stringResource(R.string.account_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
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
                Text(text = stringResource(R.string.account_storage_title), style = MaterialTheme.typography.titleSmall)
            }

            fun planCard(code: String, title: String, price: String, desc: String) {
                item {
                    val selected = currentPlanCode == code
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        border =
                            BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(if (selected) Ionicons.CheckmarkCircle else Ionicons.LayersOutline),
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(title, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = price,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = desc,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    enabled = !busy && state.isLoggedIn,
                                    onClick = {
                                        scope.launch {
                                            setBusy(true)
                                            val r = SyncServerClient.setSubscriptionPlan(OfficialSync.BASE_URL, state.token, code)
                                            if (r.ok) currentPlanCode = code
                                            status = if (r.ok) context.getString(R.string.account_storage_selected, title) else r.toUiMessage("Failed")
                                            setBusy(false)
                                        }
                                    },
                                ) { Text(stringResource(R.string.account_storage_select)) }
                            }

                            if (!state.isLoggedIn) {
                                Text(
                                    text = stringResource(R.string.account_storage_login_required),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            planCard("storage_512m", plan512Title, plan512Price, plan512Desc)
            planCard("storage_1g", plan1Title, plan1Price, plan1Desc)
            planCard("storage_2g", plan2Title, plan2Price, plan2Desc)

            if (!status.isNullOrBlank()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(status!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
