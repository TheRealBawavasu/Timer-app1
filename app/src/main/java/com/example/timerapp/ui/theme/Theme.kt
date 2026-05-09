package com.example.timerapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppTheme {
    Default, Ocean, Forest, Sunset, Cool, DeepDark
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

// Ocean Theme
private val OceanLightColors = lightColorScheme(
    primary = Color(0xFF006494),
    secondary = Color(0xFF00A6FB),
    surfaceVariant = Color(0xFFE1F5FE),
    background = Color(0xFFF0F8FF)
)
private val OceanDarkColors = darkColorScheme(
    primary = Color(0xFF00A6FB),
    secondary = Color(0xFF0582CA),
    surfaceVariant = Color(0xFF003554),
    background = Color(0xFF001D3D)
)

// Forest Theme
private val ForestLightColors = lightColorScheme(
    primary = Color(0xFF2D6A4F),
    secondary = Color(0xFF52B788),
    surfaceVariant = Color(0xFFD8F3DC),
    background = Color(0xFFF7FFF7)
)
private val ForestDarkColors = darkColorScheme(
    primary = Color(0xFF74C69D),
    secondary = Color(0xFF95D5B2),
    surfaceVariant = Color(0xFF081C15),
    background = Color(0xFF0D1B1E)
)

// Sunset Theme
private val SunsetLightColors = lightColorScheme(
    primary = Color(0xFFD00000),
    secondary = Color(0xFFFF8C00),
    surfaceVariant = Color(0xFFFFF3E0),
    background = Color(0xFFFFF9F0)
)
private val SunsetDarkColors = darkColorScheme(
    primary = Color(0xFFFFBA08),
    secondary = Color(0xFFFAA307),
    surfaceVariant = Color(0xFF370617),
    background = Color(0xFF1A0A0A)
)

// Cool Theme (Teal/Cyan)
private val CoolLightColors = lightColorScheme(
    primary = Color(0xFF00796B),
    secondary = Color(0xFF00BCD4),
    surfaceVariant = Color(0xFFE0F2F1),
    background = Color(0xFFF5FFFA)
)
private val CoolDarkColors = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    secondary = Color(0xFF80DEEA),
    surfaceVariant = Color(0xFF004D40),
    background = Color(0xFF002424)
)

// Deep Dark Theme (True Black)
private val DeepDarkColors = darkColorScheme(
    primary = Color(0xFFBB86FC),
    secondary = Color(0xFF03DAC6),
    surfaceVariant = Color(0xFF121212),
    background = Color(0xFF000000),
    surface = Color(0xFF000000)
)

@Composable
fun TimerAppTheme(
    appTheme: AppTheme = AppTheme.Default,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.Ocean -> if (darkTheme) OceanDarkColors else OceanLightColors
        AppTheme.Forest -> if (darkTheme) ForestDarkColors else ForestLightColors
        AppTheme.Sunset -> if (darkTheme) SunsetDarkColors else SunsetLightColors
        AppTheme.Cool -> if (darkTheme) CoolDarkColors else CoolLightColors
        AppTheme.DeepDark -> DeepDarkColors
        AppTheme.Default -> when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}