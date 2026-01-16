package app.zhixu.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.data.AccountPreferences
import app.zhixu.data.AccountState
import app.zhixu.ui.components.ZhixuDialogDefaults
import kotlinx.coroutines.launch

@Composable
fun AccountManagementDialog(
    accountPrefs: AccountPreferences,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, email = "", avatarUri = ""),
    )

    var authMode by remember { mutableStateOf(AuthMode.Login) }

    AlertDialog(
        modifier = ZhixuDialogDefaults.modifier(),
        onDismissRequest = onDismiss,
        properties = ZhixuDialogDefaults.properties,
        title = { Text(stringResource(R.string.account_manage_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.account_sync_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.account_sync_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (!state.isLoggedIn) {
                            AuthForm(
                                accountPrefs = accountPrefs,
                                mode = authMode,
                                onModeChange = { authMode = it },
                                modifier = Modifier.fillMaxWidth(),
                                onAuthed = onDismiss,
                            )
                        } else {
                            Text(
                                text =
                                    stringResource(
                                        R.string.account_logged_in_as_fmt,
                                        state.username.ifBlank { "Zhixu" },
                                    ),
                            )
                            if (state.email.isNotBlank()) {
                                Text(
                                    text = state.email,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                onClick = {
                                    scope.launch {
                                        accountPrefs.logout()
                                        onDismiss()
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.account_logout))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

