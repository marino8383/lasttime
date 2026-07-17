package it.marino8383.lasttime.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette antracite/ambra Solari, ripresa dal mockup v27
val Surface = Color(0xFF14161A)
val SurfaceContainer = Color(0xFF1C1F24)
val SurfaceContainerHigh = Color(0xFF262B32)
val OnSurface = Color(0xFFF2F3EF)
val OnSurfaceVariant = Color(0xFF9AA0A8)
val Outline = Color(0xFF33383F)
val Primary = Color(0xFFF5A524)
val OnPrimary = Color(0xFF17191D)
val PrimaryContainer = Color(0xFF3A2D12)
val OnPrimaryContainer = Color(0xFFFFC966)
val ErrorColor = Color(0xFFE0562F)
val ErrorContainer = Color(0xFF2E2410)
val OnErrorContainer = Color(0xFFFFB84D)

private val LastTimeColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHigh,
    outline = Outline,
    error = ErrorColor,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

@Composable
fun LastTimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LastTimeColors,
        content = content,
    )
}
