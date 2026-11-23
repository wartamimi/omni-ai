package com.omniai.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.omniai.core.model.AccentColor
import com.omniai.core.model.ThemeMode

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFFF6B6B), Color(0xFF9AA3B5))
}

private val OmniTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

private val OmniShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * OmniAI theme. Dark mode is the reference design; dynamic color (Android 12+) is opt-in so the
 * chosen accent stays consistent by default. [textScale] multiplies the system font scale.
 */
@Composable
fun OmniTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.INDIGO,
    dynamicColor: Boolean = false,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkScheme(accent)
        else -> lightScheme(accent)
    }
    val status = if (dark) {
        StatusColors(Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFFF6B6B), Color(0xFF9AA3B5))
    } else {
        StatusColors(Color(0xFF15803D), Color(0xFFB45309), Color(0xFFC62828), Color(0xFF5B6475))
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalStatusColors provides status,
        LocalDensity provides Density(density.density, density.fontScale * textScale),
    ) {
        MaterialTheme(colorScheme = scheme, typography = OmniTypography, shapes = OmniShapes, content = content)
    }
}
