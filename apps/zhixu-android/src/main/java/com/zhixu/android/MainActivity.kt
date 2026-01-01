package com.zhixu.android

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import com.zhixu.android.ui.ZhixuApp
import com.zhixu.android.ui.theme.ZhixuTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ZhixuTheme {
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

    DisposableEffect(view, activity, useDarkIcons) {
        val controller = WindowInsetsControllerCompat(activity.window, view)
        val previousLightStatusBars = controller.isAppearanceLightStatusBars
        val previousLightNavBars = controller.isAppearanceLightNavigationBars

        controller.isAppearanceLightStatusBars = useDarkIcons
        controller.isAppearanceLightNavigationBars = useDarkIcons

        onDispose {
            controller.isAppearanceLightStatusBars = previousLightStatusBars
            controller.isAppearanceLightNavigationBars = previousLightNavBars
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
        // Defer permission prompt until after the first frame to reduce cold-start jank.
        withFrameNanos { }
        val granted =
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
