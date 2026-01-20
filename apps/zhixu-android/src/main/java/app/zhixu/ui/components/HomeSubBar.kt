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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.launch

@Composable
fun HomeSubBar(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    height: Dp,
    recentLabel: String,
    featureLabel: String,
) {
    // 8dp vertical padding so the control height matches the search bar sizing (56 - 8 - 8 = 40).
    val controlHeight = height - 16.dp
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(999.dp)

    Surface(
        modifier = modifier.fillMaxWidth().height(height),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(controlHeight)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)),
            ) {
                val segmentWidth = maxWidth / 2
                val progress = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
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
                    HomeSubBarSegment(
                        label = recentLabel,
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        modifier = Modifier.weight(1f),
                    )
                    HomeSubBarSegment(
                        label = featureLabel,
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        modifier = Modifier.weight(1f),
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
        )
    }
}
