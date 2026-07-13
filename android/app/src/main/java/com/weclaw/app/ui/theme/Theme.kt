package com.weclaw.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = WeClawColors.primary,
    onPrimary = WeClawColors.textPrimary,
    secondary = WeClawColors.accent,
    background = WeClawColors.background,
    surface = WeClawColors.surface,
    surfaceVariant = WeClawColors.surfaceVariant,
    onBackground = WeClawColors.textPrimary,
    onSurface = WeClawColors.textPrimary,
    error = WeClawColors.error,
)

@Composable
fun WeClawTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
