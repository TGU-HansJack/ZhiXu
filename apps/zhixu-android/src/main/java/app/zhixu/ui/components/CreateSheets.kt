package app.zhixu.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.zhixu.R
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description

@Composable
internal fun ZhixuCompactDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.size(width = 34.dp, height = 4.dp),
            content = {},
        )
    }
}

@Composable
internal fun CreateMenuSheetContent(
    onOcr: () -> Unit,
    onRecord: () -> Unit,
    onCamera: () -> Unit,
    onDraw: () -> Unit,
    onNewTodo: () -> Unit,
    onNewNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CreateActionItem(
                iconRes = R.drawable.ic_material_document_scanner,
                label = "OCR识图",
                onClick = onOcr,
                modifier = Modifier.weight(1f),
            )
            CreateActionItem(
                iconRes = R.drawable.ic_hero_microphone,
                label = "录音",
                onClick = onRecord,
                modifier = Modifier.weight(1f),
            )
            CreateActionItem(
                iconRes = R.drawable.ic_ion_camera_outline,
                label = "相机",
                onClick = onCamera,
                modifier = Modifier.weight(1f),
            )
            CreateActionItem(
                iconRes = R.drawable.ic_hero_paint_brush,
                label = "绘画",
                onClick = onDraw,
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                onClick = onNewTodo,
            ) {
                Icon(imageVector = Icons.Outlined.Checklist, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "新建待办", fontWeight = FontWeight.SemiBold)
            }
            Button(
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                onClick = onNewNote,
            ) {
                Icon(imageVector = Icons.Outlined.Description, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "新建笔记", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CreateActionItem(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = shape,
            tonalElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier =
                Modifier
                    .size(56.dp)
                    .clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
