package com.omniai.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.omniai.core.model.AccentColor

/** Neutral palette tuned for a dark-first, low-glare UI. Accent colors come from user settings. */
internal object OmniPalette {
    val Ink950 = Color(0xFF0B0D12)
    val Ink900 = Color(0xFF11141B)
    val Ink850 = Color(0xFF161A23)
    val Ink800 = Color(0xFF1C212C)
    val Ink700 = Color(0xFF282E3B)
    val Ink500 = Color(0xFF5B6475)
    val Ink300 = Color(0xFF9AA3B5)
    val Ink100 = Color(0xFFE4E8F0)
    val Paper = Color(0xFFF7F8FB)
    val PaperDim = Color(0xFFECEFF5)
    val Error = Color(0xFFFF6B6B)
    val ErrorLight = Color(0xFFC62828)
}

/** Semantic status colors used by chips and indicators. */
data class StatusColors(val success: Color, val warning: Color, val danger: Color, val neutral: Color)

internal fun darkScheme(accent: AccentColor): ColorScheme {
    val primary = Color(accent.argb)
    val onPrimary = if (primary.luminance() > 0.4f) OmniPalette.Ink950 else Color.White
    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primary.copy(alpha = 0.22f).compositeOver(OmniPalette.Ink850),
        onPrimaryContainer = OmniPalette.Ink100,
        secondary = primary.copy(alpha = 0.8f).compositeOver(OmniPalette.Ink300),
        onSecondary = OmniPalette.Ink950,
        secondaryContainer = OmniPalette.Ink700,
        onSecondaryContainer = OmniPalette.Ink100,
        tertiary = Color(0xFF8BD3FF),
        background = OmniPalette.Ink950,
        onBackground = OmniPalette.Ink100,
        surface = OmniPalette.Ink950,
        onSurface = OmniPalette.Ink100,
        surfaceVariant = OmniPalette.Ink800,
        onSurfaceVariant = OmniPalette.Ink300,
        surfaceContainerLowest = OmniPalette.Ink950,
        surfaceContainerLow = OmniPalette.Ink900,
        surfaceContainer = OmniPalette.Ink850,
        surfaceContainerHigh = OmniPalette.Ink800,
        surfaceContainerHighest = OmniPalette.Ink700,
        outline = OmniPalette.Ink500,
        outlineVariant = OmniPalette.Ink700,
        error = OmniPalette.Error,
        onError = OmniPalette.Ink950,
    )
}

internal fun lightScheme(accent: AccentColor): ColorScheme {
    val base = Color(accent.argb)
    // Darken the accent for legible contrast on light surfaces.
    val primary = Color(red = base.red * 0.62f, green = base.green * 0.62f, blue = base.blue * 0.62f)
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = base.copy(alpha = 0.18f).compositeOver(Color.White),
        onPrimaryContainer = OmniPalette.Ink950,
        secondaryContainer = OmniPalette.PaperDim,
        onSecondaryContainer = OmniPalette.Ink900,
        background = OmniPalette.Paper,
        onBackground = OmniPalette.Ink950,
        surface = OmniPalette.Paper,
        onSurface = OmniPalette.Ink950,
        surfaceVariant = OmniPalette.PaperDim,
        onSurfaceVariant = OmniPalette.Ink500,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF2F4F8),
        surfaceContainer = Color(0xFFEDF0F5),
        surfaceContainerHigh = Color(0xFFE7EAF1),
        surfaceContainerHighest = Color(0xFFE0E4EC),
        outline = OmniPalette.Ink300,
        outlineVariant = Color(0xFFD5DAE3),
        error = OmniPalette.ErrorLight,
    )
}
