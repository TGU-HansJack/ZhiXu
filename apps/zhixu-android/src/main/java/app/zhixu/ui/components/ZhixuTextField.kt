package app.zhixu.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.zhixu.ui.Ionicons

object ZhixuTextFieldDefaults {
    val height = 48.dp
    val shape: Shape = RoundedCornerShape(8.dp)
}

@Composable
fun ZhixuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = ZhixuTextFieldDefaults.height,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    isError: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = ZhixuTextFieldDefaults.shape,
    textStyle: TextStyle = LocalTextStyle.current,
    cursorBrush: SolidColor = SolidColor(MaterialTheme.colorScheme.primary),
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor =
        when {
            !enabled -> Color(0xFFE6E6E6)
            isError -> Color(0xFFE57373)
            focused -> MaterialTheme.colorScheme.primary
            else -> Color(0xFFD0D0D0)
        }

    val resolvedTextColor =
        if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    val labelTextStyle =
        MaterialTheme.typography.labelMedium.merge(
            TextStyle(
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            CompositionLocalProvider(LocalTextStyle provides labelTextStyle) { label() }
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            textStyle = textStyle.merge(TextStyle(color = resolvedTextColor, fontWeight = FontWeight.Normal)),
            cursorBrush = cursorBrush,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .border(width = 1.dp, color = borderColor, shape = shape)
                    .clip(shape)
                    .padding(horizontal = 12.dp),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Box(modifier = Modifier.padding(end = 8.dp)) { leadingIcon() }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            val placeholderStyle =
                                MaterialTheme.typography.bodyMedium.merge(
                                    TextStyle(
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    ),
                                )
                            CompositionLocalProvider(LocalTextStyle provides placeholderStyle) { placeholder() }
                        }
                        innerTextField()
                    }

                    if (trailingIcon != null) {
                        Box(modifier = Modifier.padding(start = 8.dp)) { trailingIcon() }
                    }
                }
            },
        )
    }
}

@Composable
fun ZhixuPasswordToggleIconButton(
    show: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ZhixuIconButton(enabled = enabled, onClick = onClick) {
        Icon(
            painter = painterResource(if (show) Ionicons.EyeOffOutline else Ionicons.EyeOutline),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
