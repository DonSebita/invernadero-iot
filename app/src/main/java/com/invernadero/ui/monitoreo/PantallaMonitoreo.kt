@file:OptIn(ExperimentalMaterial3Api::class)

package com.invernadero.ui.monitoreo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import android.content.res.Configuration
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material3.Card
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.invernadero.datos.PlantasRepo
import com.invernadero.datos.nombrePlanta
import com.invernadero.ui.mostrar
import com.invernadero.ui.rememberPedido
import kotlinx.coroutines.Dispatchers
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
import com.invernadero.conexion.haceCuanto
import com.invernadero.datos.ModoConexion
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
    data object Plantas : Parametro
    data object GestionPlantas : Parametro      // botón "+" junto a la hoja: añadir o quitar plantas
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
fun PantallaMonitoreo(c: Contenedor, modoTarde: Boolean, alCambiarModo: (Boolean) -> Unit, alConfigurar: () -> Unit = {}) {
    val lectura by c.conexion.lectura.collectAsState()
    val estado by c.conexion.estado.collectAsState()
    val usuario by c.sesion.usuario.collectAsState()
    val nombres by c.plantas.nombres.collectAsState()
    val esperando by c.plantas.esperando.collectAsState()
    val quitadas by c.plantas.quitadas.collectAsState()
    LaunchedEffect(lectura?.humedades?.size) { lectura?.humedades?.size?.let { c.plantas.sincronizar(it) } }
    val plantas = lectura?.let { plantasVisibles(it, quitadas, esperando) } ?: emptyList()

    val apaisado = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
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
    // Dónde dejó el usuario cada tarjeta (fracción de la escena); sin entrada = lugar automático
    val posiciones = remember {
        mutableStateMapOf<String, Offset>().apply {
            putAll(c.ajustes.posicionesTarjetas.mapValues { (_, p) -> Offset(p.first, p.second) })
        }
    }
    fun guardarPosiciones() {
        c.ajustes.posicionesTarjetas = posiciones.mapValues { (_, o) -> o.x to o.y }
    }

    // Pista "mantén presionada una tarjeta para moverla": las primeras veces (mientras no se haya
    // movido ninguna) y después de vez en cuando. Se va sola o al tocar la escena.
    var pistaVisible by remember { mutableStateOf(false) }
    val pista = animateFloatAsState(if (pistaVisible) 1f else 0f, tween(450), label = "pista")
    LaunchedEffect(Unit) {
        val aperturas = c.ajustes.aperturasMonitoreo + 1
        c.ajustes.aperturasMonitoreo = aperturas
        val vistas = c.ajustes.pistaTarjetasVista
        if ((posiciones.isEmpty() && vistas < 4) || aperturas % 10 == 0) {
            c.conexion.lectura.first { it != null }
            delay(1500)
            pistaVisible = true
            c.ajustes.pistaTarjetasVista = vistas + 1
            delay(9000)
            pistaVisible = false
        }
    }

    // El cielo llena toda la pantalla, también detrás de la barra de estado y la cámara;
    // solo el contenido se aparta de esas zonas
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(paleta.fondo))) {
        Canvas(Modifier.fillMaxSize()) { dibujarCielo(tiempo.floatValue, mezclaTema) }

        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 8.dp, vertical = if (apaisado) 6.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (apaisado) 4.dp else 8.dp)
        ) {
            BarraSuperior(paleta, modoTarde, alCambiarModo)

            // Qué pasa con los datos: demostración, nube, cargando o error (la caja se ve igual)
            val esInvitado = usuario?.invitado == true
            val falla = lectura == null && estado.fallas > 0 && estado.mensaje != null
            val configurar = if (esInvitado) null else "Configurar" to alConfigurar
            when {
                estado.sinConfigurar -> AvisoConexion("Datos de demostración: aún no configuras tu ESP32.", false, paleta, configurar)
                estado.modo == ModoConexion.SIMULADOR -> AvisoConexion("Modo demostración: estos datos son simulados.", false, paleta, "Cambiar" to alConfigurar)
                estado.desdeNube && estado.conectado -> AvisoConexion("Mirando desde la nube · ${haceCuanto(estado.nubeTs)} (solo mirar)", false, paleta, null)
                estado.desdeNube -> AvisoConexion(estado.mensaje ?: "La nube no recibe datos del ESP32", true, paleta, null)
                falla -> AvisoConexion("Error: ${estado.mensaje}", true, paleta, configurar)
                lectura != null && !estado.conectado ->
                    AvisoConexion("Sin conexión con el ESP32 (${estado.mensaje ?: "sin datos"}). Lo que ves puede estar desactualizado.", true, paleta, null)
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                EscenaInvernadero(
                    lectura = lectura,
                    sinDato = if (falla) "Error" else "Cargando…",
                    plantas = plantas.map { it.humedad },
                    tiempo = tiempo,
                    aspas = aspas,
                    velocidad = velocidad,
                    anguloVentana = anguloVentana,
                    paleta = paleta,
                    seleccionado = seleccion?.parametro,
                    zonas = zonas,
                    posiciones = posiciones,
                    alMover = { clave, fraccion -> posiciones[clave] = fraccion },
                    alSoltar = { guardarPosiciones() },
                    alTocar = { z -> seleccion = if (z == null || z.parametro == seleccion?.parametro) null else z },
                    pista = pista,
                    alInteractuar = { pistaVisible = false },
                    modifier = Modifier.fillMaxSize()
                )
                val sel = seleccion
                val l = lectura
                if (sel != null && l != null) {
                    PanelFlotante(sel.area) {
                        PanelModificador(
                            parametro = sel.parametro,
                            l = l,
                            plantas = plantas,
                            quitadas = quitadas,
                            puedeActivar = usuario?.rol?.puedeActivar == true,
                            puedeRenombrar = usuario?.invitado == false,
                            nombres = nombres,
                            c = c,
                            alCerrar = { seleccion = null }
                        )
                    }
                }
            }
        }
    }
}

/** Franja bajo la barra superior: avisa si los datos son de demostración, de la nube o si hay un error. */
@Composable
private fun AvisoConexion(texto: String, error: Boolean, p: Paleta, accion: Pair<String, () -> Unit>?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.tarjeta)
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            texto,
            color = if (error) MaterialTheme.colorScheme.error else p.texto,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp)
        )
        accion?.let { (etiqueta, alPulsar) -> TextButton(onClick = alPulsar) { Text(etiqueta, color = p.verde) } }
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

// ─────────────────────────────────────────────────────────────
//  Cielo: sol, nubes en dos capas (las lejanas más lentas), aves, colinas y animales
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
    // Ancho de referencia: en vertical es el ancho; en horizontal no crece más que 0,6 × alto.
    // Sol, nubes, colinas y árboles se miden con él, y el paisaje se repite a lo ancho en vez de estirarse.
    val ref = min(w, h * 0.6f)

    // Sol: arriba a la derecha de día, grande y bajo al atardecer
    val sol = lerpOffset(Offset(w * 0.84f, h * 0.10f), Offset(w * 0.22f, h * 0.72f), tarde)
    val colorSol = lerp(Color(0xFFFFF4B8), Color(0xFFFFB37A), tarde)
    val radio = ref * (0.065f + 0.05f * tarde)
    drawCircle(Brush.radialGradient(listOf(colorSol.copy(alpha = 0.5f), Color.Transparent), sol, radio * 3.4f), radio * 3.4f, sol)
    drawCircle(colorSol, radio, sol)

    // Nubes: blancas de día, durazno al atardecer
    val colorNube = lerp(Color.White, Color(0xFFFFD2B8), tarde)
    val copias = (w / ref).roundToInt().coerceIn(1, 3)   // más nubes si la pantalla es más ancha
    for (copia in 0 until copias) for (n in NUBES) {
        val ancho = ref * 0.34f * n.escala
        val x = ((n.fase + copia.toFloat() / copias + t * n.velocidad * ref / w) % 1f) * (w + ancho * 2) - ancho
        val alpha = (if (n.cerca) 0.88f else 0.55f) * (1f - 0.3f * tarde)
        dibujarNube(Offset(x, h * n.y), ancho, colorNube.copy(alpha = alpha))
    }
    dibujarAves(t, tarde, ref)

    // Paisaje en tres capas: cada colina con su vegetación; lo lejano más chico y desteñido (profundidad)
    val luz = 1f - 2f * tarde          // de día la luz viene de la derecha; al atardecer, de la izquierda
    val lejana = Colina(h * 0.74f, min(h, ref * 2f) * 0.030f, 1.1f, 2.6f, ref)
    val colorLejana = lerp(Color(0xFFBBDCC3), Color(0xFF8E5A62), tarde)
    colina(lejana, colorLejana)
    val tonoLejos = TonoArbol(
        copa = lerp(Color(0xFF93C79E), Color(0xFF74485A), tarde), pino = lerp(Color(0xFF84BB92), Color(0xFF6A4152), tarde),
        arbusto = lerp(Color(0xFF9CCDA6), Color(0xFF7A4C5E), tarde), tronco = lerp(Color(0xFF86A08D), Color(0xFF5E3B4A), tarde)
    )
    repetirAlAncho(ref) { desde -> for (a in ARBOLES_LEJOS) dibujarArbol(a, desde + a.x * ref, lejana, min(h, ref * 2f) * 0.050f, tonoLejos, t, luz, tarde) }
    dibujarAnimales(ANIMALES_LEJOS, lejana, min(h, ref * 2f) * 0.050f, t, tarde, colorLejana, 0.45f)

    val media = Colina(h * 0.80f, min(h, ref * 2f) * 0.035f, 1.4f, 0.4f, ref)
    val colorMedia = lerp(Color(0xFFA5D6A7), Color(0xFF7A4F55), tarde)
    colina(media, colorMedia)
    val tonoMedio = TonoArbol(
        copa = lerp(Color(0xFF62AB6D), Color(0xFF5C3645), tarde), pino = lerp(Color(0xFF4C915E), Color(0xFF4E2E3C), tarde),
        arbusto = lerp(Color(0xFF6DB477), Color(0xFF633B4B), tarde), tronco = lerp(Color(0xFF7A5A43), Color(0xFF3F2733), tarde)
    )
    repetirAlAncho(ref) { desde -> for (a in ARBOLES_MEDIO) dibujarArbol(a, desde + a.x * ref, media, min(h, ref * 2f) * 0.080f, tonoMedio, t, luz, tarde) }
    dibujarAnimales(ANIMALES_MEDIO, media, min(h, ref * 2f) * 0.080f, t, tarde, colorMedia, 0.12f)

    val cercana = Colina(h * 0.88f, min(h, ref * 2f) * 0.030f, 2.2f, 1.9f, ref)
    val colorCercana = lerp(Color(0xFF81C784), Color(0xFF4E3440), tarde)
    colina(cercana, colorCercana)
    val tonoCerca = TonoArbol(
        copa = lerp(Color(0xFF3F8F4E), Color(0xFF462A37), tarde), pino = lerp(Color(0xFF2F7745), Color(0xFF3A2230), tarde),
        arbusto = lerp(Color(0xFF55A15E), Color(0xFF4F2F3D), tarde), tronco = lerp(Color(0xFF6B4B36), Color(0xFF33202A), tarde)
    )
    repetirAlAncho(ref) { desde -> for (a in ARBOLES_CERCA) dibujarArbol(a, desde + a.x * ref, cercana, min(h, ref * 2f) * 0.105f, tonoCerca, t, luz, tarde) }
    dibujarAnimales(ANIMALES_CERCA, cercana, min(h, ref * 2f) * 0.105f, t, tarde, colorCercana, 0f)
}

// ─────────────────────────────────────────────────────────────
//  Vegetación del fondo: árboles de copa, pinos, arbustos y pasto
// ─────────────────────────────────────────────────────────────
/** [periodo]: ancho en el que la colina hace sus ondulaciones (se repite a lo ancho). */
internal class Colina(val base: Float, val amplitud: Float, val frecuencia: Float, val fase: Float, val periodo: Float)

/** Llama [dibujar] con el inicio de cada tramo de ancho [ref] que hace falta para cubrir la pantalla. */
private inline fun DrawScope.repetirAlAncho(ref: Float, dibujar: (Float) -> Unit) {
    var desde = 0f
    while (desde < size.width) { dibujar(desde); desde += ref }
}

/** Altura de la colina en x (misma fórmula que colina()). */
internal fun DrawScope.sueloEn(c: Colina, x: Float) =
    c.base - c.amplitud * sin(x / c.periodo * PI.toFloat() * c.frecuencia + c.fase)

private enum class TipoArbol { COPA, PINO, ARBUSTO, PASTO }

/** x en fracción del ancho; escala relativa; fase para que no se mezan todos a la vez. */
private class Arbol(val x: Float, val tipo: TipoArbol, val escala: Float = 1f, val fase: Float = x * 9.1f)

private class TonoArbol(val copa: Color, val pino: Color, val arbusto: Color, val tronco: Color)

private val ARBOLES_LEJOS = listOf(
    Arbol(0.02f, TipoArbol.PINO, 0.9f), Arbol(0.06f, TipoArbol.COPA, 1.1f), Arbol(0.10f, TipoArbol.PINO, 1.2f),
    Arbol(0.20f, TipoArbol.PINO, 0.8f), Arbol(0.29f, TipoArbol.COPA, 0.9f), Arbol(0.33f, TipoArbol.PINO, 1.0f),
    Arbol(0.40f, TipoArbol.COPA, 1.0f), Arbol(0.45f, TipoArbol.PINO, 1.15f), Arbol(0.50f, TipoArbol.COPA, 0.85f),
    Arbol(0.55f, TipoArbol.PINO, 0.95f), Arbol(0.61f, TipoArbol.PINO, 1.1f), Arbol(0.65f, TipoArbol.COPA, 1.0f), Arbol(0.69f, TipoArbol.PINO, 0.8f),
    Arbol(0.87f, TipoArbol.COPA, 1.0f), Arbol(0.92f, TipoArbol.PINO, 1.2f), Arbol(0.97f, TipoArbol.COPA, 0.9f),
)

private val ARBOLES_MEDIO = listOf(
    Arbol(0.04f, TipoArbol.PINO, 1.1f), Arbol(0.11f, TipoArbol.COPA, 1.0f), Arbol(0.18f, TipoArbol.ARBUSTO, 0.9f),
    Arbol(0.26f, TipoArbol.PINO, 0.9f), Arbol(0.33f, TipoArbol.COPA, 1.05f), Arbol(0.40f, TipoArbol.COPA, 0.9f),
    Arbol(0.47f, TipoArbol.ARBUSTO, 1.0f), Arbol(0.51f, TipoArbol.COPA, 1.1f), Arbol(0.57f, TipoArbol.PINO, 1.0f),
    Arbol(0.65f, TipoArbol.ARBUSTO, 0.9f), Arbol(0.71f, TipoArbol.PINO, 1.05f), Arbol(0.79f, TipoArbol.COPA, 1.15f), Arbol(0.86f, TipoArbol.PINO, 0.95f), Arbol(0.95f, TipoArbol.ARBUSTO, 1.0f),
)

private val ARBOLES_CERCA = listOf(
    Arbol(0.02f, TipoArbol.COPA, 1.2f), Arbol(0.09f, TipoArbol.ARBUSTO, 1.0f), Arbol(0.15f, TipoArbol.PASTO),
    Arbol(0.21f, TipoArbol.ARBUSTO, 0.9f), Arbol(0.26f, TipoArbol.PASTO, 0.9f), Arbol(0.35f, TipoArbol.ARBUSTO, 0.8f),
    Arbol(0.42f, TipoArbol.COPA, 0.9f), Arbol(0.49f, TipoArbol.PASTO), Arbol(0.56f, TipoArbol.ARBUSTO, 1.0f),
    Arbol(0.63f, TipoArbol.ARBUSTO, 0.9f), Arbol(0.72f, TipoArbol.PASTO, 1.1f), Arbol(0.78f, TipoArbol.COPA, 1.0f),
    Arbol(0.84f, TipoArbol.ARBUSTO, 1.1f),
    Arbol(0.92f, TipoArbol.PINO, 1.1f), Arbol(0.98f, TipoArbol.PASTO, 0.9f),
)

private fun DrawScope.dibujarArbol(a: Arbol, x: Float, colina: Colina, altoBase: Float, tono: TonoArbol, t: Float, luz: Float, tarde: Float) {
    if (x < -altoBase || x > size.width + altoBase) return
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

private fun DrawScope.colina(c: Colina, color: Color) {
    val w = size.width
    val pasos = (40 * w / c.periodo).roundToInt().coerceAtLeast(40)
    val camino = Path().apply {
        moveTo(0f, size.height)
        for (i in 0..pasos) {
            val x = w * i / pasos
            lineTo(x, sueloEn(c, x))
        }
        lineTo(w, size.height)
        close()
    }
    drawPath(camino, color)
}

// ─────────────────────────────────────────────────────────────
//  Canvas: caja 3D + tarjetas de datos que se pueden arrastrar
// ─────────────────────────────────────────────────────────────
private fun Parametro.clave(): String? = when (this) {
    Parametro.Temperatura -> "temperatura"
    Parametro.HumedadAire -> "humedadAire"
    Parametro.Ventilador -> "ventilador"
    Parametro.Ventana -> "ventana"
    Parametro.Plantas -> null
    Parametro.GestionPlantas -> null
}

private enum class Lado { IZQ, DER, ARRIBA, ABAJO }

/** Una tarjeta de dato. [ladoPreferido]: por dónde entra la línea mientras la tarjeta está en su lugar automático. */
private class Tarjeta(
    val parametro: Parametro,
    val clave: String,
    val ancla: Offset,
    val valor: String,
    val titulo: String,
    val columnaIzquierda: Boolean,
    val ladoPreferido: Lado? = null
)

@Composable
private fun EscenaInvernadero(
    lectura: LecturaEsp32?,
    sinDato: String,                 // lo que muestran las tarjetas si aún no hay datos: "Cargando…" o "Error"
    plantas: List<Float?>,
    tiempo: State<Float>,
    aspas: State<Float>,
    velocidad: State<Float>,
    anguloVentana: State<Float>,
    paleta: Paleta,
    seleccionado: Parametro?,
    zonas: MutableList<ZonaToque>,
    posiciones: Map<String, Offset>,
    alMover: (String, Offset) -> Unit,
    alSoltar: () -> Unit,
    alTocar: (ZonaToque?) -> Unit,
    pista: State<Float>,
    alInteractuar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val medidor = rememberTextMeasurer()
    val alTocarActual by rememberUpdatedState(alTocar)
    val alMoverActual by rememberUpdatedState(alMover)
    val alSoltarActual by rememberUpdatedState(alSoltar)
    val alInteractuarActual by rememberUpdatedState(alInteractuar)
    // Tarjetas dibujadas en el último cuadro: clave → rectángulo (para arrastrarlas)
    val arrastrables = remember { mutableListOf<Pair<String, Rect>>() }
    var levantada by remember { mutableStateOf<String?>(null) }   // tarjeta que se está moviendo
    val vibracion = LocalHapticFeedback.current

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { },   // el toque mantenido es para mover, no abre el panel
                    onTap = { pos -> alInteractuarActual(); alTocarActual(zonas.lastOrNull { it.area.contains(pos) }) }
                )
            }
            .pointerInput(Unit) {
                var clave: String? = null
                var centro = Offset.Zero
                // Se mueve solo con toque mantenido (~½ s): un roce o un desliz no cambia nada de lugar
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        alInteractuarActual()
                        val toque = arrastrables.lastOrNull { it.second.inflate(6.dp.toPx()).contains(pos) }
                        clave = toque?.first
                        centro = toque?.second?.center ?: Offset.Zero
                        if (clave != null) {
                            levantada = clave
                            vibracion.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDragEnd = { if (clave != null) alSoltarActual(); clave = null; levantada = null },
                    onDragCancel = { if (clave != null) alSoltarActual(); clave = null; levantada = null },
                    onDrag = { cambio, desplazamiento ->
                        val k = clave
                        if (k != null) {
                            cambio.consume()
                            centro += desplazamiento
                            alMoverActual(
                                k,
                                Offset((centro.x / size.width).coerceIn(0f, 1f), (centro.y / size.height).coerceIn(0f, 1f))
                            )
                        }
                    }
                )
            }
    ) {
        // Sin datos la caja se dibuja igual (ventilador quieto, ventana cerrada) y las tarjetas dicen "Cargando…" o "Error"
        // La caja queda al centro y deja ver el paisaje alrededor (~2/3 del ancho útil);
        // apenas a la derecha porque la ventana abierta sobresale a la izquierda.
        // En horizontal la escena es baja y ancha: las tarjetas van a los costados
        val apaisado = size.width > size.height * 1.25f
        val margen = 36.dp.toPx()
        val escala = if (apaisado) min(size.width * 0.40f / 1.85f, size.height * 0.64f / 1.25f)
                     else min((size.width - 2 * margen) * 0.68f / 1.85f, size.height / 3.0f)
        val cam = Camara(YAW_GRADOS, PITCH_GRADOS, escala, Offset(size.width * 0.515f, size.height * 0.50f))

        val anclas = dibujarEscena(
            cam,
            DatosEscena(
                humedades = plantas,
                temperatura = lectura?.temperatura ?: 20f,
                velocidadVentilador = velocidad.value,
                anguloAspas = aspas.value,
                anguloVentana = anguloVentana.value,
                tiempo = tiempo.value
            ),
            paleta
        )

        // La humedad del aire se mide "en el aire" de la caja: el punto flota arriba, sobre las plantas
        val anclaAire = cam.proyectar(Vec3(0.22f, 0.30f, -0.18f))
        val l = lectura
        val tarjetas = listOfNotNull(
            Tarjeta(Parametro.Ventana, "ventana", anclas.ventana, l?.let { "${it.anguloVentana.roundToInt()}°" } ?: sinDato,
                l?.let { "Ventana · ${it.modoVentana.lowercase()}" } ?: "Ventana", columnaIzquierda = true, ladoPreferido = Lado.IZQ),
            Tarjeta(Parametro.Temperatura, "temperatura", anclas.temperatura, l?.let { "%.1f °C".format(it.temperatura) } ?: sinDato,
                "Temperatura", columnaIzquierda = true),
            Tarjeta(Parametro.Ventilador, "ventilador", anclas.ventilador,
                l?.let { if (it.ventiladorEncendido) "${(it.velocidadVentilador * 100).roundToInt()}%" else "OFF" } ?: sinDato,
                l?.let { "Ventilador · ${it.modoVentilador.lowercase()}" } ?: "Ventilador", columnaIzquierda = false, ladoPreferido = Lado.DER),
            if (l == null) {
                Tarjeta(Parametro.HumedadAire, "humedadAire", anclaAire, sinDato, "Humedad del aire", columnaIzquierda = false)
            } else l.humedadAmbiente?.let {
                Tarjeta(Parametro.HumedadAire, "humedadAire", anclaAire, "${it.roundToInt()}%", "Humedad del aire", columnaIzquierda = false)
            },
        )
        zonas.clear()
        arrastrables.clear()
        val (insignia, botonMas) = dibujarInsigniaPlantas(
            plantas.size, medidor, paleta, seleccionado == Parametro.Plantas, seleccionado == Parametro.GestionPlantas
        )
        zonas += ZonaToque(Parametro.Plantas, insignia.inflate(4.dp.toPx()))
        zonas += ZonaToque(Parametro.GestionPlantas, botonMas.inflate(4.dp.toPx()))
        val bloque = Rect(insignia.left, min(insignia.top, botonMas.top), botonMas.right, max(insignia.bottom, botonMas.bottom))
        dibujarTarjetas(tarjetas, posiciones, medidor, paleta, tiempo.value, seleccionado, zonas, arrastrables, anclas.techo, bloque, apaisado, levantada, pista.value)
    }
}

/**
 * Tarjetas + líneas. Cada tarjeta está donde la dejó el usuario (o en su lugar automático, apiladas
 * sobre el techo: ventana y temperatura a la izquierda, ventilador y humedad a la derecha).
 * Cada línea busca el camino más corto que rodea las demás tarjetas (y la propia, para entrar por su lado).
 */
private fun DrawScope.dibujarTarjetas(
    lista: List<Tarjeta>,
    posiciones: Map<String, Offset>,
    medidor: TextMeasurer,
    p: Paleta,
    t: Float,
    seleccionado: Parametro?,
    zonas: MutableList<ZonaToque>,
    arrastrables: MutableList<Pair<String, Rect>>,
    techo: Float,
    insignia: Rect,
    apaisado: Boolean,
    levantada: String?,
    pista: Float
) {
    val estiloValor = TextStyle(color = p.verde, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = FuenteTitulos, fontFeatureSettings = "tnum")
    val estiloTitulo = TextStyle(color = p.texto, fontSize = 11.sp, fontFamily = FuenteTexto, letterSpacing = 0.2.sp)
    val relleno = 9.dp.toPx()
    val borde = 6.dp.toPx()
    val holgura = 9.dp.toPx()          // cuánto se aparta una línea de las tarjetas que rodea
    val pasillo = holgura + 14.dp.toPx() // espacio junto al borde para que entre la línea por fuera
    val separacion = 58.dp.toPx()
    val grosor = 1.6.dp.toPx()

    val textos = lista.map { medidor.measure(it.valor, estiloValor) to medidor.measure(it.titulo, estiloTitulo) }
    val tamanos = textos.map { (v, ti) -> Size(max(v.size.width, ti.size.width) + 2 * relleno, v.size.height + ti.size.height + relleno * 1.5f) }

    // 1. Lugar automático: columnas a cada lado, la primera de cada columna más abajo
    val automatico = arrayOfNulls<Offset>(lista.size)
    for (izquierda in listOf(true, false)) {
        val columna = lista.indices.filter { lista[it].columnaIzquierda == izquierda }
        // Vertical: apiladas sobre el techo. Horizontal: centradas a la altura de la caja, junto a los bordes.
        var y = if (apaisado) size.height / 2f + (columna.size - 1) * separacion / 2f + tamanos.maxOf { it.height } / 2f
                else techo - 34.dp.toPx()
        val ys = columna.map { i -> (y - tamanos[i].height / 2f).also { y -= separacion } }
        val falta = borde - ((ys.lastOrNull() ?: borde) - (columna.lastOrNull()?.let { tamanos[it].height / 2f } ?: 0f))
        columna.forEachIndexed { k, i ->
            val tam = tamanos[i]
            val porFuera = lista[i].ladoPreferido == (if (izquierda) Lado.IZQ else Lado.DER)
            val x = if (izquierda) borde + (if (porFuera) pasillo else 0f) + tam.width / 2f
                    else size.width - borde - (if (porFuera) pasillo else 0f) - tam.width / 2f
            automatico[i] = Offset(x, ys[k] + max(0f, falta))
        }
    }

    // 2. Rectángulos finales (dentro de la escena)
    val rectsBase = lista.mapIndexed { i, e ->
        val tam = tamanos[i]
        val c = posiciones[e.clave]?.let { Offset(it.x * size.width, it.y * size.height) } ?: automatico[i]!!
        val cx = c.x.coerceIn(borde + tam.width / 2f, max(borde + tam.width / 2f, size.width - borde - tam.width / 2f))
        val cy = c.y.coerceIn(borde + tam.height / 2f, max(borde + tam.height / 2f, size.height - borde - tam.height / 2f))
        Rect(Offset(cx - tam.width / 2f, cy - tam.height / 2f), tam)
    }

    // Pista: una tarjeta muestra el gesto (se mantiene presionada → se levanta y se desplaza un poco, ida y vuelta)
    val objetivo = if (pista > 0.01f && lista.isNotEmpty()) lista.indexOfFirst { it.clave == "temperatura" }.coerceAtLeast(0) else -1
    val ciclo = (t % 2.6f) / 2.6f
    val presion = (ciclo / 0.4f).coerceIn(0f, 1f)
    val arrastre = sin(((ciclo - 0.42f) / 0.4f).coerceIn(0f, 1f) * PI.toFloat())
    val rects = rectsBase.mapIndexed { i, r ->
        if (i != objetivo) r
        else r.translate(Offset((if (r.center.x > size.width / 2f) -1f else 1f) * 22.dp.toPx(), -6.dp.toPx()) * (arrastre * pista))
    }
    val limites = Rect(0f, 0f, size.width, size.height)

    // 3. Líneas (todas primero, así ninguna queda encima de una tarjeta)
    lista.forEachIndexed { i, e ->
        val r = rects[i]
        val movida = posiciones.containsKey(e.clave)
        val lado = (if (movida) null else e.ladoPreferido) ?: ladoHacia(r, e.ancla)
        val (entrada, normal) = when (lado) {
            Lado.IZQ -> r.centerLeft to Offset(-1f, 0f)
            Lado.DER -> r.centerRight to Offset(1f, 0f)
            Lado.ARRIBA -> r.topCenter to Offset(0f, -1f)
            Lado.ABAJO -> r.bottomCenter to Offset(0f, 1f)
        }
        val acercamiento = entrada + normal * (holgura + 5.dp.toPx())
        val obstaculos = (rects.map { it.inflate(holgura) } + insignia.inflate(holgura))
            .filterNot { it.contains(e.ancla) || it.contains(acercamiento) }
        val camino = rutaLibre(e.ancla, acercamiento, obstaculos, limites) + entrada
        val trazo = Path().apply {
            moveTo(camino[0].x, camino[0].y)
            for (k in 1 until camino.size) lineTo(camino[k].x, camino[k].y)
        }
        drawPath(trazo, p.linea, style = Stroke(grosor, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // 4. Tarjetas y puntos que laten
    lista.forEachIndexed { i, e ->
        val r = rects[i]
        val (valor, titulo) = textos[i]
        val elegido = e.parametro == seleccionado
        val enAlto = e.clave == levantada || (i == objetivo && arrastre > 0.05f)
        if (enAlto) {
            // Levantada: sombra suave debajo y borde, para que se note que se está moviendo
            drawRoundRect(Color.Black.copy(alpha = 0.18f), r.topLeft + Offset(0f, 4.dp.toPx()), r.size, CornerRadius(14.dp.toPx()))
        }
        drawRoundRect(p.tarjeta, r.topLeft, r.size, CornerRadius(14.dp.toPx()))
        if (enAlto) drawRoundRect(p.linea, r.topLeft, r.size, CornerRadius(14.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        if (elegido) drawRoundRect(p.verde, r.topLeft, r.size, CornerRadius(14.dp.toPx()), style = Stroke(2.dp.toPx()))
        drawText(valor, topLeft = Offset(r.left + relleno, r.top + relleno * 0.75f))
        drawText(titulo, topLeft = Offset(r.left + relleno, r.top + relleno * 0.75f + valor.size.height))
        dibujarLatido(e.ancla, t + i * 0.37f, p, elegido)

        zonas += ZonaToque(e.parametro, r.inflate(6.dp.toPx()))
        zonas += ZonaToque(e.parametro, Rect(e.ancla, 22.dp.toPx()))
        arrastrables += e.clave to r
    }
    if (objetivo >= 0) dibujarPista(rects[objetivo], rectsBase[objetivo], medidor, p, presion, arrastre, pista)
}

/**
 * Pista del gesto para mover tarjetas: un "dedo" sobre la tarjeta con un anillo que se llena
 * (toque mantenido) y un globo con el texto. [dedo]: tarjeta animada; [globo]: dónde está en reposo.
 */
private fun DrawScope.dibujarPista(dedo: Rect, globo: Rect, medidor: TextMeasurer, p: Paleta, presion: Float, arrastre: Float, alfa: Float) {
    val radio = 16.dp.toPx()
    val c = dedo.center
    drawCircle(Color.White.copy(alpha = 0.45f * alfa), radio * (1f - 0.15f * presion), c)
    drawCircle(p.linea.copy(alpha = 0.35f * alfa), radio, c, style = Stroke(2.dp.toPx()))
    if (arrastre < 0.05f) {
        drawArc(
            p.verde.copy(alpha = alfa), -90f, 360f * presion, false,
            Offset(c.x - radio, c.y - radio), Size(radio * 2f, radio * 2f),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
        )
    }

    val relleno = 12.dp.toPx()
    val flecha = 7.dp.toPx()
    val anchoMax = min(size.width - 24.dp.toPx(), 230.dp.toPx())
    val estilo = TextStyle(color = p.textoBoton, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FuenteTexto)
    val texto = medidor.measure(
        "Mantén presionada una tarjeta para moverla", estilo,
        constraints = Constraints(maxWidth = (anchoMax - 2f * relleno).roundToInt().coerceAtLeast(1))
    )
    val tam = Size(texto.size.width + relleno * 2f, texto.size.height + relleno * 1.4f)
    val debajo = globo.bottom + flecha + 6.dp.toPx() + tam.height < size.height - 64.dp.toPx()   // libre la hoja de plantas
    val x = (globo.center.x - tam.width / 2f).coerceIn(8.dp.toPx(), max(8.dp.toPx(), size.width - 8.dp.toPx() - tam.width))
    val y = if (debajo) globo.bottom + flecha + 6.dp.toPx() else globo.top - flecha - 6.dp.toPx() - tam.height
    val fondo = p.botonActivo.copy(alpha = 0.95f * alfa)
    drawRoundRect(fondo, Offset(x, y), tam, CornerRadius(12.dp.toPx()))
    val px = globo.center.x.coerceIn(x + 16.dp.toPx(), x + tam.width - 16.dp.toPx())
    val pico = Path().apply {
        if (debajo) { moveTo(px - flecha, y); lineTo(px, y - flecha); lineTo(px + flecha, y) }
        else { moveTo(px - flecha, y + tam.height); lineTo(px, y + tam.height + flecha); lineTo(px + flecha, y + tam.height) }
        close()
    }
    drawPath(pico, fondo)
    drawText(texto, topLeft = Offset(x + relleno, y + relleno * 0.7f), alpha = alfa)
}

/** Lado de la tarjeta que mira hacia el objeto. */
private fun ladoHacia(r: Rect, a: Offset): Lado {
    val dx = (a.x - r.center.x) / (r.width / 2f).coerceAtLeast(1f)
    val dy = (a.y - r.center.y) / (r.height / 2f).coerceAtLeast(1f)
    return if (abs(dx) > abs(dy)) (if (dx < 0f) Lado.IZQ else Lado.DER) else (if (dy < 0f) Lado.ARRIBA else Lado.ABAJO)
}

/**
 * Camino más corto de [desde] a [hasta] que no atraviesa ningún obstáculo: grafo de visibilidad con
 * las esquinas de los obstáculos (ya agrandados con su holgura) y Dijkstra. Sin camino → línea recta.
 */
private fun rutaLibre(desde: Offset, hasta: Offset, obstaculos: List<Rect>, limites: Rect): List<Offset> {
    val nodos = ArrayList<Offset>(2 + obstaculos.size * 4).apply {
        add(desde); add(hasta)
        for (o in obstaculos) { add(o.topLeft); add(o.topRight); add(o.bottomLeft); add(o.bottomRight) }
    }
    val n = nodos.size
    val usable = BooleanArray(n) { i ->
        i < 2 || (limites.contains(nodos[i]) && obstaculos.none { it.deflate(0.5f).contains(nodos[i]) })
    }
    val dist = FloatArray(n) { Float.MAX_VALUE }
    val previo = IntArray(n) { -1 }
    val listo = BooleanArray(n)
    dist[0] = 0f
    while (true) {
        var u = -1
        for (i in 0 until n) if (usable[i] && !listo[i] && dist[i] < Float.MAX_VALUE && (u == -1 || dist[i] < dist[u])) u = i
        if (u == -1 || u == 1) break
        listo[u] = true
        for (v in 0 until n) {
            if (!usable[v] || listo[v] || v == u) continue
            if (obstaculos.any { cruza(nodos[u], nodos[v], it) }) continue
            val d = dist[u] + (nodos[v] - nodos[u]).getDistance()
            if (d < dist[v]) { dist[v] = d; previo[v] = u }
        }
    }
    if (dist[1] == Float.MAX_VALUE) return listOf(desde, hasta)
    val camino = ArrayList<Offset>()
    var k = 1
    while (k != -1) { camino.add(nodos[k]); k = previo[k] }
    return camino.asReversed()
}

/** ¿El segmento a–b pasa por DENTRO del rectángulo? (tocar el borde no cuenta). Liang–Barsky. */
private fun cruza(a: Offset, b: Offset, r: Rect): Boolean {
    val s = r.deflate(0.5f)
    if (s.width <= 0f || s.height <= 0f) return false
    val dx = b.x - a.x
    val dy = b.y - a.y
    val p = floatArrayOf(-dx, dx, -dy, dy)
    val q = floatArrayOf(a.x - s.left, s.right - a.x, a.y - s.top, s.bottom - a.y)
    var t0 = 0f
    var t1 = 1f
    for (i in 0..3) {
        if (p[i] == 0f) {
            if (q[i] < 0f) return false
        } else {
            val tt = q[i] / p[i]
            if (p[i] < 0f) { if (tt > t1) return false; if (tt > t0) t0 = tt }
            else { if (tt < t0) return false; if (tt < t1) t1 = tt }
        }
    }
    return t0 < t1
}

/** Esquina inferior izquierda: hoja + cantidad de plantas, y al lado el botón "+". Devuelve ambos rectángulos. */
private fun DrawScope.dibujarInsigniaPlantas(
    cantidad: Int, medidor: TextMeasurer, p: Paleta, elegido: Boolean, masElegido: Boolean
): Pair<Rect, Rect> {
    val estilo = TextStyle(color = p.texto, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FuenteTitulos, fontFeatureSettings = "tnum")
    val numero = medidor.measure("$cantidad", estilo)
    val icono = 22.dp.toPx()
    val relleno = 10.dp.toPx()
    val alto = max(icono, numero.size.height.toFloat()) + relleno * 1.4f
    val ancho = relleno + icono + 6.dp.toPx() + numero.size.width + relleno * 1.2f
    val r = Rect(Offset(6.dp.toPx(), size.height - 6.dp.toPx() - alto), Size(ancho, alto))
    drawRoundRect(p.tarjeta, r.topLeft, r.size, CornerRadius(alto / 2f))
    if (elegido) drawRoundRect(p.verde, r.topLeft, r.size, CornerRadius(alto / 2f), style = Stroke(2.dp.toPx()))
    dibujarHoja(Offset(r.left + relleno, r.center.y - icono / 2f), icono, p.verde, p.tarjeta)
    drawText(numero, topLeft = Offset(r.left + relleno + icono + 6.dp.toPx(), r.center.y - numero.size.height / 2f))

    // Botón "+": añadir o quitar plantas
    val mas = Rect(Offset(r.right + 8.dp.toPx(), r.top), Size(alto, alto))
    drawCircle(p.botonActivo, alto / 2f, mas.center)
    if (masElegido) drawCircle(p.verde, alto / 2f, mas.center, style = Stroke(2.dp.toPx()))
    val brazo = alto * 0.22f
    drawLine(p.textoBoton, mas.center - Offset(brazo, 0f), mas.center + Offset(brazo, 0f), 2.5.dp.toPx(), StrokeCap.Round)
    drawLine(p.textoBoton, mas.center - Offset(0f, brazo), mas.center + Offset(0f, brazo), 2.5.dp.toPx(), StrokeCap.Round)
    return r to mas
}

/** Hoja dibujada a mano: lámina curva, nervio central y tallo. */
private fun DrawScope.dibujarHoja(o: Offset, s: Float, color: Color, nervio: Color) {
    val base = Offset(o.x + s * 0.18f, o.y + s * 0.82f)
    val punta = Offset(o.x + s * 0.92f, o.y + s * 0.08f)
    val hoja = Path().apply {
        moveTo(base.x, base.y)
        cubicTo(o.x + s * 0.08f, o.y + s * 0.38f, o.x + s * 0.46f, o.y + s * 0.06f, punta.x, punta.y)
        cubicTo(o.x + s * 0.94f, o.y + s * 0.54f, o.x + s * 0.62f, o.y + s * 0.92f, base.x, base.y)
        close()
    }
    drawPath(hoja, color)
    drawLine(nervio, lerpOffset(base, punta, 0.08f), lerpOffset(base, punta, 0.82f), s * 0.06f, StrokeCap.Round)
    drawLine(color, base, Offset(o.x + s * 0.02f, o.y + s * 0.98f), s * 0.09f, StrokeCap.Round)
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
    plantas: List<PlantaVisible>,
    quitadas: Set<Int>,
    puedeActivar: Boolean,
    puedeRenombrar: Boolean,
    nombres: List<String>,
    c: Contenedor,
    alCerrar: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var aviso by remember(parametro) { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    val habilitado = puedeActivar && !enviando

    // Lo pedido se ve al instante (sin volver al valor anterior mientras el ESP32 confirma)
    val pedidoEncendido = rememberPedido<Boolean>()
    val pedidoPwm = rememberPedido<Float>()
    val pedidoApertura = rememberPedido<Float>()

    fun enviar(vararg codigos: String, siFalla: () -> Unit = {}) {
        if (enviando) return
        scope.launch {
            enviando = true
            aviso = "Enviando…"
            var ok = true
            for (cod in codigos) if (!c.conexion.enviar(cod)) { ok = false; break }
            enviando = false
            if (!ok) siFalla()
            aviso = if (ok) "Enviado: " + codigos.joinToString(" + ") { Comandos.descripcion(it) } else "No se pudo enviar. Revisa la conexión."
        }
    }
    fun auditar(detalle: String) {
        val quien = c.sesion.usuario.value?.nombre ?: "?"
        scope.launch(Dispatchers.IO) { c.db.auditar(quien, "AJUSTES", detalle, "OK") }
    }
    fun pedirPwm(objetivo: Float, codigo: String) {
        pedidoPwm.valor = objetivo.coerceIn(0f, 255f)
        pedidoEncendido.valor = objetivo > 0f
        enviar(codigo) { pedidoPwm.valor = null; pedidoEncendido.valor = null }
    }
    fun pedirApertura(objetivo: Float, codigo: String) {
        pedidoApertura.valor = objetivo
        enviar(codigo) { pedidoApertura.valor = null }
    }

    val titulo = when (parametro) {
        Parametro.Plantas -> "Plantas (${plantas.size})"
        Parametro.GestionPlantas -> "Añadir o quitar plantas"
        Parametro.Temperatura -> "Temperatura"
        Parametro.HumedadAire -> "Humedad del aire"
        Parametro.Ventilador -> "Ventilador"
        Parametro.Ventana -> "Ventana"
    }
    val verde = MaterialTheme.colorScheme.secondary

    // Un poco translúcido: se alcanza a ver la escena detrás
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = alCerrar) { Icon(Icons.Filled.Close, contentDescription = "Cerrar") }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (parametro) {
                    Parametro.GestionPlantas -> GestionPlantas(
                        plantas, quitadas, l.humedades.size, nombres, puedeRenombrar,
                        alAnadir = {
                            if (c.plantas.anadir(plantas.size)) {
                                aviso = "Planta añadida. Conecta su sensor al ESP32 y aparecerá sola."
                                auditar("Planta añadida (espera su sensor)")
                            } else aviso = "Máximo ${PlantasRepo.MAX_PLANTAS} plantas."
                        },
                        alQuitar = { pl ->
                            val nombre = nombrePlanta(nombres, pl.indice)
                            if (pl.humedad == null) c.plantas.quitarEsperando(pl.indice, l.humedades.size) else c.plantas.quitar(pl.indice)
                            aviso = "Quitaste \"$nombre\"."
                            auditar("Planta quitada: \"$nombre\"")
                        },
                        alRestaurar = { i ->
                            c.plantas.restaurar(i)
                            aviso = null
                            auditar("Planta vuelve a mostrarse: \"${nombrePlanta(nombres, i)}\"")
                        }
                    )

                    Parametro.Plantas -> ListaPlantas(plantas, nombres, puedeRenombrar) { i, nombre ->
                        val anterior = nombrePlanta(nombres, i)
                        val nuevo = c.plantas.renombrar(i, nombre)
                        val quien = c.sesion.usuario.value?.nombre ?: "?"
                        scope.launch(Dispatchers.IO) {
                            c.db.auditar(quien, "AJUSTES", "Planta ${i + 1}: \"$anterior\" → \"${nuevo.ifBlank { "Planta ${i + 1}" }}\"", "OK")
                        }
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
                            FilledTonalButton(onClick = {
                                pedidoPwm.valor = 255f; pedidoEncendido.valor = true; pedidoApertura.valor = 90f
                                enviar(Comandos.MAXIMO, Comandos.ABRIR) { pedidoPwm.valor = null; pedidoEncendido.valor = null; pedidoApertura.valor = null }
                            }, enabled = habilitado) { Text("Ventilar") }
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
                        val confirmado = pedidoPwm.mostrar(l.velocidadVentilador * 255f) { a, b -> abs(a - b) <= 3f }
                        val pwm = arrastre ?: confirmado
                        val encendido = pedidoEncendido.mostrar(l.ventiladorEncendido)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (encendido) "Encendido" else "Apagado", style = MaterialTheme.typography.bodyLarge)
                                Text("Modo ${l.modoVentilador.lowercase()}", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = encendido,
                                onCheckedChange = { pedirPwm(if (it) 255f else 0f, if (it) Comandos.MAXIMO else Comandos.APAGAR) },
                                enabled = habilitado
                            )
                        }
                        Text("Velocidad: ${(pwm / 2.55f).roundToInt()}%", color = verde)
                        Slider(
                            value = pwm,
                            onValueChange = { arrastre = it },
                            onValueChangeFinished = {
                                arrastre?.let { pedirPwm(it, Comandos.velocidad(it.roundToInt())) }
                                arrastre = null
                            },
                            valueRange = 0f..255f,
                            enabled = habilitado
                        )
                        TextButton(onClick = { enviar(Comandos.AUTOMATICO) }, enabled = habilitado) { Text("Volver a automático") }
                    }

                    Parametro.Ventana -> {
                        var arrastre by remember { mutableStateOf<Float?>(null) }
                        val confirmada = pedidoApertura.mostrar(l.anguloVentana) { a, b -> abs(a - b) <= 2f }
                        val apertura = arrastre ?: confirmada
                        Text("Apertura: ${apertura.roundToInt()}° de 90°", color = verde, style = MaterialTheme.typography.titleMedium)
                        Text("Modo ${l.modoVentana.lowercase()}", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = apertura,
                            onValueChange = { arrastre = it },
                            onValueChangeFinished = {
                                arrastre?.let { pedirApertura(it, Comandos.ventana(it.roundToInt())) }
                                arrastre = null
                            },
                            valueRange = 0f..90f,
                            steps = 8,
                            enabled = habilitado
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { pedirApertura(0f, Comandos.CERRAR) }, enabled = habilitado) { Text("Cerrar") }
                            Button(onClick = { pedirApertura(90f, Comandos.ABRIR) }, enabled = habilitado) { Text("Abrir") }
                        }
                        TextButton(onClick = { enviar(Comandos.AUTOMATICO) }, enabled = habilitado) { Text("Volver a automático") }
                    }
                }
                if (!puedeActivar && parametro != Parametro.Plantas && parametro != Parametro.GestionPlantas && parametro != Parametro.HumedadAire) {
                    Text("Tu rol solo permite ver los datos.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                aviso?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

/** Cada planta (un sensor de suelo): su nombre, humedad y estado; se puede renombrar. */
@Composable
private fun ListaPlantas(plantas: List<PlantaVisible>, nombres: List<String>, puedeRenombrar: Boolean, alRenombrar: (Int, String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var editando by remember { mutableStateOf<Int?>(null) }
    var texto by remember { mutableStateOf("") }
    Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        plantas.forEach { pl ->
            val i = pl.indice
            val h = pl.humedad
            if (editando == i) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.take(PlantasRepo.MAX_LARGO) },
                    label = { Text("Nombre de la planta ${i + 1}") },
                    placeholder = { Text("Ej.: Tomate cherry") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { alRenombrar(i, texto); editando = null }),
                    trailingIcon = {
                        IconButton(onClick = { alRenombrar(i, texto); editando = null }) {
                            Icon(Icons.Filled.Check, contentDescription = "Guardar nombre")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(nombrePlanta(nombres, i), style = MaterialTheme.typography.titleSmall)
                        Text(if (h == null) "Esperando sensor" else pl.estado.orEmpty(), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    if (h != null) Text("${h.roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = cs.secondary)
                    if (puedeRenombrar) {
                        IconButton(onClick = { texto = nombres.getOrNull(i).orEmpty(); editando = i }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Renombrar ${nombrePlanta(nombres, i)}")
                        }
                    }
                }
                if (h != null) LinearProgressIndicator(progress = { h / 100f }, modifier = Modifier.fillMaxWidth(), color = cs.secondary)
            }
        }
        Text(
            "Humedad de la tierra de cada sensor. Solo se monitorea: el ESP32 no tiene bomba de riego. " +
                "El nombre se guarda en este teléfono y aparece también en el Historial.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
    }
}

/** Planta que se muestra. [indice]: su sensor (o el que tendrá); [humedad] null = espera su sensor. */
class PlantaVisible(val indice: Int, val humedad: Float?, val estado: String?)

/** Sensores que no se quitaron, en orden, y después las plantas añadidas que esperan sensor. */
private fun plantasVisibles(l: LecturaEsp32, quitadas: Set<Int>, esperando: Int): List<PlantaVisible> =
    l.humedades.indices.filter { it !in quitadas }.map { PlantaVisible(it, l.humedades[it], l.estadosSuelo.getOrNull(it)) } +
        List(esperando) { k -> PlantaVisible(l.humedades.size + k, null, null) }

/** Panel del botón "+": lista de plantas con su estado, quitar, volver a mostrar y añadir. */
@Composable
private fun GestionPlantas(
    plantas: List<PlantaVisible>,
    quitadas: Set<Int>,
    sensores: Int,
    nombres: List<String>,
    puedeEditar: Boolean,
    alAnadir: () -> Unit,
    alQuitar: (PlantaVisible) -> Unit,
    alRestaurar: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Cada planta lleva un sensor de humedad de suelo conectado al ESP32. Añade una planta y conecta " +
                "su sensor: aparecerá sola cuando el ESP32 lo detecte.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
        plantas.forEach { pl ->
            val nombre = nombrePlanta(nombres, pl.indice)
            val h = pl.humedad
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(nombre, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (h == null) "Esperando sensor" else "Conectada · ${h.roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (h == null) cs.tertiary else cs.onSurfaceVariant
                    )
                }
                if (puedeEditar) {
                    IconButton(onClick = { alQuitar(pl) }) { Icon(Icons.Filled.Delete, contentDescription = "Quitar $nombre") }
                }
            }
        }
        val ocultas = quitadas.filter { it < sensores }.sorted()
        if (ocultas.isNotEmpty()) {
            Text(
                "Quitadas (su sensor sigue conectado)",
                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
            )
            ocultas.forEach { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(nombrePlanta(nombres, i), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (puedeEditar) TextButton(onClick = { alRestaurar(i) }) { Text("Volver a mostrar") }
                }
            }
        }
        if (puedeEditar) {
            Button(
                onClick = alAnadir,
                enabled = plantas.size < PlantasRepo.MAX_PLANTAS,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Añadir planta")
            }
        } else {
            Text("Las cuentas de invitado no pueden cambiar las plantas.", color = cs.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
