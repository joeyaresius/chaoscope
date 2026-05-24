package com.chaoscope.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ChaoscopePrimary   = Color(0xFF4FC3F7)  // light blue
private val ChaoscopeSecondary = Color(0xFF9C27B0)  // violet
private val ChaoscopeSurface   = Color(0xFF1A1A2E)
private val ChaoscopeBackground= Color(0xFF0D0D0D)

private val DarkColorScheme = darkColorScheme(
    primary          = ChaoscopePrimary,
    secondary        = ChaoscopeSecondary,
    background       = ChaoscopeBackground,
    surface          = ChaoscopeSurface,
    onPrimary        = Color.Black,
    onSecondary      = Color.White,
    onBackground     = Color.White,
    onSurface        = Color(0xFFE0E0E0),
)

@Composable
fun ChaoscopeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content     = content,
    )
}
