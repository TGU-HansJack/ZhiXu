package com.zhixu.android.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.zhixu.android.R

private const val PRIVACY_POLICY_URL = "https://zhixu.app/privacy"

@Suppress("UNUSED_PARAMETER")
@Composable
fun PrivacyPolicyScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    WebPageScreen(
        contentPadding = contentPadding,
        titleRes = R.string.privacy_policy_title,
        url = PRIVACY_POLICY_URL,
        onBack = onBack,
    )
}

