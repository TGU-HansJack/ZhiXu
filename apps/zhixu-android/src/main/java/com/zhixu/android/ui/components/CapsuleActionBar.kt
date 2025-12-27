package com.zhixu.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CapsuleActionBar(
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    onAdd: () -> Unit,
    onAi: () -> Unit,
) {
    val barHeight = 52.dp
    val buttonSize = 52.dp
    val aiBadgeSize = 30.dp
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFFFFFFFF),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.height(barHeight).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (onSearch != null) {
                IconButton(onClick = onSearch, modifier = Modifier.size(buttonSize)) {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                }
                CapsuleDivider()
            }
            IconButton(onClick = onAdd, modifier = Modifier.size(buttonSize)) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
            }
            CapsuleDivider()
            IconButton(onClick = onAi, modifier = Modifier.size(buttonSize)) {
                Box(
                    modifier =
                        Modifier
                            .size(aiBadgeSize)
                            .background(color = Color(0xFF00C853), shape = RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "AI", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CapsuleDivider() {
    Box(
        modifier =
            Modifier
                .padding(vertical = 10.dp)
                .fillMaxHeight()
                .size(width = 1.dp, height = 30.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
    )
}
