package app.zhixu.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun HomeSubBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp,
    labels: List<String>,
) {
    val verticalPadding = 12.dp
    val controlHeight = (height - verticalPadding * 2).coerceAtLeast(0.dp)
    val segments = labels.ifEmpty { listOf("") }
    val listState = rememberLazyListState()

    Surface(
        modifier = modifier.fillMaxWidth().height(height),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        val safeSelectedIndex = selectedIndex.coerceIn(0, (segments.size - 1).coerceAtLeast(0))
        LaunchedEffect(safeSelectedIndex, segments.size) {
            runCatching { listState.animateScrollToItem(safeSelectedIndex) }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = verticalPadding),
            ) {
                items(segments.size) { idx ->
                    HomeSubBarSegment(
                        label = segments[idx],
                        selected = safeSelectedIndex == idx,
                        onClick = { onSelect(idx) },
                        modifier = Modifier.padding(end = if (idx == segments.lastIndex) 0.dp else 8.dp),
                        height = controlHeight,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSubBarSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val shape = RoundedCornerShape(4.dp)
    val unselectedBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val unselectedText = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF6B7280)
    val selectedBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFE5E7EB)
    val selectedText = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF111827)
    val stroke = if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE5E7EB)
    val bg = if (selected) selectedBg else unselectedBg
    val fg = if (selected) selectedText else unselectedText

    Surface(
        modifier = modifier.height(height).clip(shape),
        color = bg,
        contentColor = fg,
        shape = shape,
        border = BorderStroke(1.dp, stroke),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp)
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
