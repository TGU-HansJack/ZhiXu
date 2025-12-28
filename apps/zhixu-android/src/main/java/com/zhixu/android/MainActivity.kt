package com.zhixu.android

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.zhixu.android.ui.ZhixuApp
import com.zhixu.android.ui.theme.ZhixuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ZhixuTheme {
                PostNotificationsPermissionRequester()
                ZhixuApp()
            }
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
