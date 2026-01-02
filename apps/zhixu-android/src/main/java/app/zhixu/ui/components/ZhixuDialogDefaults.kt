package app.zhixu.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

object ZhixuDialogDefaults {
    val edgePadding = 12.dp
    val properties = DialogProperties(usePlatformDefaultWidth = false)

    fun modifier(modifier: Modifier = Modifier): Modifier = modifier.fillMaxWidth().padding(horizontal = edgePadding)
}

