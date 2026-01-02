package app.zhixu.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.ui.Ionicons
import kotlinx.coroutines.delay

@Composable
fun RefreshStatusBanner(
    isRefreshing: Boolean,
    lastRefreshedAtMs: Long,
    modifier: Modifier = Modifier,
) {
    var showJustRefreshed by remember { mutableStateOf(false) }

    LaunchedEffect(lastRefreshedAtMs) {
        if (lastRefreshedAtMs <= 0L) return@LaunchedEffect
        showJustRefreshed = true
        delay(1_200)
        showJustRefreshed = false
    }

    val visible = isRefreshing || showJustRefreshed
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(text = stringResource(R.string.pull_refresh_refreshing), style = MaterialTheme.typography.bodySmall)
                } else {
                    Icon(
                        painter = painterResource(Ionicons.CheckmarkCircle),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(text = stringResource(R.string.pull_refresh_updated), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

