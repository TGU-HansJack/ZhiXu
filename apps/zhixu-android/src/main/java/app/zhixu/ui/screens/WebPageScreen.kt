package app.zhixu.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPageScreen(
    contentPadding: PaddingValues,
    @StringRes titleRes: Int,
    url: String,
    allowedHostSuffix: String? = "zhixu.app",
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }

    fun back() {
        val view = webView
        if (view != null && view.canGoBack()) {
            view.goBack()
        } else {
            onBack()
        }
    }

    BackHandler(enabled = true, onBack = ::back)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ZhixuTopAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    ZhixuIconButton(onClick = ::back) {
                        Icon(
                            painter = painterResource(Ionicons.ArrowBack),
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier.size(ZhixuTopBarIconSize),
                        )
                    }
                },
                actions = {
                    if (canGoBack) {
                        ZhixuIconButton(onClick = { webView?.goForward() }, enabled = webView?.canGoForward() == true) {
                            Icon(
                                painter = painterResource(Ionicons.ArrowForward),
                                contentDescription = null,
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
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
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                    webViewClient =
                        object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                                val target = request.url ?: return false
                                val host = target.host.orEmpty()
                                val allowed = allowedHostSuffix?.let { host == it || host.endsWith(".$it") } ?: true
                                if (allowed) return false

                                val intent =
                                    Intent(Intent.ACTION_VIEW, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(intent) }
                                return true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                canGoBack = view.canGoBack()
                            }
                        }

                    loadUrl(url)
                    webView = this
                }
            },
            update = { view ->
                webView = view
                canGoBack = view.canGoBack()
                val current = view.url.orEmpty()
                if (current != url) {
                    view.loadUrl(url)
                }
            },
        )
    }
}
