package com.invernadero.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Material 3 con tus dos paletas: cielo azul (día) y atardecer (tarde), con acentos verdes. */
private val EsquemaDia = lightColorScheme(
    primary = Color(0xFF3880E4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFADCDF2),
    onPrimaryContainer = Color(0xFF0E2A47),
    secondary = Color(0xFF2E7D32),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF0B3D20),
    tertiary = Color(0xFF6E9FD4),
    background = Color(0xFFEAF2FB),
    onBackground = Color(0xFF0E2A47),
    surface = Color(0xFFF7FAFE),
    onSurface = Color(0xFF0E2A47),
    surfaceVariant = Color(0xFFD6E4F5),
    onSurfaceVariant = Color(0xFF3B5A7A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F6FC),
    surfaceContainer = Color(0xFFE6EFF9),
    surfaceContainerHigh = Color(0xFFDDE8F6),
    surfaceContainerHighest = Color(0xFFD6E4F5),
    outline = Color(0xFF6E9FD4),
)

private val EsquemaTarde = darkColorScheme(
    primary = Color(0xFFF28466),
    onPrimary = Color(0xFF2E1F2A),
    primaryContainer = Color(0xFF7A4F55),
    onPrimaryContainer = Color(0xFFFCE9DF),
    secondary = Color(0xFF7CFFB0),
    onSecondary = Color(0xFF0B3D20),
    secondaryContainer = Color(0xFF1F5135),
    onSecondaryContainer = Color(0xFFC8FFDD),
    tertiary = Color(0xFFF8C9A0),
    background = Color(0xFF2E1F2A),
    onBackground = Color(0xFFFCE9DF),
    surface = Color(0xFF2E1F2A),
    onSurface = Color(0xFFFCE9DF),
    surfaceVariant = Color(0xFF5E3A44),
    onSurfaceVariant = Color(0xFFF8C9A0),
    surfaceContainerLowest = Color(0xFF261922),
    surfaceContainerLow = Color(0xFF35232F),
    surfaceContainer = Color(0xFF3B2832),
    surfaceContainerHigh = Color(0xFF47303B),
    surfaceContainerHighest = Color(0xFF5E3A44),
    outline = Color(0xFFD97C6C),
)

@Composable
fun TemaInvernadero(modoTarde: Boolean, contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (modoTarde) EsquemaTarde else EsquemaDia, content = contenido)
}
