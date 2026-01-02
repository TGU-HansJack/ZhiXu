package app.zhixu.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import app.zhixu.R

private const val OPEN_SOURCE_LICENSE_URL = "https://zhixu.app/license"

@Suppress("UNUSED_PARAMETER")
@Composable
fun OpenSourceLicenseScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    WebPageScreen(
        contentPadding = contentPadding,
        titleRes = R.string.open_source_license_title,
        url = OPEN_SOURCE_LICENSE_URL,
        onBack = onBack,
    )
}

