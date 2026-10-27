package com.doctorknepper.personalore.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** A colorway: one accent family with a dark (OLED black) and a light variant. */
data class Colorway(
    val id: String,
    val name: String,
    val darkAccent: Color,
    val darkAccentSoft: Color,
    val lightAccent: Color,
    val lightAccentSoft: Color,
)

val Colorways = listOf(
    Colorway("amber", "Amber", Color(0xFFFFB300), Color(0xFF3A2A00), Color(0xFFB26A00), Color(0xFFFFE8B8)),
    Colorway("ember", "Ember", Color(0xFFFF7043), Color(0xFF3D1A10), Color(0xFFC1440E), Color(0xFFFFDCCF)),
    Colorway("rose", "Rose", Color(0xFFF48FB1), Color(0xFF3B1826), Color(0xFFB0305F), Color(0xFFFFD9E4)),
    Colorway("sage", "Sage", Color(0xFF9CCC65), Color(0xFF1E2E12), Color(0xFF4E7A21), Color(0xFFDCEFC7)),
    Colorway("ocean", "Ocean", Color(0xFF4FC3F7), Color(0xFF0E2A38), Color(0xFF0B6E99), Color(0xFFCDEBFA)),
    Colorway("violet", "Violet", Color(0xFFB39DDB), Color(0xFF261D3B), Color(0xFF5E3FA3), Color(0xFFE6DDFA)),
    Colorway("silver", "Silver", Color(0xFFCFD8DC), Color(0xFF22282B), Color(0xFF455A64), Color(0xFFE2E8EB)),
)

fun colorwayById(id: String) = Colorways.firstOrNull { it.id == id } ?: Colorways.first()

enum class ThemeMode(val label: String) { DARK("Dark (OLED black)"), LIGHT("Light"), SYSTEM("Follow system") }

fun buildScheme(cw: Colorway, dark: Boolean): ColorScheme =
    if (dark) darkColorScheme(
        primary = cw.darkAccent,
        onPrimary = Color.Black,
        primaryContainer = cw.darkAccentSoft,
        onPrimaryContainer = cw.darkAccent,
        secondary = cw.darkAccent,
        onSecondary = Color.Black,
        secondaryContainer = cw.darkAccentSoft,
        onSecondaryContainer = cw.darkAccent,
        tertiary = cw.darkAccent,
        background = Color.Black,
        onBackground = Color(0xFFEDEDED),
        surface = Color.Black,
        onSurface = Color(0xFFEDEDED),
        surfaceVariant = Color(0xFF121212),
        onSurfaceVariant = Color(0xFFA8A8A8),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0B0B0B),
        surfaceContainer = Color(0xFF111111),
        surfaceContainerHigh = Color(0xFF181818),
        surfaceContainerHighest = Color(0xFF202020),
        outline = Color(0xFF3A3A3A),
        outlineVariant = Color(0xFF262626),
        error = Color(0xFFFF6E6E),
    ) else lightColorScheme(
        primary = cw.lightAccent,
        onPrimary = Color.White,
        primaryContainer = cw.lightAccentSoft,
        onPrimaryContainer = Color(0xFF1C1B1F),
        secondary = cw.lightAccent,
        onSecondary = Color.White,
        secondaryContainer = cw.lightAccentSoft,
        onSecondaryContainer = Color(0xFF1C1B1F),
        tertiary = cw.lightAccent,
        background = Color(0xFFFAF8F5),
        onBackground = Color(0xFF1C1B1F),
        surface = Color(0xFFFAF8F5),
        onSurface = Color(0xFF1C1B1F),
        surfaceVariant = Color(0xFFEFECE7),
        onSurfaceVariant = Color(0xFF5C5853),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF5F2EE),
        surfaceContainer = Color(0xFFF0EDE8),
        surfaceContainerHigh = Color(0xFFEAE6E0),
        surfaceContainerHighest = Color(0xFFE3DFD8),
        outline = Color(0xFFC9C4BC),
        outlineVariant = Color(0xFFDDD8D0),
    )

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
    )
}

@Composable
fun PersonaLoreTheme(colorwayId: String, mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = buildScheme(colorwayById(colorwayId), dark), typography = AppTypography, content = content)
}
