package app.zhixu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.launch

@Composable
fun HomeSubBar(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    height: Dp,
    labels: List<String>,
) {
    val verticalPadding = 12.dp
    val controlHeight = height - verticalPadding * 2
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(999.dp)
    val segments = labels.ifEmpty { listOf("") }

    Surface(
        modifier = modifier.fillMaxWidth().height(height),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = verticalPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .widthIn(max = 280.dp)
                        .height(controlHeight)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)),
            ) {
                val segmentWidth = maxWidth / segments.size
                val progress =
                    (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                        .coerceIn(0f, (segments.size - 1).toFloat().coerceAtLeast(0f))
                val indicatorOffset = segmentWidth * progress

                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(segmentWidth)
                            .offset(x = indicatorOffset, y = 0.dp)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.primary),
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    segments.forEachIndexed { idx, label ->
                        HomeSubBarSegment(
                            label = label,
                            selected = pagerState.currentPage == idx,
                            onClick = { scope.launch { pagerState.animateScrollToPage(idx) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
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
) {
    val textColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier = modifier.fillMaxHeight().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
