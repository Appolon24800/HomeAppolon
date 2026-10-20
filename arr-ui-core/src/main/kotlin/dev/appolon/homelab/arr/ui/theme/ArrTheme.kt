package dev.appolon.homelab.arr.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Theme for every native *arr screen. Falls back to the dashboard's palette so
 * the two halves of the app read as one product, and takes dynamic color on
 * Android 12+ exactly like HomelabTheme's SYSTEM mode does.
 */
@Composable
fun ArrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ArrDarkColors
        else -> ArrLightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = ArrTypography, content = content)
}

/** Stock M3 type ramp; the dashboard sets no custom typography either. */
val ArrTypography = Typography()

// Token values mirror the fallback schemes in app/ui/theme/Theme.kt (the
// SYSTEM palette). :arr-ui-core cannot depend on :app, so they are duplicated
// here deliberately; keep both files in sync until the tokens move to a
// shared module.
private val ArrDarkColors = darkColorScheme(
    primary = Color(0xFFFF5A50),
    onPrimary = Color(0xFF2B0503),
    primaryContainer = Color(0xFF5D1510),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFE6BCB7),
    onSecondary = Color(0xFF442A26),
    tertiary = Color(0xFFE0C38D),
    background = Color(0xFF1A1918),
    onBackground = Color(0xFFEAE0DD),
    surface = Color(0xFF1A1918),
    onSurface = Color(0xFFEAE0DD),
    surfaceVariant = Color(0xFF534441),
    onSurfaceVariant = Color(0xFFD8C2BE),
)

private val ArrLightColors = lightColorScheme(
    primary = Color(0xFFB3261E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF775651),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF221A19),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF221A19),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
)
