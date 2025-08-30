package ai.runow.ui

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class Preset { SportLight, SportDark, HighContrast, Minimal }
enum class ShapeStyle { Square, Rounded, Pill }

@Immutable
data class ThemeState(
    val preset: Preset = Preset.SportDark,
    val darkMode: Boolean? = null,          // null = segui sistema
    val dynamicColor: Boolean = true,
    val shape: ShapeStyle = ShapeStyle.Pill
)

@Composable
fun RunowTheme(state: ThemeState, content: @Composable () -> Unit) {
    // Scegli dark/light: se null segue il sistema
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = state.darkMode ?: systemDark

    val context = LocalContext.current
    val colorScheme = if (state.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        when (state.preset) {
            Preset.SportLight   -> sportLightScheme()
            Preset.SportDark    -> sportDarkScheme()
            Preset.HighContrast -> highContrastScheme(isDark)
            Preset.Minimal      -> minimalScheme(isDark)
        }
    }

    val shapes = when (state.shape) {
        ShapeStyle.Square  -> Shapes(
            extraSmall = RoundedCornerShape(0.dp),
            small      = RoundedCornerShape(0.dp),
            medium     = RoundedCornerShape(0.dp),
            large      = RoundedCornerShape(0.dp),
            extraLarge = RoundedCornerShape(0.dp)
        )
        ShapeStyle.Rounded -> Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small      = RoundedCornerShape(8.dp),
            medium     = RoundedCornerShape(12.dp),
            large      = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(20.dp)
        )
        ShapeStyle.Pill    -> Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small      = RoundedCornerShape(14.dp),
            medium     = RoundedCornerShape(18.dp),
            large      = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(percent = 50)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        typography = Typography(), // possiamo introdurre scala tipografica dopo
        content = content
    )
}

// ---------- SCHEMI COLORI DI BASE (estratti dal theme.json che ti ho dato) ----------

@Composable
private fun sportDarkScheme() = darkColorScheme(
    primary        = Color(0xFF00B894),
    onPrimary      = Color(0xFF00110D),
    secondary      = Color(0xFF2B2E3A),
    onSecondary    = Color(0xFFFFFFFF),
    background     = Color(0xFF0E1116),
    onBackground   = Color(0xFFE8EAED),
    surface        = Color(0xFF141820),
    onSurface      = Color(0xFFDDE1E6),
    error          = Color(0xFFFF5A5F)
)

@Composable
private fun sportLightScheme() = lightColorScheme(
    primary        = Color(0xFF0BA36B),
    onPrimary      = Color(0xFFFFFFFF),
    secondary      = Color(0xFF2B2E3A),
    onSecondary    = Color(0xFFFFFFFF),
    background     = Color(0xFFFFFFFF),
    onBackground   = Color(0xFF101114),
    surface        = Color(0xFFF5F7FA),
    onSurface      = Color(0xFF1B1F24),
    error          = Color(0xFFE5484D)
)

@Composable
private fun highContrastScheme(isDark: Boolean) =
    if (isDark) darkColorScheme(
        primary      = Color(0xFFFFD400),
        onPrimary    = Color(0xFF000000),
        background   = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface      = Color(0xFF111111),
        onSurface    = Color(0xFFFFFFFF),
        error        = Color(0xFFFF3355)
    ) else lightColorScheme(
        primary      = Color(0xFFFFD400),
        onPrimary    = Color(0xFF000000),
        background   = Color(0xFFFFFFFF),
        onBackground = Color(0xFF000000),
        surface      = Color(0xFFF5F5F5),
        onSurface    = Color(0xFF000000),
        error        = Color(0xFFB00020)
    )

@Composable
private fun minimalScheme(isDark: Boolean) =
    if (isDark) darkColorScheme(
        primary      = Color(0xFF4C6EF5),
        onPrimary    = Color(0xFFFFFFFF),
        background   = Color(0xFF0B1220),
        onBackground = Color(0xFFE5E7EB),
        surface      = Color(0xFF111827),
        onSurface    = Color(0xFFE5E7EB),
        error        = Color(0xFFEF4444)
    ) else lightColorScheme(
        primary      = Color(0xFF4C6EF5),
        onPrimary    = Color(0xFFFFFFFF),
        background   = Color(0xFFF9FAFB),
        onBackground = Color(0xFF0B1220),
        surface      = Color(0xFFFFFFFF),
        onSurface    = Color(0xFF1F2937),
        error        = Color(0xFFDC2626)
    )
