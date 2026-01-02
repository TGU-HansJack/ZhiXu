package app.zhixu.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import java.lang.reflect.Proxy

private val ZhixuLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF0969DA), // blue
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDDF4FF), // light blue
        onPrimaryContainer = Color(0xFF0A3069),
        secondary = Color(0xFF57606A), // muted
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF6F8FA), // light gray
        onSecondaryContainer = Color(0xFF24292F),
        tertiary = Color(0xFF1A7F37), // green
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFDAFBE1), // light green
        onTertiaryContainer = Color(0xFF002D11),
        error = Color(0xFFCF222E), // red
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFEBE9), // light red
        onErrorContainer = Color(0xFF82071E),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF24292F),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF24292F),
        surfaceVariant = Color(0xFFF6F8FA),
        onSurfaceVariant = Color(0xFF57606A),
        outline = Color(0xFFD0D7DE),
        outlineVariant = Color(0xFFEAECEF),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFF24292F),
        inverseOnSurface = Color(0xFFF6F8FA),
        inversePrimary = Color(0xFF2F81F7),
    )

private val ZhixuDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF2F81F7), // blue
        onPrimary = Color(0xFF0D1117),
        primaryContainer = Color(0xFF0A3069),
        onPrimaryContainer = Color(0xFFDDF4FF),
        secondary = Color(0xFF8B949E),
        onSecondary = Color(0xFF0D1117),
        secondaryContainer = Color(0xFF21262D),
        onSecondaryContainer = Color(0xFFC9D1D9),
        tertiary = Color(0xFF3FB950), // green
        onTertiary = Color(0xFF0D1117),
        tertiaryContainer = Color(0xFF033A16),
        onTertiaryContainer = Color(0xFFDAFBE1),
        error = Color(0xFFF85149), // red
        onError = Color(0xFF0D1117),
        errorContainer = Color(0xFF5A1E1A),
        onErrorContainer = Color(0xFFFFEBE9),
        background = Color(0xFF0D1117),
        onBackground = Color(0xFFC9D1D9),
        surface = Color(0xFF0D1117),
        onSurface = Color(0xFFC9D1D9),
        surfaceVariant = Color(0xFF161B22),
        onSurfaceVariant = Color(0xFF8B949E),
        outline = Color(0xFF30363D),
        outlineVariant = Color(0xFF21262D),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFC9D1D9),
        inverseOnSurface = Color(0xFF0D1117),
        inversePrimary = Color(0xFF0969DA),
    )

private fun ColorScheme.withFlatSurfaces(): ColorScheme =
    copy(
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        surfaceBright = surface,
        surfaceDim = surface,
    )

@Composable
fun ZhixuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            (if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).withFlatSurfaces()
        } else {
            (if (darkTheme) ZhixuDarkColorScheme else ZhixuLightColorScheme).withFlatSurfaces()
        }

    val baseResolver = LocalFontFamilyResolver.current
    val appResolver =
        remember(baseResolver) {
            Proxy.newProxyInstance(
                FontFamily.Resolver::class.java.classLoader,
                arrayOf(FontFamily.Resolver::class.java),
            ) { _, method, argsOrNull ->
                val args = argsOrNull ?: emptyArray()
                if (args.isNotEmpty() && args[0] == FontFamily.Default) {
                    val nextArgs = args.copyOf()
                    nextArgs[0] = AppFontFamily
                    method.invoke(baseResolver, *nextArgs)
                } else {
                    method.invoke(baseResolver, *args)
                }
            } as FontFamily.Resolver
        }

    CompositionLocalProvider(LocalFontFamilyResolver provides appResolver) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}
