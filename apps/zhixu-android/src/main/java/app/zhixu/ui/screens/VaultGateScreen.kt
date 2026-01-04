package app.zhixu.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import app.zhixu.R
import kotlinx.coroutines.launch

@Composable
fun VaultGateScreen(
    onSelectLocalFolder: suspend (Uri) -> Unit,
    onSelectOfficialServer: suspend () -> Unit,
    onSelectThirdPartyService: suspend () -> Unit,
    onSelectLocalPrivateDir: (suspend () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "欢迎使用知序",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "知序是一款本地优先的笔记与待办应用",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "数据将存储在应用私有目录中",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        FilledTonalButton(
            enabled = !isLoading,
            onClick = {
                isLoading = true
                scope.launch {
                    val callback = onSelectLocalPrivateDir ?: onSelectOfficialServer
                    runCatching { callback() }
                        .onFailure {
                            status = it.message ?: it.javaClass.simpleName
                            isLoading = false
                        }
                }
            },
        ) {
            Text(if (isLoading) "正在初始化..." else "开始使用")
        }

        if (!status.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(status!!, color = MaterialTheme.colorScheme.error)
        }
    }
}
