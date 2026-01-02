package app.zhixu.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.data.VaultRepository
import app.zhixu.ui.DocListMutation
import app.zhixu.ui.components.VaultDrawer

@Composable
fun SpaceScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    isActive: Boolean,
    refreshToken: Long,
    mutation: DocListMutation?,
    onOpenDoc: (String) -> Unit,
    onChangeVault: () -> Unit,
) {
    val root = vaultRootUri
    if (root == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.workshop_no_vault),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onChangeVault),
            )
        }
        return
    }

    VaultDrawer(
        vaultRootUri = root,
        repository = repository,
        onOpenDoc = onOpenDoc,
        onCloseDrawer = {},
        isActive = isActive,
        refreshToken = refreshToken,
        mutation = mutation,
        sheetWidth = Dp.Unspecified,
        contentPadding = contentPadding,
        useSystemInsets = false,
        showHeader = false,
        itemMinHeight = 44.dp,
        itemTextStyle = MaterialTheme.typography.bodyLarge,
        itemIconSize = 22.dp,
        itemChevronSize = 20.dp,
        modifier = Modifier.fillMaxSize(),
    )
}
