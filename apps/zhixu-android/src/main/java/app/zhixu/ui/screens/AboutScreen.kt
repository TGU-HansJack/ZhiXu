package app.zhixu.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import app.zhixu.BuildConfig
import app.zhixu.R
import app.zhixu.ui.Ionicons
import app.zhixu.ui.components.ZhixuIconButton

private const val OFFICIAL_SITE_URL = "https://zhixu.app"
private const val OFFICIAL_TOS_URL = "https://zhixu.app/tos"
private const val OFFICIAL_PRIVACY_URL = "https://zhixu.app/privacy"
private const val OFFICIAL_LICENSE_URL = "https://zhixu.app/license"
private const val QQ_GROUP_NUMBER = "556339740"
private const val QQ_GROUP_URI =
    "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$QQ_GROUP_NUMBER&card_type=group&source=external"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenTermsOfUse: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenOpenSourceLicense: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val headerIconSize = 92.dp
    val appIcon =
        androidx.compose.runtime.remember(context) {
            runCatching { context.packageManager.getApplicationIcon(context.packageName) }.getOrNull()
        }
    val iconSizePx = with(density) { headerIconSize.roundToPx().coerceAtLeast(1) }
    val appIconBitmap =
        androidx.compose.runtime.remember(appIcon, iconSizePx) {
            appIcon
                ?.let { d -> runCatching { d.toBitmap(iconSizePx, iconSizePx).asImageBitmap() }.getOrNull() }
        }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            toast(context.getString(R.string.about_open_failed))
        }
    }

    fun openQqGroup() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(QQ_GROUP_URI)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            clipboard.setText(AnnotatedString(QQ_GROUP_NUMBER))
            toast(context.getString(R.string.about_qq_copied_fmt, QQ_GROUP_NUMBER))
        }
    }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = { Text(stringResource(R.string.settings_placeholder_about)) },
                navigationIcon = {
                    ZhixuIconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Ionicons.ArrowBack),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
            HorizontalDivider(color = dividerColor)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = null,
                            modifier = Modifier.size(headerIconSize),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(headerIconSize),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.about_version_fmt, BuildConfig.VERSION_NAME),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AboutNavRow(
                            iconRes = Ionicons.InformationCircleOutline,
                            title = stringResource(R.string.about_visit_website),
                            value = "zhixu.app",
                            onClick = { openUrl(OFFICIAL_SITE_URL) },
                        )
                        HorizontalDivider(color = dividerColor)
                        AboutNavRow(
                            iconRes = Ionicons.User,
                            title = stringResource(R.string.about_join_qq_group),
                            value = QQ_GROUP_NUMBER,
                            onClick = ::openQqGroup,
                        )
                        HorizontalDivider(color = dividerColor)
                        AboutNavRow(
                            iconRes = Ionicons.DocumentText,
                            title = stringResource(R.string.terms_of_use_title),
                            value = OFFICIAL_TOS_URL.removePrefix("https://"),
                            onClick = onOpenTermsOfUse,
                        )
                        HorizontalDivider(color = dividerColor)
                        AboutNavRow(
                            iconRes = Ionicons.DocumentText,
                            title = stringResource(R.string.privacy_policy_title),
                            value = OFFICIAL_PRIVACY_URL.removePrefix("https://"),
                            onClick = onOpenPrivacyPolicy,
                        )
                        HorizontalDivider(color = dividerColor)
                        AboutNavRow(
                            iconRes = Ionicons.DocumentText,
                            title = stringResource(R.string.open_source_license_title),
                            value = OFFICIAL_LICENSE_URL.removePrefix("https://"),
                            onClick = onOpenOpenSourceLicense,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun AboutNavRow(
    iconRes: Int,
    title: String,
    value: String?,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = { Text(title) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!value.isNullOrBlank()) {
                    Text(
                        text = value,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Icon(
                    painter = painterResource(Ionicons.ChevronForward),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}
