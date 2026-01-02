package com.zhixu.android.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.zhixu.android.R

private const val TERMS_OF_USE_URL = "https://zhixu.app/tos"

@Suppress("UNUSED_PARAMETER")
@Composable
fun TermsOfUseScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    WebPageScreen(
        contentPadding = contentPadding,
        titleRes = R.string.terms_of_use_title,
        url = TERMS_OF_USE_URL,
        onBack = onBack,
    )
}

