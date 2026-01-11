package app.zhixu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhixu.R
import app.zhixu.data.EditorDefaultMode
import app.zhixu.data.EditorPreferences
import app.zhixu.data.EditorSettings
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuSwitch
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { EditorPreferences(context.applicationContext) }
    val settings by
        prefs.settings.collectAsState(
            initial =
                EditorSettings(
                    defaultMode = EditorDefaultMode.LIVE_PREVIEW,
                    showNoteProperties = true,
                    showLineNumbers = false,
                    showEditorToolbar = true,
                ),
        )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.editor_title), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(Ionicons.ChevronBack),
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { innerPadding ->
        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        LazyColumn(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize()
                    .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        ) {
            item {
                SettingsSectionTitle(text = stringResource(R.string.editor_settings_section_mode))
            }

            item {
                var showModeMenu by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    val modeLabel =
                        when (settings.defaultMode) {
                            EditorDefaultMode.LIVE_PREVIEW -> stringResource(R.string.editor_settings_mode_live_preview)
                            EditorDefaultMode.SOURCE -> stringResource(R.string.editor_settings_mode_source)
                        }

                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showModeMenu = true },
                            headlineContent = { Text(stringResource(R.string.editor_settings_default_mode_title)) },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.editor_settings_default_mode_subtitle),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                androidx.compose.foundation.layout.Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = modeLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Icon(
                                        painter = painterResource(Ionicons.ChevronDown),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 6.dp).size(18.dp),
                                    )
                                }
                            },
                        )
                        DropdownMenu(
                            expanded = showModeMenu,
                            onDismissRequest = { showModeMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_settings_mode_live_preview)) },
                                onClick = {
                                    showModeMenu = false
                                    scope.launch { prefs.setDefaultMode(EditorDefaultMode.LIVE_PREVIEW) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_settings_mode_source)) },
                                onClick = {
                                    showModeMenu = false
                                    scope.launch { prefs.setDefaultMode(EditorDefaultMode.SOURCE) }
                                },
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }

            item {
                SettingsSectionTitle(text = stringResource(R.string.editor_settings_section_display))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    ListItem(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { prefs.setShowNoteProperties(!settings.showNoteProperties) } },
                        headlineContent = { Text(stringResource(R.string.editor_settings_note_properties_visible_title)) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.editor_settings_note_properties_visible_subtitle),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            ZhixuSwitch(
                                checked = settings.showNoteProperties,
                                onCheckedChange = { checked -> scope.launch { prefs.setShowNoteProperties(checked) } },
                            )
                        },
                    )

                    HorizontalDivider(color = dividerColor)
                    ListItem(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { prefs.setShowLineNumbers(!settings.showLineNumbers) } },
                        headlineContent = { Text(stringResource(R.string.editor_settings_show_line_numbers_title)) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.editor_settings_show_line_numbers_subtitle),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            ZhixuSwitch(
                                checked = settings.showLineNumbers,
                                onCheckedChange = { checked -> scope.launch { prefs.setShowLineNumbers(checked) } },
                            )
                        },
                    )

                    HorizontalDivider(color = dividerColor)
                    ListItem(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { prefs.setShowEditorToolbar(!settings.showEditorToolbar) } },
                        headlineContent = { Text(stringResource(R.string.editor_settings_editor_toolbar_visible_title)) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.editor_settings_editor_toolbar_visible_subtitle),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            ZhixuSwitch(
                                checked = settings.showEditorToolbar,
                                onCheckedChange = { checked -> scope.launch { prefs.setShowEditorToolbar(checked) } },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

