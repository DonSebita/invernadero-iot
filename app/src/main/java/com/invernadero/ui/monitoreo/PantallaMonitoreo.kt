@file:OptIn(ExperimentalMaterial3Api::class)

package com.invernadero.ui.monitoreo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invernadero.Contenedor
import com.invernadero.conexion.Comandos
import com.invernadero.conexion.EstadoConexion
import com.invernadero.conexion.LecturaEsp32
import com.invernadero.ui.FuenteTexto
import com.invernadero.ui.FuenteTitulos
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.geometry.lerp as lerpOffset

/** Qué parámetro de la escena se tocó. */
sealed interface Parametro {
    data class Planta(val indice: Int) : Parametro
    data object Temperatura : Parametro
    data object HumedadAire : Parametro
    data object Ventilador : Parametro
    data object Ventana : Parametro
}

/** Zona tocable de una etiqueta (se recalcula en cada cuadro). */
class ZonaToque(val parametro: Parametro, val area: Rect)

// ─────────────────────────────────────────────────────────────
//  Pantalla de MONITOREO: caja 3D animada con los datos en vivo
// ─────────────────────────────────────────────────────────────
@Composable
fun PantallaMonitoreo(c: Contenedor, modoTarde: Boolean, alCambiarModo: (Boolean) -> Unit) {
    val lectura by c.conexion.lectura.collectAsState()
    val estado by c.conexion.estado.collectAsState()
    val usuario by c.sesion.usuario.collectAsState()

    val mezclaTema by animateFloatAsState(if (modoTarde) 1f else 0f, tween(800), label = "tema")
    val paleta = mezclarPaletas(PaletaDia, PaletaTarde, mezclaTema)

    // El giro sigue el PWM real del ventilador; la ventana sigue el ángulo real
    val velocidad = animateFloatAsState(
        lectura?.let { if (it.ventiladorEncendido) it.velocidadVentilador.coerceIn(0.2f, 1f) else 0f } ?: 0f,
        tween(1500), label = "vel"
    )
    val anguloVentana = animateFloatAsState(lectura?.anguloVentana ?: 0f, tween(900), label = "ventana")

    val tiempo = remember { mutableFloatStateOf(0f) }
    val aspas = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var ultimo = 0L
        while (true) withFrameNanos { ahora ->
            if (ultimo != 0L) {
                val dt = ((ahora - ultimo) / 1_000_000_000f).coerceAtMost(0.05f)
                tiempo.floatValue += dt
                aspas.floatValue = (aspas.floatValue + 1080f * velocidad.value * dt) % 360f
            }
            ultimo = ahora
        }
    }

    var seleccion by remember { mutableStateOf<ZonaToque?>(null) }
    val zonas = remember { mutableListOf<ZonaToque>() }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(paleta.fondo))) {
        // Cielo con sol, nubes que pasan lento y colinas
        Canvas(Modifier.fillMaxSize()) { dibujarCielo(tiempo.floatValue, mezclaTema) }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BarraSuperior(paleta, modoTarde, alCambiarModo)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                EscenaInvernadero(
                    lectura = lectura,
                    tiempo = tiempo,
                    aspas = aspas,
                    velocidad = velocidad,
                    anguloVentana = anguloVentana,
                    paleta = paleta,
                    seleccionado = seleccion?.parametro,
                    zonas = zonas,
                    alTocar = { z -> seleccion = if (z == null || z.parametro == seleccion?.parametro) null else z },
                    modifier = Modifier.fillMaxSize()
                )
                val sel = seleccion
                val l = lectura
                if (sel != null && l != null) {
                    PanelFlotante(sel.area) {
                        PanelModificador(
                            parametro = sel.parametro,
                            l = l,
                            puedeActivar = usuario?.rol?.puedeActivar == true,
                            c = c,
                            alCerrar = { seleccion = null }
                        )
                    }
                }
            }
            BarraEstado(lectura, estado, paleta)
        }
    }
}

@Composable
private fun BarraSuperior(p: Paleta, modoTarde: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.tarjeta).padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Monitoreo", color = p.texto, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        val colores = SegmentedButtonDefaults.colors(
            activeContainerColor = p.botonActivo,
            activeContentColor = p.textoBoton,
            activeBorderColor = p.botonActivo,
            inactiveContainerColor = Color.Transparent,
            inactiveContentColor = p.texto,
            inactiveBorderColor = p.texto.copy(alpha = 0.35f),
        )
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = !modoTarde,
                onClick = { alCambiar(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                colors = colores,
                icon = { IconoSol(Modifier.size(18.dp)) },
                label = { Text("Día") }
            )
            SegmentedButton(
                selected = modoTarde,
                onClick = { alCambiar(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                colors = colores,
                icon = { IconoAtardecer(Modifier.size(18.dp)) },
                label = { Text("Tarde") }
            )
        }
    }
}

/** Sol: disco + 8 rayos, dibujado con el color de contenido actual. */
@Composable
private fun IconoSol(modifier: Modifier) {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(modifier) {
        val c = center
        val r = size.minDimension * 0.22f
        drawCircle(color, r, c)
        val trazo = size.minDimension * 0.09f
        for (k in 0 until 8) {
            val ang = k * PI.toFloat() / 4f
            val dir = Offset(kotlin.math.cos(ang), sin(ang))
            drawLine(color, c + dir * (r * 1.55f), c + dir * (size.minDimension * 0.47f), trazo, StrokeCap.Round)
        }
    }
}

/** Atardecer: medio sol sobre el horizonte, con tres rayos. */
@Composable
private fun IconoAtardecer(modifier: Modifier) {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val trazo = size.minDimension * 0.09f
        val horizonte = h * 0.72f
        val r = w * 0.24f
        drawArc(color, 180f, 180f, true, Offset(w / 2f - r, horizonte - r), Size(r * 2, r * 2))
        drawLine(color, Offset(w * 0.06f, horizonte), Offset(w * 0.94f, horizonte), trazo, StrokeCap.Round)
        val c = Offset(w / 2f, horizonte)
        for (ang in listOf(-150f, -90f, -30f)) {
            val rad = ang * PI.toFloat() / 180f
            val dir = Offset(kotlin.math.cos(rad), sin(rad))
            drawLine(color, c + dir * (r * 1.45f), c + dir * (r * 1.95f), trazo, StrokeCap.Round)
        }
    }
}

@Composable
private fun BarraEstado(lectura: LecturaEsp32?, e: EstadoConexion, p: Paleta) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.tarjeta).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (e.conectado) p.verde else MaterialTheme.colorScheme.error))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.descripcion, color = p.texto, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                e.mensaje ?: if (e.compartiendo) "Compartiendo con ${e.telefonosConectados} teléfono(s)" else "Toca un dato para modificarlo",
                color = p.texto.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (lectura != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(CircleShape).background(p.verde.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("${lectura.humedades.size} sensores", color = p.verde, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Cielo: sol, nubes en dos capas (las lejanas más lentas) y colinas
// ─────────────────────────────────────────────────────────────
private class Nube(val y: Float, val escala: Float, val velocidad: Float, val fase: Float, val cerca: Boolean)

private val NUBES = listOf(
    Nube(0.07f, 0.55f, 0.0030f, 0.15f, false),
    Nube(0.15f, 0.80f, 0.0045f, 0.62f, false),
    Nube(0.24f, 0.45f, 0.0025f, 0.90f, false),
    Nube(0.33f, 0.70f, 0.0040f, 0.35f, false),
    Nube(0.11f, 1.05f, 0.0070f, 0.48f, true),
    Nube(0.29f, 1.25f, 0.0085f, 0.02f, true),
    Nube(0.46f, 0.95f, 0.0060f, 0.74f, true),
    Nube(0.58f, 1.15f, 0.0075f, 0.30f, true),
)

private fun DrawScope.dibujarCielo(t: Float, tarde: Float) {
    val w = size.width
    val h = size.height

    // Sol: arriba a la derecha de día, grande y bajo al atardecer
    val sol = lerpOffset(Offset(w * 0.84f, h * 0.10f), Offset(w * 0.22f, h * 0.72f), tarde)
    val colorSol = lerp(Color(0xFFFFF4B8), Color(0xFFFFB37A), tarde)
    val radio = w * (0.065f + 0.05f * tarde)
    drawCircle(Brush.radialGradient(listOf(colorSol.copy(alpha = 0.5f), Color.Transparent), sol, radio * 3.4f), radio * 3.4f, sol)
    drawCircle(colorSol, radio, sol)

    // Nubes: blancas de día, durazno al atardecer
    val colorNube = lerp(Color.White, Color(0xFFFFD2B8), tarde)
    for (n in NUBES) {
        val ancho = w * 0.34f * n.escala
        val x = ((n.fase + t * n.velocidad) % 1f) * (w + ancho * 2) - ancho
        val alpha = (if (n.cerca) 0.88f else 0.55f) * (1f - 0.3f * tarde)
        dibujarNube(Offset(x, h * n.y), ancho, colorNube.copy(alpha = alpha))
    }

    // Paisaje en tres capas: cada colina con su vegetación; lo lejano más chico y desteñido (profundidad)
    val luz = 1f - 2f * tarde          // de día la luz viene de la derecha; al atardecer, de la izquierda
    val lejana = Colina(h * 0.74f, h * 0.030f, 1.1f, 2.6f)
    colina(lejana.base, lejana.amplitud, lejana.frecuencia, lejana.fase, lerp(Color(0xFFBBDCC3), Color(0xFF8E5A62), tarde))
    val tonoLejos = TonoArbol(
        copa = lerp(Color(0xFF93C79E), Color(0xFF74485A), tarde), pino = lerp(Color(0xFF84BB92), Color(0xFF6A4152), tarde),
        arbusto = lerp(Color(0xFF9CCDA6), Color(0xFF7A4C5E), tarde), tronco = lerp(Color(0xFF86A08D), Color(0xFF5E3B4A), tarde)
    )
    for (a in ARBOLES_LEJOS) dibujarArbol(a, lejana, h * 0.050f, tonoLejos, t, luz, tarde)

    val media = Colina(h * 0.80f, h * 0.035f, 1.4f, 0.4f)
    colina(media.base, media.amplitud, media.frecuencia, media.fase, lerp(Color(0xFFA5D6A7), Color(0xFF7A4F55), tarde))
    val tonoMedio = TonoArbol(
        copa = lerp(Color(0xFF62AB6D), Color(0xFF5C3645), tarde), pino = lerp(Color(0xFF4C915E), Color(0xFF4E2E3C), tarde),
        arbusto = lerp(Color(0xFF6DB477), Color(0xFF633B4B), tarde), tronco = lerp(Color(0xFF7A5A43), Color(0xFF3F2733), tarde)
    )
    for (a in ARBOLES_MEDIO) dibujarArbol(a, media, h * 0.080f, tonoMedio, t, luz, tarde)

    val cercana = Colina(h * 0.88f, h * 0.030f, 2.2f, 1.9f)
    colina(cercana.base, cercana.amplitud, cercana.frecuencia, cercana.fase, lerp(Color(0xFF81C784), Color(0xFF4E3440), tarde))
    val tonoCerca = TonoArbol(
        copa = lerp(Color(0xFF3F8F4E), Color(0xFF462A37), tarde), pino = lerp(Color(0xFF2F7745), Color(0xFF3A2230), tarde),
        arbusto = lerp(Color(0xFF55A15E), Color(0xFF4F2F3D), tarde), tronco = lerp(Color(0xFF6B4B36), Color(0xFF33202A), tarde)
    )
    for (a in ARBOLES_CERCA) dibujarArbol(a, cercana, h * 0.105f, tonoCerca, t, luz, tarde)
}

// ─────────────────────────────────────────────────────────────
//  Vegetación del fondo: árboles de copa, pinos, arbustos y pasto
// ─────────────────────────────────────────────────────────────
private class Colina(val base: Float, val amplitud: Float, val frecuencia: Float, val fase: Float)

/** Altura de la colina en x (misma fórmula que colina()). */
private fun DrawScope.sueloEn(c: Colina, x: Float) =
    c.base - c.amplitud * sin(x / size.width * PI.toFloat() * c.frecuencia + c.fase)

private enum class TipoArbol { COPA, PINO, ARBUSTO, PASTO }

/** x en fracción del ancho; escala relativa; fase para que no se mezan todos a la vez. */
private class Arbol(val x: Float, val tipo: TipoArbol, val escala: Float = 1f, val fase: Float = x * 9.1f)

private class TonoArbol(val copa: Color, val pino: Color, val arbusto: Color, val tronco: Color)

private val ARBOLES_LEJOS = listOf(
    Arbol(0.02f, TipoArbol.PINO, 0.9f), Arbol(0.06f, TipoArbol.COPA, 1.1f), Arbol(0.10f, TipoArbol.PINO, 1.2f),
    Arbol(0.29f, TipoArbol.COPA, 0.9f), Arbol(0.33f, TipoArbol.PINO, 1.0f),
    Arbol(0.61f, TipoArbol.PINO, 1.1f), Arbol(0.65f, TipoArbol.COPA, 1.0f), Arbol(0.69f, TipoArbol.PINO, 0.8f),
    Arbol(0.87f, TipoArbol.COPA, 1.0f), Arbol(0.92f, TipoArbol.PINO, 1.2f), Arbol(0.97f, TipoArbol.COPA, 0.9f),
)

private val ARBOLES_MEDIO = listOf(
    Arbol(0.04f, TipoArbol.PINO, 1.1f), Arbol(0.11f, TipoArbol.COPA, 1.0f), Arbol(0.18f, TipoArbol.ARBUSTO, 0.9f),
    Arbol(0.40f, TipoArbol.COPA, 0.9f), Arbol(0.57f, TipoArbol.PINO, 1.0f),
    Arbol(0.79f, TipoArbol.COPA, 1.15f), Arbol(0.86f, TipoArbol.PINO, 0.95f), Arbol(0.95f, TipoArbol.ARBUSTO, 1.0f),
)

private val ARBOLES_CERCA = listOf(
    Arbol(0.02f, TipoArbol.COPA, 1.2f), Arbol(0.09f, TipoArbol.ARBUSTO, 1.0f), Arbol(0.15f, TipoArbol.PASTO),
    Arbol(0.26f, TipoArbol.PASTO, 0.9f), Arbol(0.35f, TipoArbol.ARBUSTO, 0.8f), Arbol(0.49f, TipoArbol.PASTO),
    Arbol(0.63f, TipoArbol.ARBUSTO, 0.9f), Arbol(0.72f, TipoArbol.PASTO, 1.1f), Arbol(0.84f, TipoArbol.ARBUSTO, 1.1f),
    Arbol(0.92f, TipoArbol.PINO, 1.1f), Arbol(0.98f, TipoArbol.PASTO, 0.9f),
)

private fun DrawScope.dibujarArbol(a: Arbol, colina: Colina, altoBase: Float, tono: TonoArbol, t: Float, luz: Float, tarde: Float) {
    val x = a.x * size.width
    val suelo = sueloEn(colina, x) + altoBase * 0.06f      // un poco enterrado, para que no flote
    val alto = altoBase * a.escala
    val brisa = sin(t * 0.9f + a.fase) * alto * 0.035f      // vaivén muy leve de la copa
    val brillo = lerp(Color.White, Color(0xFFFFC9A0), tarde).copy(alpha = 0.16f)

    when (a.tipo) {
        TipoArbol.COPA -> {
            val ancho = alto * 0.11f
            drawRect(tono.tronco, Offset(x - ancho / 2f, suelo - alto * 0.48f), Size(ancho, alto * 0.48f))
            val cx = x + brisa
            val copa = Path().apply {
                addOval(Rect(Offset(cx, suelo - alto * 0.62f), alto * 0.30f))
                addOval(Rect(Offset(cx - alto * 0.20f, suelo - alto * 0.50f), alto * 0.21f))
                addOval(Rect(Offset(cx + alto * 0.20f, suelo - alto * 0.52f), alto * 0.22f))
                addOval(Rect(Offset(cx + brisa * 0.4f, suelo - alto * 0.82f), alto * 0.20f))
            }
            drawPath(copa, tono.copa)
            drawCircle(brillo, alto * 0.16f, Offset(cx + luz * alto * 0.12f, suelo - alto * 0.72f))
        }
        TipoArbol.PINO -> {
            val ancho = alto * 0.08f
            drawRect(tono.tronco, Offset(x - ancho / 2f, suelo - alto * 0.22f), Size(ancho, alto * 0.22f))
            val pisos = listOf(Triple(0.18f, 0.56f, 0.30f), Triple(0.40f, 0.80f, 0.24f), Triple(0.60f, 1.00f, 0.17f))
            for ((i, piso) in pisos.withIndex()) {
                val (abajo, arriba, mitad) = piso
                val dx = brisa * (0.4f + i * 0.3f)
                val tri = Path().apply {
                    moveTo(x - alto * mitad, suelo - alto * abajo)
                    lineTo(x + alto * mitad, suelo - alto * abajo)
                    lineTo(x + dx, suelo - alto * arriba)
                    close()
                }
                drawPath(tri, tono.pino)
            }
            // Lado iluminado del pino
            val luzPino = Path().apply {
                moveTo(x + brisa, suelo - alto)
                lineTo(x + luz * alto * 0.30f, suelo - alto * 0.18f)
                lineTo(x, suelo - alto * 0.18f)
                close()
            }
            drawPath(luzPino, brillo.copy(alpha = 0.10f))
        }
        TipoArbol.ARBUSTO -> {
            val r = alto * 0.20f
            val mata = Path().apply {
                addOval(Rect(Offset(x - r * 0.9f + brisa * 0.3f, suelo - r * 0.55f), r * 0.85f))
                addOval(Rect(Offset(x + brisa * 0.5f, suelo - r * 0.85f), r * 1.1f))
                addOval(Rect(Offset(x + r * 0.95f + brisa * 0.3f, suelo - r * 0.5f), r * 0.8f))
            }
            drawPath(mata, tono.arbusto)
            drawCircle(brillo, r * 0.5f, Offset(x + luz * r * 0.5f, suelo - r * 1.2f))
        }
        TipoArbol.PASTO -> {
            val grosor = (alto * 0.035f).coerceAtLeast(1.5f)
            for (k in -3..3) {
                val largo = alto * (0.22f + 0.06f * ((k * 7 + 3) % 4))
                val inclinacion = k * alto * 0.035f + brisa * 1.5f
                drawLine(
                    tono.arbusto,
                    Offset(x + k * alto * 0.035f, suelo),
                    Offset(x + k * alto * 0.035f + inclinacion, suelo - largo),
                    grosor,
                    StrokeCap.Round
                )
            }
        }
    }
}

/** Una nube = un solo Path con varios óvalos (así no se ven las uniones al ser semitransparente). */
private fun DrawScope.dibujarNube(c: Offset, ancho: Float, color: Color) {
    val r = ancho * 0.22f
    val nube = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                Rect(Offset(c.x - ancho / 2f, c.y - r * 0.15f), Size(ancho, r * 1.05f)),
                CornerRadius(r * 0.55f)
            )
        )
        addOval(Rect(Offset(c.x - ancho * 0.22f, c.y), r))
        addOval(Rect(Offset(c.x + ancho * 0.03f, c.y - r * 0.45f), r * 1.35f))
        addOval(Rect(Offset(c.x + ancho * 0.27f, c.y + r * 0.05f), r * 0.95f))
    }
    drawPath(nube, color)
}

private fun DrawScope.colina(base: Float, amplitud: Float, frecuencia: Float, fase: Float, color: Color) {
    val w = size.width
    val camino = Path().apply {
        moveTo(0f, size.height)
        for (i in 0..40) {
            val x = w * i / 40f
            lineTo(x, base - amplitud * sin(i / 40f * PI.toFloat() * frecuencia + fase))
        }
        lineTo(w, size.height)
        close()
    }
    drawPath(camino, color)
}

// ─────────────────────────────────────────────────────────────
//  Canvas: caja 3D grande + etiquetas tocables con latido
// ─────────────────────────────────────────────────────────────
@Composable
private fun EscenaInvernadero(
    lectura: LecturaEsp32?,
    tiempo: State<Float>,
    aspas: State<Float>,
    velocidad: State<Float>,
    anguloVentana: State<Float>,
    paleta: Paleta,
    seleccionado: Parametro?,
    zonas: MutableList<ZonaToque>,
    alTocar: (ZonaToque?) -> Unit,
    modifier: Modifier = Modifier
) {
    val medidor = rememberTextMeasurer()
    val alTocarActual by rememberUpdatedState(alTocar)
    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { pos -> alTocarActual(zonas.lastOrNull { it.area.contains(pos) }) }
        }
    ) {
        if (lectura == null) {
            zonas.clear()
            val txt = medidor.measure("Esperando datos…", TextStyle(color = paleta.texto, fontSize = 16.sp, fontFamily = FuenteTexto))
            drawText(txt, topLeft = center - Offset(txt.size.width / 2f, txt.size.height / 2f))
            return@Canvas
        }
        // La caja ocupa la mayor parte del ancho; las etiquetas flotan encima con su propio fondo
        val margen = 36.dp.toPx()
        // La caja es la protagonista (~85% del ancho útil) y queda un poco sobre el centro: arriba van
        // los datos de aire/ventilador/ventana y abajo la media luna con los sensores de tierra
        val escala = min((size.width - 2 * margen) * 0.86f / 1.85f, size.height / 2.35f)
        // un poco a la derecha (la ventana abierta sobresale a la izquierda)
        val cam = Camara(YAW_GRADOS, PITCH_GRADOS, escala, Offset(size.width * 0.54f, size.height * 0.40f))

        val anclas = dibujarEscena(
            cam,
            DatosEscena(
                humedades = lectura.humedades,
                temperatura = lectura.temperatura,
                velocidadVentilador = velocidad.value,
                anguloAspas = aspas.value,
                anguloVentana = anguloVentana.value,
                tiempo = tiempo.value
            ),
            paleta
        )

        val n = anclas.plantas.size
        val plantas = anclas.plantas.mapIndexed { i, a ->
            // Con pocas plantas cabe el estado del suelo; con muchas, solo el nombre (el estado sale al tocar)
            val titulo = if (n <= 3) lectura.estadosSuelo.getOrNull(i) ?: "Planta ${i + 1}" else "Planta ${i + 1}"
            Etiqueta(a, "${lectura.humedades[i].roundToInt()}%", titulo, Parametro.Planta(i))
        }
        // La humedad del aire se mide "en el aire" de la caja: el punto flota arriba, sobre las plantas
        val anclaAire = cam.proyectar(Vec3(0.22f, 0.30f, -0.18f))
        val laterales = listOfNotNull(
            Etiqueta(anclas.temperatura, "%.1f °C".format(lectura.temperatura), "Temperatura", Parametro.Temperatura),
            lectura.humedadAmbiente?.let {
                Etiqueta(anclaAire, "${it.roundToInt()}%", "Humedad del aire", Parametro.HumedadAire, izquierda = false)
            },
            Etiqueta(
                anclas.ventilador,
                if (lectura.ventiladorEncendido) "${(lectura.velocidadVentilador * 100).roundToInt()}%" else "OFF",
                "Ventilador · ${lectura.modoVentilador.lowercase()}",
                Parametro.Ventilador,
                izquierda = false,
                porFuera = true
            ),
            Etiqueta(anclas.ventana, "${lectura.anguloVentana.roundToInt()}°", "Ventana · ${lectura.modoVentana.lowercase()}", Parametro.Ventana, izquierda = true, porFuera = true),
        )
        zonas.clear()
        dibujarEtiquetas(laterales, medidor, paleta, tiempo.value, seleccionado, zonas, anclas.techo, desfase = n)
        dibujarMediaLuna(plantas, medidor, paleta, tiempo.value, seleccionado, zonas, anclas.base)
    }
}

/** [izquierda]: lado fijo de la tarjeta (null = el lado más cercano al objeto). */
private data class Etiqueta(
    val ancla: Offset,
    val valor: String,
    val titulo: String,
    val parametro: Parametro,
    val izquierda: Boolean? = null,
    val porFuera: Boolean = false     // la línea entra por el borde exterior de la tarjeta (el del lado de la pantalla)
)

/** Círculo sobre el objeto → línea diagonal → línea horizontal → dato con fondo (estilo Paint). */
private fun DrawScope.dibujarEtiquetas(
    lista: List<Etiqueta>,
    medidor: TextMeasurer,
    p: Paleta,
    t: Float,
    seleccionado: Parametro?,
    zonas: MutableList<ZonaToque>,
    techo: Float,
    desfase: Int = 0
) {
    val estiloValor = TextStyle(color = p.verde, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = FuenteTitulos, fontFeatureSettings = "tnum")
    val estiloTitulo = TextStyle(color = p.texto, fontSize = 11.sp, fontFamily = FuenteTexto, letterSpacing = 0.2.sp)
    val margen = 6.dp.toPx()
    val separacion = 52.dp.toPx()
    val grosor = 1.6.dp.toPx()
    val relleno = 8.dp.toPx()

    lista.withIndex()
        .groupBy { it.value.izquierda ?: (it.value.ancla.x < size.width / 2f) }
        .forEach { (izquierda, grupo) ->
            // Las etiquetas van en el cielo, SOBRE el techo de la caja, apiladas hacia arriba.
            // El objeto más cercano al borde toma la fila más baja (si están casi alineados, el más bajo):
            // así las líneas no se cruzan entre sí ni pasan bajo otras etiquetas.
            val tramo = 40.dp.toPx()
            fun distanciaAlBorde(e: Etiqueta) = ((if (izquierda) e.ancla.x else size.width - e.ancla.x) / tramo).toInt()
            val orden = grupo.sortedWith(
                compareBy<IndexedValue<Etiqueta>> { distanciaAlBorde(it.value) }.thenByDescending { it.value.ancla.y }
            )
            val ys = FloatArray(orden.size)
            var maximo = techo - 30.dp.toPx()
            orden.forEachIndexed { k, _ ->
                ys[k] = maximo
                maximo -= separacion
            }
            val falta = 30.dp.toPx() - (ys.lastOrNull() ?: 30.dp.toPx())
            if (falta > 0f) for (k in ys.indices) ys[k] += falta

            orden.forEachIndexed { k, (indice, e) ->
                val elegido = e.parametro == seleccionado
                val valor = medidor.measure(e.valor, estiloValor)
                val titulo = medidor.measure(e.titulo, estiloTitulo)
                val anchoTexto = max(valor.size.width, titulo.size.width).toFloat()
                val y = ys[k]
                val hueco = 6.dp.toPx()
                // Con porFuera la tarjeta se corre hacia adentro para dejar un pasillo junto al borde de la pantalla
                val pasillo = if (e.porFuera) 16.dp.toPx() else 0f
                val xFin = if (izquierda) margen + pasillo + relleno + anchoTexto + hueco
                           else size.width - margen - pasillo - relleno - anchoTexto - hueco
                if (e.porFuera) {
                    // objeto → hacia el borde → sube por el pasillo → entra por el lado exterior de la tarjeta
                    val xPasillo = if (izquierda) margen + pasillo * 0.45f else size.width - margen - pasillo * 0.45f
                    val xBorde = if (izquierda) margen + pasillo else size.width - margen - pasillo
                    val ruta = Path().apply {
                        moveTo(e.ancla.x, e.ancla.y)
                        lineTo(xPasillo, e.ancla.y)
                        lineTo(xPasillo, y)
                        lineTo(xBorde, y)
                    }
                    drawPath(ruta, p.linea, style = Stroke(grosor, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                } else {
                    val dy = abs(e.ancla.y - y)
                    val codoX = if (izquierda) max(e.ancla.x - dy * 0.6f, xFin + 8.dp.toPx())
                                else min(e.ancla.x + dy * 0.6f, xFin - 8.dp.toPx())
                    val codo = Offset(codoX, y)
                    drawLine(p.linea, e.ancla, codo, grosor, StrokeCap.Round)
                    drawLine(p.linea, codo, Offset(xFin, y), grosor, StrokeCap.Round)
                }

                // Fondo del dato (para leerlo aunque quede sobre la caja)
                val xValor = if (izquierda) xFin - hueco - valor.size.width else xFin + hueco
                val xTitulo = if (izquierda) xFin - hueco - titulo.size.width else xFin + hueco
                val izq = min(xValor, xTitulo) - relleno
                val der = max(xValor + valor.size.width, xTitulo + titulo.size.width) + relleno
                val bloque = Rect(izq, y - valor.size.height - relleno * 0.6f, der, y + titulo.size.height + relleno)
                drawRoundRect(p.tarjeta, bloque.topLeft, bloque.size, CornerRadius(14.dp.toPx()))
                if (elegido) {
                    drawRoundRect(p.verde, bloque.topLeft, bloque.size, CornerRadius(14.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
                drawText(valor, topLeft = Offset(xValor, y - valor.size.height))
                drawText(titulo, topLeft = Offset(xTitulo, y + 1.dp.toPx()))

                dibujarLatido(e.ancla, t + (indice + desfase) * 0.37f, p, elegido)

                // Zonas tocables: el bloque de texto y el círculo
                zonas += ZonaToque(e.parametro, bloque.inflate(6.dp.toPx()))
                zonas += ZonaToque(e.parametro, Rect(e.ancla, 22.dp.toPx()))
            }
        }
}

/**
 * Sensores de tierra: tarjetas repartidas en media luna bajo la caja (medio óvalo, separadas por
 * igual a lo largo del arco). Se ordenan según dónde está cada sensor, de izquierda a derecha,
 * para que las líneas no se crucen; cada línea termina en el borde de su tarjeta.
 */
private fun DrawScope.dibujarMediaLuna(
    lista: List<Etiqueta>,
    medidor: TextMeasurer,
    p: Paleta,
    t: Float,
    seleccionado: Parametro?,
    zonas: MutableList<ZonaToque>,
    base: Float
) {
    if (lista.isEmpty()) return
    val n = lista.size
    val estiloValor = TextStyle(color = p.verde, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = FuenteTitulos, fontFeatureSettings = "tnum")
    val estiloTitulo = TextStyle(color = p.texto, fontSize = 11.sp, fontFamily = FuenteTexto, letterSpacing = 0.2.sp)
    val relleno = 8.dp.toPx()
    val grosor = 1.6.dp.toPx()
    val margen = 8.dp.toPx()
    val textos = lista.map { medidor.measure(it.valor, estiloValor) to medidor.measure(it.titulo, estiloTitulo) }
    val tamanos = textos.map { (v, ti) -> Size(max(v.size.width, ti.size.width) + 2 * relleno, v.size.height + ti.size.height + relleno * 1.6f) }
    val anchoMax = tamanos.maxOf { it.width }
    val altoMax = tamanos.maxOf { it.height }

    // Medio óvalo: extremos justo bajo la caja, el punto más bajo cerca del borde inferior
    val cx = size.width / 2f
    val rx = (size.width / 2f - margen - anchoMax / 2f).coerceAtLeast(0f)
    val yExtremos = base + 14.dp.toPx() + altoMax / 2f
    val ry = (size.height - margen - altoMax / 2f - yExtremos).coerceIn(0f, rx * 0.8f)
    val muestras = 72
    val pts = List(muestras + 1) { k ->
        val th = PI.toFloat() * k / muestras
        Offset(cx - rx * cos(th), yExtremos + ry * sin(th))
    }
    val acumulado = FloatArray(muestras + 1)
    for (k in 1..muestras) acumulado[k] = acumulado[k - 1] + (pts[k] - pts[k - 1]).getDistance()
    val total = acumulado[muestras]
    fun enArco(f: Float): Offset {
        val objetivo = f * total
        var k = 1
        while (k < muestras && acumulado[k] < objetivo) k++
        val tramo = (acumulado[k] - acumulado[k - 1]).coerceAtLeast(0.001f)
        return lerpOffset(pts[k - 1], pts[k], ((objetivo - acumulado[k - 1]) / tramo).coerceIn(0f, 1f))
    }
    val centros = arrayOfNulls<Offset>(n)
    lista.indices.sortedBy { lista[it].ancla.x }.forEachIndexed { pos, i -> centros[i] = enArco((pos + 0.5f) / n) }

    // Primero todas las líneas (así ninguna pasa por encima de otra tarjeta), luego las tarjetas
    lista.forEachIndexed { i, e ->
        val c = centros[i]!!
        val d = e.ancla - c
        val s = min(
            if (abs(d.x) > 0.01f) tamanos[i].width / 2f / abs(d.x) else Float.MAX_VALUE,
            if (abs(d.y) > 0.01f) tamanos[i].height / 2f / abs(d.y) else Float.MAX_VALUE
        ).coerceAtMost(1f)
        drawLine(p.linea, e.ancla, c + d * s, grosor, StrokeCap.Round)
    }
    lista.forEachIndexed { i, e ->
        val c = centros[i]!!
        val (valor, titulo) = textos[i]
        val tam = tamanos[i]
        val bloque = Rect(c.x - tam.width / 2f, c.y - tam.height / 2f, c.x + tam.width / 2f, c.y + tam.height / 2f)
        val elegido = e.parametro == seleccionado
        drawRoundRect(p.tarjeta, bloque.topLeft, bloque.size, CornerRadius(14.dp.toPx()))
        if (elegido) drawRoundRect(p.verde, bloque.topLeft, bloque.size, CornerRadius(14.dp.toPx()), style = Stroke(2.dp.toPx()))
        drawText(valor, topLeft = Offset(c.x - valor.size.width / 2f, bloque.top + relleno * 0.8f))
        drawText(titulo, topLeft = Offset(c.x - titulo.size.width / 2f, bloque.top + relleno * 0.8f + valor.size.height))
        dibujarLatido(e.ancla, t + i * 0.37f, p, elegido)
        zonas += ZonaToque(e.parametro, bloque.inflate(4.dp.toPx()))
        zonas += ZonaToque(e.parametro, Rect(e.ancla, 22.dp.toPx()))
    }
}

/** Latido lento y suave (≈ 2,4 s por ciclo, doble pulso) con una onda que se desvanece. */
private fun DrawScope.dibujarLatido(c: Offset, t: Float, p: Paleta, elegido: Boolean) {
    val periodo = 2.4f
    val f = (t % periodo) / periodo
    fun golpe(centro: Float, ancho: Float) = exp(-((f - centro) / ancho).let { it * it })
    val latido = golpe(0.10f, 0.08f) + 0.55f * golpe(0.34f, 0.09f)
    val base = (if (elegido) 8 else 6).dp.toPx()

    val onda = (f * 1.15f).coerceAtMost(1f)
    val alfaOnda = 0.35f * (1f - onda) * (1f - onda)
    drawCircle(p.verde.copy(alpha = alfaOnda), base + 14.dp.toPx() * onda, c, style = Stroke(1.5.dp.toPx()))

    val r = base * (1f + 0.22f * latido)
    drawCircle(p.tarjeta, r, c)
    drawCircle(p.linea, r, c, style = Stroke((if (elegido) 2.5f else 2f).dp.toPx()))
    drawCircle(p.verde, r * 0.45f, c)
}

// ─────────────────────────────────────────────────────────────
//  Panel que aparece junto al dato tocado
// ─────────────────────────────────────────────────────────────

/** Coloca el panel bajo el dato (o encima si no cabe), sin salirse de la pantalla. */
@Composable
private fun PanelFlotante(area: Rect, contenido: @Composable () -> Unit) {
    Layout(content = contenido, modifier = Modifier.fillMaxSize()) { medibles, restricciones ->
        val w = restricciones.maxWidth
        val h = restricciones.maxHeight
        val margen = 8.dp.roundToPx()
        val ancho = min(290.dp.roundToPx(), w - 2 * margen)
        val panel = medibles.first().measure(Constraints(minWidth = ancho, maxWidth = ancho, maxHeight = h))
        val x = (area.center.x - panel.width / 2f).roundToInt().coerceIn(margen, max(margen, w - panel.width - margen))
        val debajo = (area.bottom + margen).roundToInt()
        val y = if (debajo + panel.height <= h - margen) debajo
                else max(margen, (area.top - margen - panel.height).roundToInt())
        layout(w, h) { panel.place(x, y) }
    }
}

@Composable
private fun PanelModificador(
    parametro: Parametro,
    l: LecturaEsp32,
    puedeActivar: Boolean,
    c: Contenedor,
    alCerrar: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var aviso by remember(parametro) { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    val habilitado = puedeActivar && !enviando

    fun enviar(vararg codigos: String) {
        if (enviando) return
        scope.launch {
            enviando = true
            aviso = "Enviando…"
            var ok = true
            for (cod in codigos) if (!c.conexion.enviar(cod)) ok = false
            enviando = false
            aviso = if (ok) "Enviado: " + codigos.joinToString(" + ") { Comandos.descripcion(it) } else "No se pudo enviar. Revisa la conexión."
        }
    }

    val titulo = when (parametro) {
        is Parametro.Planta -> "Planta ${parametro.indice + 1}"
        Parametro.Temperatura -> "Temperatura"
        Parametro.HumedadAire -> "Humedad del aire"
        Parametro.Ventilador -> "Ventilador"
        Parametro.Ventana -> "Ventana"
    }
    val verde = MaterialTheme.colorScheme.secondary

    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = alCerrar) { Icon(Icons.Filled.Close, contentDescription = "Cerrar") }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (parametro) {
                    is Parametro.Planta -> {
                        val h = l.humedades.getOrNull(parametro.indice) ?: 0f
                        Text("${h.roundToInt()}% de humedad de suelo", color = verde, style = MaterialTheme.typography.headlineSmall)
                        LinearProgressIndicator(progress = { h / 100f }, modifier = Modifier.fillMaxWidth(), color = verde)
                        Text(l.estadosSuelo.getOrNull(parametro.indice) ?: "", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Este valor solo se monitorea: el ESP32 no tiene bomba de riego conectada.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Parametro.Temperatura -> {
                        Text("%.1f °C".format(l.temperatura), color = verde, style = MaterialTheme.typography.headlineSmall)
                        l.humedadAmbiente?.let { Text("Humedad del aire: ${it.roundToInt()}%") }
                        val r = l.parametros ?: com.invernadero.conexion.ParametrosControl.FABRICA
                        Text(
                            "En automático el ventilador se enciende sobre ${r.ventiladorSobre} °C y la ventana se abre " +
                                "entre ${r.ventanaDesde} y ${r.ventanaHasta} °C. Cámbialo en Controles.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { enviar(Comandos.MAXIMO, Comandos.ABRIR) }, enabled = habilitado) { Text("Ventilar") }
                            OutlinedButton(onClick = { enviar(Comandos.AUTOMATICO) }, enabled = habilitado) { Text("Automático") }
                        }
                    }

                    Parametro.HumedadAire -> {
                        val ha = l.humedadAmbiente ?: 0f
                        Text("${ha.roundToInt()}% de humedad del aire", color = verde, style = MaterialTheme.typography.headlineSmall)
                        LinearProgressIndicator(progress = { ha / 100f }, modifier = Modifier.fillMaxWidth(), color = verde)
                        Text("Temperatura: %.1f °C".format(l.temperatura), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Medida dentro de la caja por el mismo sensor de la temperatura. Solo se monitorea: " +
                                "para bajarla, ventila (ventilador y ventana).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Parametro.Ventilador -> {
                        var arrastre by remember { mutableStateOf<Float?>(null) }
                        val pwm = arrastre ?: (l.velocidadVentilador * 255f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (l.ventiladorEncendido) "Encendido" else "Apagado", style = MaterialTheme.typography.bodyLarge)
                                Text("Modo ${l.modoVentilador.lowercase()}", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = l.ventiladorEncendido,
                                onCheckedChange = { enviar(if (it) Comandos.MAXIMO else Comandos.APAGAR) },
                                enabled = habilitado
                            )
                        }
                        Text("Velocidad: ${(pwm / 2.55f).roundToInt()}%  (PWM ${pwm.roundToInt()})", color = verde)
                        Slider(
                            value = pwm,
                            onValueChange = { arrastre = it },
                            onValueChangeFinished = {
                                arrastre?.let { enviar(Comandos.velocidad(it.roundToInt())) }
                                arrastre = null
                            },
                            valueRange = 0f..255f,
                            enabled = habilitado
                        )
                        TextButton(onClick = { enviar(Comandos.AUTOMATICO) }, enabled = habilitado) { Text("Volver a automático") }
                    }

                    Parametro.Ventana -> {
                        var arrastre by remember { mutableStateOf<Float?>(null) }
                        val apertura = arrastre ?: l.anguloVentana
                        Text("Apertura: ${apertura.roundToInt()}° de 90°", color = verde, style = MaterialTheme.typography.titleMedium)
                        Text("Modo ${l.modoVentana.lowercase()}", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = apertura,
                            onValueChange = { arrastre = it },
                            onValueChangeFinished = {
                                arrastre?.let { enviar(Comandos.ventana(it.roundToInt())) }
                                arrastre = null
                            },
                            valueRange = 0f..90f,
                            steps = 8,
                            enabled = habilitado
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { enviar(Comandos.CERRAR) }, enabled = habilitado) { Text("Cerrar") }
                            Button(onClick = { enviar(Comandos.ABRIR) }, enabled = habilitado) { Text("Abrir") }
                        }
                        TextButton(onClick = { enviar(Comandos.AUTOMATICO) }, enabled = habilitado) { Text("Volver a automático") }
                    }
                }
                if (!puedeActivar && parametro !is Parametro.Planta && parametro != Parametro.HumedadAire) {
                    Text("Tu rol solo permite ver los datos.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                aviso?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}
