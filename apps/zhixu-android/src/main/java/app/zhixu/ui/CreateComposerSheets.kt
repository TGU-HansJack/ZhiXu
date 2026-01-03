package app.zhixu.ui

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.zhixu.data.VaultRepository
import app.zhixu.sync.VaultAutoSync
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.screens.NewDocScreen
import app.zhixu.ui.screens.TaskComposer
import kotlinx.coroutines.launch

@Composable
internal fun TodoComposerSheet(
    vaultRootUri: Uri?,
    repository: VaultRepository,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZhixuIconButton(onClick = onBack) {
                androidx.compose.material3.Icon(
                    painter = painterResource(Ionicons.ArrowBack),
                    contentDescription = "返回",
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "新建待办",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            ZhixuIconButton(onClick = onClose) {
                androidx.compose.material3.Icon(
                    painter = painterResource(Ionicons.Close),
                    contentDescription = "关闭",
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        TaskComposer(
            onSubmit = { draft ->
                val root = vaultRootUri
                if (root == null) {
                    android.widget.Toast.makeText(context, "未选择资料库", android.widget.Toast.LENGTH_SHORT).show()
                    return@TaskComposer
                }
                scope.launch {
                    val ok =
                        repository.addTaskToInbox(
                            rootUri = root,
                            title = draft.title.trim(),
                            dueDate = draft.dueDate,
                            dueTime = draft.dueTime,
                            tags = draft.tags,
                            priority = draft.priority,
                        )
                    if (!ok) {
                        android.widget.Toast.makeText(context, "添加失败", android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    runCatching {
                        VaultAutoSync.maybeUploadInbox(
                            context = context,
                            repository = repository,
                            vaultRootUri = root,
                            force = true,
                        )
                    }
                    android.widget.Toast.makeText(context, "已添加", android.widget.Toast.LENGTH_SHORT).show()
                    onClose()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteComposerSheet(
    sheetState: SheetState,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    onBack: () -> Unit,
    onCreated: (app.zhixu.data.UiDoc) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isExpanded = sheetState.currentValue == SheetValue.Expanded || sheetState.targetValue == SheetValue.Expanded
    val heightFraction =
        animateFloatAsState(
            targetValue = if (isExpanded) 1f else 0.66f,
            label = "note_sheet_height_fraction",
        ).value

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction),
    ) {
        val root = vaultRootUri
        if (root == null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("未选择资料库", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Box
        }

        NewDocScreen(
            vaultRootUri = root,
            repository = repository,
            onCreated = onCreated,
            onBack = onBack,
        )
    }
}

