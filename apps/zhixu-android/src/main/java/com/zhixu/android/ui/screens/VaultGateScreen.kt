package com.zhixu.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.zhixu.android.R
import com.zhixu.android.data.VaultStorageLocation
import kotlinx.coroutines.launch

@Composable
fun VaultGateScreen(
    onSelectLocalFolder: suspend (Uri) -> Unit,
    onSelectOfficialServer: suspend () -> Unit,
    onSelectThirdPartyService: suspend () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var location by remember { mutableStateOf(VaultStorageLocation.LOCAL) }
    var status by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }

        scope.launch {
            runCatching { onSelectLocalFolder(uri) }
                .onFailure { status = it.message ?: it.javaClass.simpleName }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.vault_select_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.vault_select_desc),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(12.dp))

        StorageLocationRow(
            selected = location,
            onSelected = { location = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        when (location) {
            VaultStorageLocation.LOCAL -> {
                FilledTonalButton(onClick = { launcher.launch(null) }) {
                    Text(stringResource(R.string.vault_select_button))
                }
            }

            VaultStorageLocation.OFFICIAL_SERVER -> {
                Text(
                    text = stringResource(R.string.vault_settings_official_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            runCatching { onSelectOfficialServer() }
                                .onFailure { status = it.message ?: it.javaClass.simpleName }
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_continue))
                }
            }

            VaultStorageLocation.THIRD_PARTY_SERVICE -> {
                Text(
                    text = stringResource(R.string.vault_settings_third_party_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            runCatching { onSelectThirdPartyService() }
                                .onFailure { status = it.message ?: it.javaClass.simpleName }
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_continue))
                }
            }
        }

        if (!status.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(status!!, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StorageLocationRow(
    selected: VaultStorageLocation,
    onSelected: (VaultStorageLocation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LocationButton(
            selected = selected == VaultStorageLocation.LOCAL,
            text = stringResource(R.string.vault_settings_location_local),
            onClick = { onSelected(VaultStorageLocation.LOCAL) },
            modifier = Modifier.weight(1f),
        )
        LocationButton(
            selected = selected == VaultStorageLocation.OFFICIAL_SERVER,
            text = stringResource(R.string.vault_settings_location_official),
            onClick = { onSelected(VaultStorageLocation.OFFICIAL_SERVER) },
            modifier = Modifier.weight(1f),
        )
        LocationButton(
            selected = selected == VaultStorageLocation.THIRD_PARTY_SERVICE,
            text = stringResource(R.string.vault_settings_location_third_party),
            onClick = { onSelected(VaultStorageLocation.THIRD_PARTY_SERVICE) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LocationButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        FilledTonalButton(modifier = modifier, onClick = onClick) { Text(text) }
    } else {
        OutlinedButton(modifier = modifier, onClick = onClick) { Text(text) }
    }
}
