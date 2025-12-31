package com.zhixu.android.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zhixu.android.ui.components.ZhixuTextField
import com.zhixu.android.R
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.ui.Ionicons
import com.zhixu.android.sync.VaultAutoSync
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDocScreen(
    vaultRootUri: Uri,
    repository: VaultRepository,
    onCreated: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var fileName by remember { mutableStateOf("New Note") }
    var initialContent by remember { mutableStateOf("") }

    fun buildDefaultContent(name: String): String {
        val title = name.removeSuffix(".md").trim().ifBlank { context.getString(R.string.new_doc_default_title) }
        return "# $title\n\n"
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    windowInsets = TopAppBarDefaults.windowInsets,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    title = { Text(stringResource(R.string.new_doc_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(if (LocalLayoutDirection.current == LayoutDirection.Rtl) Ionicons.ArrowForward else Ionicons.ArrowBack),
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val trimmed = fileName.trim()
                                    if (trimmed.isBlank()) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.new_doc_error_name_required))
                                        return@launch
                                    }

                                    val created = runCatching { repository.createDoc(vaultRootUri, trimmed) }
                                        .getOrElse { e ->
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.new_doc_error_create_failed, e.message ?: e.javaClass.simpleName),
                                            )
                                            return@launch
                                        }

                                    val contentToWrite = initialContent.ifBlank { buildDefaultContent(created.name) }
                                    runCatching { repository.writeText(created.uri, contentToWrite) }
                                        .onFailure {
                                            snackbarHostState.showSnackbar(context.getString(R.string.new_doc_error_write_failed))
                                        }

                                    runCatching {
                                        VaultAutoSync.maybeUploadDoc(
                                            context = context,
                                            repository = repository,
                                            vaultRootUri = vaultRootUri,
                                            docUri = created.uri,
                                            force = true,
                                        )
                                    }

                                    onCreated(created.uri.toString())
                                }
                            },
                        ) {
                            Icon(painter = painterResource(Ionicons.Checkmark), contentDescription = stringResource(R.string.action_create))
                            Text(stringResource(R.string.action_create), modifier = Modifier.padding(start = 6.dp))
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ZhixuTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text(stringResource(R.string.field_file_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.new_doc_initial_content))
            BasicTextField(
                value = initialContent,
                onValueChange = { initialContent = it },
                modifier = Modifier
                    .fillMaxSize(),
                textStyle = TextStyle.Default.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}
