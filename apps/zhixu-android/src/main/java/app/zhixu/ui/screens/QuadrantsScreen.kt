package app.zhixu.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zhixu.data.UiTask
import app.zhixu.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QuadrantsScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    isActive: Boolean,
    onOpenDoc: (String, String?, Int?) -> Unit,
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    var tasks by remember(vaultRootUri) { mutableStateOf<List<UiTask>>(emptyList()) }
    var indexReady by remember(vaultRootUri) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(isActive, vaultRootUri) {
        if (!isActive) return@LaunchedEffect
        val root = vaultRootUri ?: return@LaunchedEffect
        val (ready, list) =
            withContext(Dispatchers.IO) {
                val r = runCatching { repository.hasAnyIndexedDocs() }.getOrDefault(false)
                if (!r) {
                    false to emptyList()
                } else {
                    true to repository.getAllTasks(limit = 800)
                }
            }
        indexReady = ready
        tasks = list
    }

    val byPriority = remember(tasks) { tasks.groupBy { it.priority } }
    val p1 = byPriority[1].orEmpty()
    val p2 = byPriority[2].orEmpty()
    val p3 = byPriority[3].orEmpty()
    val p4 = byPriority[4].orEmpty()
    val unranked = byPriority[null].orEmpty()

    Column(
        modifier =
            Modifier
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (indexReady == false) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "请先在“任务”页构建索引后再查看四象限",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuadrantCard(
                title = "P1",
                subtitle = "最高优先级",
                tasks = p1,
                modifier = Modifier.weight(1f),
                onOpen = { t -> onOpenDoc(t.docUri.toString(), null, t.lineIndex) },
            )
            QuadrantCard(
                title = "P2",
                subtitle = "高优先级",
                tasks = p2,
                modifier = Modifier.weight(1f),
                onOpen = { t -> onOpenDoc(t.docUri.toString(), null, t.lineIndex) },
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuadrantCard(
                title = "P3",
                subtitle = "中优先级",
                tasks = p3,
                modifier = Modifier.weight(1f),
                onOpen = { t -> onOpenDoc(t.docUri.toString(), null, t.lineIndex) },
            )
            QuadrantCard(
                title = "P4",
                subtitle = "低优先级",
                tasks = p4,
                modifier = Modifier.weight(1f),
                onOpen = { t -> onOpenDoc(t.docUri.toString(), null, t.lineIndex) },
            )
        }

        if (unranked.isNotEmpty()) {
            HorizontalDivider(color = dividerColor)
            Text(
                text = "未设置优先级（${unranked.size}）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in unranked.take(12)) {
                    Text(
                        text = t.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenDoc(t.docUri.toString(), null, t.lineIndex) },
                    )
                }
                val remaining = (unranked.size - 12).coerceAtLeast(0)
                if (remaining > 0) {
                    Text(
                        text = "还有 $remaining 项…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuadrantCard(
    title: String,
    subtitle: String,
    tasks: List<UiTask>,
    modifier: Modifier = Modifier,
    onOpen: (UiTask) -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = tasks.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
                    Text(text = "暂无任务", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in tasks.take(6)) {
                        Text(
                            text = t.title.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(t) },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    val remaining = (tasks.size - 6).coerceAtLeast(0)
                    if (remaining > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "还有 $remaining 项…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

