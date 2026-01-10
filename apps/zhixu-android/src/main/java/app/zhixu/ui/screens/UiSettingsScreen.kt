package app.zhixu.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhixu.R
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuSwitch
import app.zhixu.data.UiPreferences
import app.zhixu.data.UiSettings
import app.zhixu.data.UiThemeMode
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiPrefs = remember(context) { UiPreferences(context.applicationContext) }
    val uiSettings by
        uiPrefs.settings.collectAsState(
            initial =
                UiSettings(
                    languageTag = "",
                    themeMode = UiThemeMode.SYSTEM,
                    strictDocListPreview = true,
                ),
        )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.settings_placeholder_ui), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
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
        LazyColumn(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize()
                    .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                Text(
                    text = stringResource(R.string.settings_ui_theme_title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    @Composable
                    fun ModeCard(
                        mode: UiThemeMode,
                        label: String,
                        icon: androidx.compose.ui.graphics.vector.ImageVector,
                    ) {
                        val selected = uiSettings.themeMode == mode
                        val borderColor =
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Surface(
                            modifier =
                                Modifier
                                    .size(width = 96.dp, height = 76.dp)
                                    .clickable { scope.launch { uiPrefs.setThemeMode(mode) } },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(2.dp, borderColor),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(10.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(text = label, color = tint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ModeCard(UiThemeMode.SYSTEM, stringResource(R.string.settings_ui_theme_system), Icons.Outlined.Android)
                        ModeCard(UiThemeMode.LIGHT, stringResource(R.string.settings_ui_theme_light), Icons.Outlined.LightMode)
                        ModeCard(UiThemeMode.DARK, stringResource(R.string.settings_ui_theme_dark), Icons.Outlined.DarkMode)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }

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
                                .clickable { scope.launch { uiPrefs.setStrictDocListPreview(!uiSettings.strictDocListPreview) } },
                        leadingContent = {
                            Icon(
                                painter = painterResource(Ionicons.EyeOutline),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.settings_ui_strict_preview_title)) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.settings_ui_strict_preview_subtitle),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            ZhixuSwitch(
                                checked = uiSettings.strictDocListPreview,
                                onCheckedChange = { checked -> scope.launch { uiPrefs.setStrictDocListPreview(checked) } },
                            )
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(18.dp)) }

            item {
                Text(
                    text = stringResource(R.string.settings_language_title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }

            item {
                data class LanguageOption(val tag: String, val label: String)
                val options =
                    listOf(
                        LanguageOption("", context.getString(R.string.settings_language_system)),
                        LanguageOption("zh-CN", context.getString(R.string.settings_language_zh_cn)),
                        LanguageOption("en", context.getString(R.string.settings_language_en)),
                    )
                val selectedTag = uiSettings.languageTag.trim()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    options.forEachIndexed { index, opt ->
                        val isSelected = selectedTag == opt.tag || (opt.tag.isBlank() && selectedTag.isBlank())
                        ListItem(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { scope.launch { uiPrefs.setLanguageTag(opt.tag) } },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.LanguageOutline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            headlineContent = { Text(opt.label) },
                            trailingContent = {
                                if (isSelected) {
                                    Icon(
                                        painter = painterResource(Ionicons.CheckmarkCircle),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        )
                        if (index != options.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
