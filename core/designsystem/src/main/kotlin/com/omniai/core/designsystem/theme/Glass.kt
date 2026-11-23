package com.omniai.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Glassmorphism tokens: translucent "frosted" fills with a light edge, floating over a soft,
 * colorful gradient background. Text always sits on a fill with enough opacity for contrast;
 * menus, dialogs and sheets use near-opaque glass so they stay readable over any content.
 */
@Immutable
data class GlassStyle(
    val isDark: Boolean,
    /** Card fill, top-left to bottom-right. */
    val fill: List<Color>,
    /** 1 dp edge highlight, top-left to bottom-right. */
    val border: List<Color>,
    /** Fill for app bars and the navigation bar. */
    val bar: Color,
    /** Background base gradient, top to bottom. */
    val base: List<Color>,
    /** Soft color "orbs" drawn behind everything. */
    val orbs: List<Color>,
)

val LocalGlass = staticCompositionLocalOf { glassStyle(dark = true, accent = Color(0xFF7C83FF)) }

internal fun glassStyle(dark: Boolean, accent: Color): GlassStyle = if (dark) {
    GlassStyle(
        isDark = true,
        fill = listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.05f)),
        border = listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.06f)),
        bar = Color(0xFF0E1020).copy(alpha = 0.55f),
        base = listOf(Color(0xFF0A0B18), Color(0xFF110C24), Color(0xFF07131C)),
        orbs = listOf(accent.copy(alpha = 0.55f), Color(0xFF22D3EE).copy(alpha = 0.32f), Color(0xFFF472B6).copy(alpha = 0.30f)),
    )
} else {
    GlassStyle(
        isDark = false,
        fill = listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.42f)),
        border = listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.35f)),
        bar = Color.White.copy(alpha = 0.55f),
        base = listOf(Color(0xFFEEF0FF), Color(0xFFF6EEFF), Color(0xFFEAF7FB)),
        orbs = listOf(accent.copy(alpha = 0.42f), Color(0xFF67E8F9).copy(alpha = 0.40f), Color(0xFFF9A8D4).copy(alpha = 0.40f)),
    )
}

/** Frosted glass surface: translucent gradient fill, light edge, clipped to [shape]. */
fun Modifier.glass(style: GlassStyle, shape: Shape = RectangleShape, borderWidth: Dp = 1.dp): Modifier =
    this
        .clip(shape)
        .background(Brush.linearGradient(style.fill), shape)
        .border(borderWidth, Brush.linearGradient(style.border), shape)

/** Full-screen gradient with soft color orbs. Place it behind the app content. */
@Composable
fun GlassBackground(style: GlassStyle, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(style.base))) {
        // Orbs are radial gradients (already soft); on Android 12+ an extra blur makes them silkier.
        val orbModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.fillMaxSize().blur(48.dp) else Modifier.fillMaxSize()
        Box(
            orbModifier.drawBehind {
                val r = max(size.width, size.height)
                fun orb(color: Color, cx: Float, cy: Float, radius: Float) = drawCircle(
                    brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = Offset(cx, cy), radius = radius),
                    radius = radius, center = Offset(cx, cy),
                )
                orb(style.orbs[0], size.width * 0.15f, size.height * 0.12f, r * 0.55f)
                orb(style.orbs[1], size.width * 0.95f, size.height * 0.45f, r * 0.45f)
                orb(style.orbs[2], size.width * 0.10f, size.height * 0.90f, r * 0.50f)
            },
        )
    }
}
