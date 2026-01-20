package app.zhixu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.zhixu.ui.Ionicons

private data class FeatureEntry(
    val title: String,
    val onClick: () -> Unit,
)

@Composable
fun HomeFeatureHubScreen(
    contentPadding: PaddingValues,
    onOpenSpace: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenPomodoro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
    val entries =
        listOf(
            FeatureEntry(title = "空间管理", onClick = onOpenSpace),
            FeatureEntry(title = "待办列表", onClick = onOpenTasks),
            FeatureEntry(title = "番茄计时", onClick = onOpenPomodoro),
        )

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(contentPadding).background(listBg),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(entries, key = { it.title }) { entry ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                ListItem(
                    modifier = Modifier.clickable(onClick = entry.onClick),
                    headlineContent = { Text(entry.title) },
                    trailingContent = {
                        Icon(
                            painter = painterResource(Ionicons.ChevronForward),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}
