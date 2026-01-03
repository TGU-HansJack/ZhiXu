package app.zhixu.ui.screens

import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreviewScreen(
    docUri: Uri,
    onBack: () -> Unit,
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    var webView: WebView? by remember { mutableStateOf(null) }

    fun back() {
        val view = webView
        if (view != null && view.canGoBack()) {
            view.goBack()
        } else {
            onBack()
        }
    }

    BackHandler(enabled = true, onBack = ::back)

    val src = Uri.encode(docUri.toString())
    val url = "file:///android_asset/image-preview/index.html?src=$src"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ZhixuTopAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("图片", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    ZhixuIconButton(onClick = ::back) {
                        Icon(
                            painter = painterResource(Ionicons.ArrowBack),
                            contentDescription = null,
                            modifier = Modifier.size(ZhixuTopBarIconSize),
                        )
                    }
                },
            )
            HorizontalDivider(color = dividerColor)
        },
    ) { innerPadding ->
        AndroidView(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            factory = {
                WebView(it).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.loadsImagesAutomatically = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true

                    webViewClient = android.webkit.WebViewClient()

                    loadUrl(url)
                    webView = this
                }
            },
            update = { view ->
                webView = view
                val current = view.url.orEmpty()
                if (current != url) {
                    view.loadUrl(url)
                }
            },
        )
    }
}

