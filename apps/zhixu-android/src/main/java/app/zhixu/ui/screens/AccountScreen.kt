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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuPasswordToggleIconButton
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.data.AccountPreferences
import app.zhixu.data.AccountState
import app.zhixu.sync.OfficialSync
import app.zhixu.sync.SyncServerClient
import app.zhixu.sync.SyncServerResult
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import kotlinx.coroutines.launch
import app.zhixu.ui.components.ZhixuTopAppBar

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
    val loginFailedText = stringResource(R.string.account_login_failed)
    val fetchProfileFailedText = stringResource(R.string.account_fetch_profile_failed)
    val registerFailedText = stringResource(R.string.account_register_failed)
    val serverUnreachableText = stringResource(R.string.error_server_unreachable)
    val syncTitle = stringResource(R.string.account_sync_title)
    val syncDesc = stringResource(R.string.account_sync_desc)
    val registerHintText = stringResource(R.string.account_register_hint)
    val loginToChoosePlanText = stringResource(R.string.account_storage_login_to_choose)
    val recommendedText = stringResource(R.string.account_storage_recommended)

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
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.account_manage_title), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(if (isRtl) app.zhixu.ui.Ionicons.ArrowForward else app.zhixu.ui.Ionicons.ArrowBack),
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = syncTitle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = syncDesc,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                        ),
                    shape = RoundedCornerShape(16.dp),
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
            }

            item {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !busy && username.trim().isNotBlank() && password.isNotBlank(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
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
                        verticalAlignment = Alignment.CenterVertically,
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
            }

            item { HorizontalDivider(color = dividerColor, modifier = Modifier.padding(top = 8.dp)) }

            item {
                Text(text = stringResource(R.string.account_storage_title), style = MaterialTheme.typography.titleSmall)
            }

            fun planCard(code: String, title: String, price: String, desc: String, recommended: Boolean) {
                item {
                    val selected = currentPlanCode == code
                    val containerColor =
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            CardDefaults.outlinedCardColors().containerColor
                        }

                    suspend fun selectPlan() {
                        if (!state.isLoggedIn || busy || selected) return
                        setBusy(true)
                        val r = SyncServerClient.setSubscriptionPlan(OfficialSync.BASE_URL, state.token, code)
                        if (r.ok) currentPlanCode = code
                        status = if (r.ok) null else r.toUiMessage("Failed")
                        setBusy(false)
                    }

                    if (state.isLoggedIn) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { scope.launch { selectPlan() } },
                            colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(text = title, style = MaterialTheme.typography.titleSmall)
                                        if (recommended) {
                                            Text(
                                                text = recommendedText,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(text = price, style = MaterialTheme.typography.titleSmall)
                                if (selected) {
                                    Icon(
                                        painter = painterResource(Ionicons.CheckmarkCircle),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(text = title, style = MaterialTheme.typography.titleSmall)
                                        if (recommended) {
                                            Text(
                                                text = recommendedText,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(text = price, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = loginToChoosePlanText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            planCard("storage_512m", plan512Title, plan512Price, plan512Desc, recommended = false)
            planCard("storage_1g", plan1Title, plan1Price, plan1Desc, recommended = true)
            planCard("storage_2g", plan2Title, plan2Price, plan2Desc, recommended = false)

            if (!status.isNullOrBlank()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(status!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

        }
    }
}
