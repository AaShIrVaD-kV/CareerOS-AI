package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2DD4BF),       // Teal accent
    secondary = Color(0xFF38BDF8),     // Sky Blue accent
    tertiary = Color(0xFFF59E0B),      // Amber highlights (Gaps)
    background = Color(0xFF0F172A),    // Deep Navy-Black
    surface = Color(0xFF1E293B),       // Slate Gray Secondary
    onPrimary = Color(0xFF0F172A),
    onSecondary = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),  // Crisp Slate White
    onSurface = Color(0xFFE2E8F0),     // Light slate
    surfaceVariant = Color(0xFF334155),// Slate Border Tone
    onSurfaceVariant = Color(0xFF94A3B8)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0D9488),       // Deep Teal
    secondary = Color(0xFF0284C7),     // Deep Sky Blue
    tertiary = Color(0xFFD97706),      // Muted Amber
    background = Color(0xFFF8FAFC),    // Muted Light Gray
    surface = Color(0xFFFFFFFF),       // Clear white surface
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),  // Charcoal
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF64748B)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Force Light Theme Only
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Strictly Light theme color palette
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
