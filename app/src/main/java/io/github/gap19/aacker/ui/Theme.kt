package io.github.gap19.aacker.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import io.github.gap19.aacker.R
import io.github.gap19.aacker.library.Artwork

/** Narrow poster voice for titles only; never for durations, lyrics or metadata. */
val Gothic = FontFamily(Font(R.font.league_gothic))
val Sans = FontFamily(
    Font(R.font.source_sans_regular, FontWeight.Normal),
    Font(R.font.source_sans_semibold, FontWeight.SemiBold),
)

val Teal = Color(Artwork.DEFAULT_ACCENT)
private val Ink = Color(0xFF0E1415)

/** FLACker's charcoal family on true black: OLED pixels outside the content stay off. */
private fun scheme(accent: Color) = ColorScheme(
    primary = accent,
    primaryDim = lerp(accent, Color.Black, 0.25f),
    primaryContainer = lerp(accent, Color(0xFF111617), 0.72f),
    onPrimary = Ink,
    onPrimaryContainer = lerp(accent, Color.White, 0.55f),
    secondary = Color(0xFFACB9BD),
    secondaryDim = Color(0xFF8A979B),
    secondaryContainer = Color(0xFF2C3638),
    onSecondary = Color(0xFF111617),
    onSecondaryContainer = Color(0xFFF0F4F4),
    tertiary = accent,
    tertiaryDim = lerp(accent, Color.Black, 0.25f),
    tertiaryContainer = lerp(accent, Color(0xFF111617), 0.72f),
    onTertiary = Ink,
    onTertiaryContainer = lerp(accent, Color.White, 0.55f),
    surfaceContainerLow = Color(0xFF111617),
    surfaceContainer = Color(0xFF1A2123),
    surfaceContainerHigh = Color(0xFF242D30),
    onSurface = Color(0xFFF0F4F4),
    onSurfaceVariant = Color(0xFFACB9BD),
    outline = Color(0xFF5A6A6E),
    outlineVariant = Color(0xFF2C3638),
    background = Color.Black,
    onBackground = Color(0xFFF0F4F4),
)

private val Type = Typography(defaultFontFamily = Sans)

@Composable
fun AackerTheme(accent: Color = Teal, content: @Composable () -> Unit) {
    val colors = androidx.compose.runtime.remember(accent) { scheme(accent) }
    MaterialTheme(colorScheme = colors, typography = Type, content = content)
}
