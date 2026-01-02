package app.zhixu

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.zhixu.data.UiFontOption
import app.zhixu.data.UiPreferences
import app.zhixu.data.UiThemeMode
import app.zhixu.ui.ZhixuApp
import app.zhixu.ui.theme.LxgwWenKaiMonoLightDefaultFamily
import app.zhixu.ui.theme.SourceSansProLightDefaultFamily
import app.zhixu.ui.theme.SourceSansProRegularDefaultFamily
import app.zhixu.ui.theme.ZhixuTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            val uiPrefs = remember(context) { UiPreferences(context.applicationContext) }

            val themeMode by uiPrefs.themeMode.collectAsState(initial = UiThemeMode.SYSTEM)
            val fontOption by uiPrefs.fontOption.collectAsState(initial = UiFontOption.SOURCE_SANS_PRO_LIGHT)

            val darkTheme =
                when (themeMode) {
                    UiThemeMode.SYSTEM -> isSystemInDarkTheme()
                    UiThemeMode.LIGHT -> false
                    UiThemeMode.DARK -> true
                }

            val appFontFamily =
                when (fontOption) {
                    UiFontOption.SOURCE_SANS_PRO_LIGHT -> SourceSansProLightDefaultFamily
                    UiFontOption.SOURCE_SANS_PRO_REGULAR -> SourceSansProRegularDefaultFamily
                    UiFontOption.LXGW_WENKAI_MONO_LIGHT -> LxgwWenKaiMonoLightDefaultFamily
                }

            ZhixuTheme(darkTheme = darkTheme, appFontFamily = appFontFamily) {
                SystemBarsAppearance()
                PostNotificationsPermissionRequester()
                ZhixuApp()
            }
        }
    }
}

@Composable
private fun SystemBarsAppearance() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val view = LocalView.current

    val surface = androidx.compose.material3.MaterialTheme.colorScheme.surface
    val useDarkIcons = surface.luminance() > 0.5f

    DisposableEffect(view, activity, useDarkIcons, surface) {
        val controller = WindowInsetsControllerCompat(activity.window, view)
        val window = activity.window
        val previousLightStatusBars = controller.isAppearanceLightStatusBars
        val previousLightNavBars = controller.isAppearanceLightNavigationBars
        val previousStatusBarColor = window.statusBarColor
        val previousNavBarColor = window.navigationBarColor

        controller.isAppearanceLightStatusBars = useDarkIcons
        controller.isAppearanceLightNavigationBars = useDarkIcons
        window.statusBarColor = surface.toArgb()
        window.navigationBarColor = surface.toArgb()

        onDispose {
            controller.isAppearanceLightStatusBars = previousLightStatusBars
            controller.isAppearanceLightNavigationBars = previousLightNavBars
            window.statusBarColor = previousStatusBarColor
            window.navigationBarColor = previousNavBarColor
        }
    }
}

@Composable
private fun PostNotificationsPermissionRequester() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { _ -> },
        )

    LaunchedEffect(Unit) {
        withFrameNanos { }
        val granted =
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
