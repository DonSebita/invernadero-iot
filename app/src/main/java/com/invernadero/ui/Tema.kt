package com.invernadero.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invernadero.R

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
    tertiary = Color(0xFFD9653B),
    background = Color(0xFFEAF2FB),
    onBackground = Color(0xFF0E2A47),
    surface = Color(0xFFF7FAFE),
    onSurface = Color(0xFF0E2A47),
    surfaceVariant = Color(0xFFD6E4F5),
    onSurfaceVariant = Color(0xFF3B5A7A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F9FD),
    surfaceContainer = Color(0xFFE9F1FA),
    surfaceContainerHigh = Color(0xFFDDE8F6),
    surfaceContainerHighest = Color(0xFFD6E4F5),
    outline = Color(0xFF6E8FB4),
    outlineVariant = Color(0xFFC9D9EC),
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
    tertiary = Color(0xFFFFB37A),
    background = Color(0xFF2E1F2A),
    onBackground = Color(0xFFFCE9DF),
    surface = Color(0xFF2E1F2A),
    onSurface = Color(0xFFFCE9DF),
    surfaceVariant = Color(0xFF5E3A44),
    onSurfaceVariant = Color(0xFFF2CDB0),
    surfaceContainerLowest = Color(0xFF261922),
    surfaceContainerLow = Color(0xFF37252F),
    surfaceContainer = Color(0xFF3D2A34),
    surfaceContainerHigh = Color(0xFF47303B),
    surfaceContainerHighest = Color(0xFF5E3A44),
    outline = Color(0xFFB98478),
    outlineVariant = Color(0xFF5A3D47),
)

/**
 * Voz de GreenBox (ambas con licencia SIL OFL, ver /licencias):
 *  - Bricolage Grotesque en títulos y cifras grandes: grotesca de trazos orgánicos, con carácter
 *    pero sin perder claridad (reemplaza a Lora: una serif caligráfica se lee peor en títulos chicos y cifras).
 *  - Instrument Sans en cuerpo, etiquetas, controles y datos: nítida al sol y con cifras tabulares,
 *    así los valores en vivo no "bailan" cuando cambian.
 * El archivo Bold también se declara como SemiBold para que M3 lo use en botones y títulos chicos.
 */
val FuenteTitulos = FontFamily(
    Font(R.font.bricolage_grotesque_regular, FontWeight.Normal),
    Font(R.font.bricolage_grotesque_bold, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
)

val FuenteTexto = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_bold, FontWeight.SemiBold),
    Font(R.font.instrument_sans_bold, FontWeight.Bold),
)

/** Cifras de ancho fijo: las columnas de números quedan alineadas. */
private const val CIFRAS = "tnum"

private fun titulo(tam: Int, alto: Int, tracking: Float = 0f) = TextStyle(
    fontFamily = FuenteTitulos, fontWeight = FontWeight.Bold,
    fontSize = tam.sp, lineHeight = alto.sp, letterSpacing = tracking.sp, fontFeatureSettings = CIFRAS
)

private fun texto(tam: Int, alto: Int, peso: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
    fontFamily = FuenteTexto, fontWeight = peso,
    fontSize = tam.sp, lineHeight = alto.sp, letterSpacing = tracking.sp, fontFeatureSettings = CIFRAS
)

private val TipografiaGreenBox = Typography(
    displayLarge = titulo(54, 60, -1.2f),
    displayMedium = titulo(44, 52, -0.9f),
    displaySmall = titulo(36, 44, -0.6f),
    headlineLarge = titulo(32, 40, -0.6f),
    headlineMedium = titulo(28, 36, -0.4f),
    headlineSmall = titulo(24, 32, -0.3f),
    titleLarge = titulo(21, 28, -0.2f),
    titleMedium = titulo(17, 24),
    titleSmall = texto(14, 20, FontWeight.SemiBold),
    bodyLarge = texto(16, 24),
    bodyMedium = texto(14, 20, tracking = 0.1f),
    bodySmall = texto(12, 17, tracking = 0.2f),
    labelLarge = texto(14, 20, FontWeight.SemiBold, 0.1f),
    labelMedium = texto(12, 16, FontWeight.SemiBold, 0.3f),
    labelSmall = texto(11, 16, tracking = 0.3f),
)

/** Esquinas suaves, como hojas: más redondas mientras más grande el contenedor. */
private val FormasGreenBox = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun TemaInvernadero(modoTarde: Boolean, contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (modoTarde) EsquemaTarde else EsquemaDia,
        typography = TipografiaGreenBox,
        shapes = FormasGreenBox,
        content = contenido
    )
}
