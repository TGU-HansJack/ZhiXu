package app.zhixu.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.data.VaultRepository
import app.zhixu.ui.DocListMutation
import app.zhixu.ui.Heroicons
import app.zhixu.ui.Ionicons
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.VaultDrawer

@Composable
fun SpaceScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    isActive: Boolean,
    refreshToken: Long,
    mutation: DocListMutation?,
    onDocListMutated: (DocListMutation) -> Unit,
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

    val context = LocalContext.current
    var isTreeExpanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        SpaceTopToolbar(
            isExpanded = isTreeExpanded,
            onUpload = {
                Toast.makeText(context, context.getString(R.string.common_not_implemented), Toast.LENGTH_SHORT).show()
            },
            onNewFolder = {
                Toast.makeText(context, context.getString(R.string.common_not_implemented), Toast.LENGTH_SHORT).show()
            },
            onFilter = {
                Toast.makeText(context, context.getString(R.string.common_not_implemented), Toast.LENGTH_SHORT).show()
            },
            onToggleExpanded = {
                isTreeExpanded = !isTreeExpanded
            },
            onSearch = {
                Toast.makeText(context, context.getString(R.string.common_not_implemented), Toast.LENGTH_SHORT).show()
            },
        )

        VaultDrawer(
            vaultRootUri = root,
            repository = repository,
            onOpenDoc = onOpenDoc,
            onCloseDrawer = {},
            isActive = isActive,
            refreshToken = refreshToken,
            mutation = mutation,
            onDocListMutated = onDocListMutated,
            sheetWidth = Dp.Unspecified,
            contentPadding = PaddingValues(0.dp),
            useSystemInsets = false,
            showHeader = false,
            itemMinHeight = 40.dp,
            itemTextStyle = MaterialTheme.typography.bodyMedium,
            itemIconSize = 20.dp,
            itemChevronSize = 18.dp,
            allDirsExpanded = isTreeExpanded,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun SpaceTopToolbar(
    isExpanded: Boolean,
    onUpload: () -> Unit,
    onNewFolder: () -> Unit,
    onFilter: () -> Unit,
    onToggleExpanded: () -> Unit,
    onSearch: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val buttonSize = 32.dp
                val iconSize = 20.dp

                ZhixuIconButton(onClick = onUpload, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        painter = painterResource(Heroicons.ArrowUpTray),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize),
                    )
                }
                ZhixuIconButton(onClick = onNewFolder, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        painter = painterResource(Heroicons.FolderPlus),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize),
                    )
                }
                ZhixuIconButton(onClick = onFilter, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        painter = painterResource(Heroicons.Funnel),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize),
                    )
                }
                ZhixuIconButton(onClick = onToggleExpanded, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        painter =
                            painterResource(
                                if (isExpanded) Ionicons.ChevronCollapseOutline else Ionicons.ChevronExpandOutline,
                            ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                ZhixuIconButton(onClick = onSearch, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        painter = painterResource(Ionicons.Search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        }
    }
}
