package com.invernadero.ui.monitoreo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

data class Paleta(
    val fondo: List<Color>,      // degradado de arriba hacia abajo (siempre 4 colores)
    val tinteCaja: Color,        // color del plástico de la caja
    val bordeCaja: Color,        // aristas de la caja
    val texto: Color,
    val linea: Color,            // líneas de las etiquetas
    val verde: Color,            // acento verde para los datos
    val tarjeta: Color,          // fondo de barras / tarjetas
    val botonActivo: Color,
    val textoBoton: Color,
)

/** Modo día: cielo azul. */
val PaletaDia = Paleta(
    fondo = listOf(Color(0xFF4A8CDB), Color(0xFF6E9FD4), Color(0xFFADCDF2), Color(0xFFD6E4F5)),
    tinteCaja = Color(0xFFADCDF2),
    bordeCaja = Color(0xFFFFFFFF),
    texto = Color(0xFF0E2A47),
    linea = Color(0xFF0E2A47),
    verde = Color(0xFF0F6B2C),
    tarjeta = Color(0xD9D6E4F5),
    botonActivo = Color(0xFF3880E4),
    textoBoton = Color(0xFFFFFFFF),
)

/** Modo noche: atardecer. */
val PaletaTarde = Paleta(
    fondo = listOf(Color(0xFF2E1F2A), Color(0xFF5E3A44), Color(0xFF7A4F55), Color(0xFFB4655E)),
    tinteCaja = Color(0xFFF8C9A0),
    bordeCaja = Color(0xFFFCE9DF),
    texto = Color(0xFFFCE9DF),
    linea = Color(0xFFFCE9DF),
    verde = Color(0xFF7CFFB0),
    tarjeta = Color(0xB35E3A44),
    botonActivo = Color(0xFFF28466),
    textoBoton = Color(0xFF2E1F2A),
)

/** Mezcla dos paletas (f = 0 → a, f = 1 → b) para que el cambio de modo sea animado. */
fun mezclarPaletas(a: Paleta, b: Paleta, f: Float) = Paleta(
    fondo = a.fondo.zip(b.fondo) { x, y -> lerp(x, y, f) },
    tinteCaja = lerp(a.tinteCaja, b.tinteCaja, f),
    bordeCaja = lerp(a.bordeCaja, b.bordeCaja, f),
    texto = lerp(a.texto, b.texto, f),
    linea = lerp(a.linea, b.linea, f),
    verde = lerp(a.verde, b.verde, f),
    tarjeta = lerp(a.tarjeta, b.tarjeta, f),
    botonActivo = lerp(a.botonActivo, b.botonActivo, f),
    textoBoton = lerp(a.textoBoton, b.textoBoton, f),
)
