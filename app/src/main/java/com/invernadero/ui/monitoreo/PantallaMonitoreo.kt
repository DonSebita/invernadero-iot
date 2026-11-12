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
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.geometry.lerp as lerpOffset

/** Qué parámetro de la escena se tocó. */
sealed interface Parametro {
    data class Planta(val indice: Int) : Parametro
    data object Temperatura : Parametro
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.tarjeta).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Monitoreo", color = p.texto, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        BotonModo("☀ Día", !modoTarde, p) { alCambiar(false) }
        Spacer(Modifier.width(8.dp))
        BotonModo("🌇 Tarde", modoTarde, p) { alCambiar(true) }
    }
}

@Composable
private fun BotonModo(texto: String, activo: Boolean, p: Paleta, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (activo) p.botonActivo else Color.Transparent,
            contentColor = if (activo) p.textoBoton else p.texto
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) { Text(texto, fontSize = 13.sp) }
}

@Composable
private fun BarraEstado(lectura: LecturaEsp32?, e: EstadoConexion, p: Paleta) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.tarjeta).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (e.conectado) p.verde else Color(0xFFE53935)))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(e.descripcion, color = p.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                e.mensaje ?: if (e.compartiendo) "Compartiendo con ${e.telefonosConectados} teléfono(s)" else "Toca un dato para modificarlo",
                color = p.texto, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (lectura != null) {
            Text("${lectura.humedades.size} sensores", color = p.verde, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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

    // Colinas (dos capas)
    colina(h * 0.80f, h * 0.035f, 1.4f, 0.4f, lerp(Color(0xFFA5D6A7), Color(0xFF7A4F55), tarde))
    colina(h * 0.88f, h * 0.030f, 2.2f, 1.9f, lerp(Color(0xFF81C784), Color(0xFF4E3440), tarde))
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
            val txt = medidor.measure("Esperando datos…", TextStyle(color = paleta.texto, fontSize = 16.sp))
            drawText(txt, topLeft = center - Offset(txt.size.width / 2f, txt.size.height / 2f))
            return@Canvas
        }
        // La caja ocupa la mayor parte del ancho; las etiquetas flotan encima con su propio fondo
        val margen = 36.dp.toPx()
        val escala = min((size.width - 2 * margen) / 1.85f, size.height / 1.5f)
        // un poco a la derecha (la ventana abierta sobresale a la izquierda) y abajo, sobre las colinas
        val cam = Camara(YAW_GRADOS, PITCH_GRADOS, escala, center + Offset(size.width * 0.04f, size.height * 0.08f))

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

        val etiquetas = buildList {
            anclas.plantas.forEachIndexed { i, a ->
                val estado = lectura.estadosSuelo.getOrNull(i)
                add(Etiqueta(a, "${lectura.humedades[i].roundToInt()}%", estado ?: "Humedad suelo ${i + 1}", Parametro.Planta(i)))
            }
            val aire = lectura.humedadAmbiente?.let { " · aire ${it.roundToInt()}%" } ?: ""
            add(Etiqueta(anclas.temperatura, "%.1f °C".format(lectura.temperatura), "Temperatura$aire", Parametro.Temperatura))
            add(Etiqueta(
                anclas.ventilador,
                if (lectura.ventiladorEncendido) "${(lectura.velocidadVentilador * 100).roundToInt()}%" else "OFF",
                "Ventilador · ${lectura.modoVentilador.lowercase()}",
                Parametro.Ventilador
            ))
            add(Etiqueta(anclas.ventana, "${lectura.anguloVentana.roundToInt()}°", "Ventana · ${lectura.modoVentana.lowercase()}", Parametro.Ventana))
        }
        zonas.clear()
        dibujarEtiquetas(etiquetas, medidor, paleta, tiempo.value, seleccionado, zonas, anclas.techo)
    }
}

private data class Etiqueta(val ancla: Offset, val valor: String, val titulo: String, val parametro: Parametro)

/** Círculo sobre el objeto → línea diagonal → línea horizontal → dato con fondo (estilo Paint). */
private fun DrawScope.dibujarEtiquetas(
    lista: List<Etiqueta>,
    medidor: TextMeasurer,
    p: Paleta,
    t: Float,
    seleccionado: Parametro?,
    zonas: MutableList<ZonaToque>,
    techo: Float
) {
    val estiloValor = TextStyle(color = p.verde, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    val estiloTitulo = TextStyle(color = p.texto, fontSize = 11.sp)
    val margen = 6.dp.toPx()
    val separacion = 52.dp.toPx()
    val grosor = 1.6.dp.toPx()
    val relleno = 6.dp.toPx()

    lista.withIndex()
        .groupBy { it.value.ancla.x < size.width / 2f }
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
                val xFin = if (izquierda) margen + relleno + anchoTexto + hueco else size.width - margen - relleno - anchoTexto - hueco
                val dy = abs(e.ancla.y - y)
                val codoX = if (izquierda) max(e.ancla.x - dy * 0.6f, xFin + 8.dp.toPx())
                            else min(e.ancla.x + dy * 0.6f, xFin - 8.dp.toPx())
                val codo = Offset(codoX, y)

                drawLine(p.linea, e.ancla, codo, grosor, StrokeCap.Round)
                drawLine(p.linea, codo, Offset(xFin, y), grosor, StrokeCap.Round)

                // Fondo del dato (para leerlo aunque quede sobre la caja)
                val xValor = if (izquierda) xFin - hueco - valor.size.width else xFin + hueco
                val xTitulo = if (izquierda) xFin - hueco - titulo.size.width else xFin + hueco
                val izq = min(xValor, xTitulo) - relleno
                val der = max(xValor + valor.size.width, xTitulo + titulo.size.width) + relleno
                val bloque = Rect(izq, y - valor.size.height - relleno * 0.6f, der, y + titulo.size.height + relleno)
                drawRoundRect(p.tarjeta, bloque.topLeft, bloque.size, CornerRadius(10.dp.toPx()))
                if (elegido) {
                    drawRoundRect(p.verde, bloque.topLeft, bloque.size, CornerRadius(10.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
                drawText(valor, topLeft = Offset(xValor, y - valor.size.height))
                drawText(titulo, topLeft = Offset(xTitulo, y + 1.dp.toPx()))

                dibujarLatido(e.ancla, t + indice * 0.37f, p, elegido)

                // Zonas tocables: el bloque de texto y el círculo
                zonas += ZonaToque(e.parametro, bloque.inflate(6.dp.toPx()))
                zonas += ZonaToque(e.parametro, Rect(e.ancla, 22.dp.toPx()))
            }
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
            aviso = if (ok) "✔ " + codigos.joinToString(" + ") { Comandos.descripcion(it) } else "✖ No se pudo enviar"
        }
    }

    val titulo = when (parametro) {
        is Parametro.Planta -> "Planta ${parametro.indice + 1}"
        Parametro.Temperatura -> "Temperatura"
        Parametro.Ventilador -> "Ventilador"
        Parametro.Ventana -> "Ventana"
    }
    val verde = MaterialTheme.colorScheme.secondary

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = alCerrar) { Icon(Icons.Filled.Close, contentDescription = "Cerrar") }
            }
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (parametro) {
                    is Parametro.Planta -> {
                        val h = l.humedades.getOrNull(parametro.indice) ?: 0f
                        Text("${h.roundToInt()}% de humedad de suelo", color = verde, style = MaterialTheme.typography.titleLarge)
                        LinearProgressIndicator(progress = { h / 100f }, modifier = Modifier.fillMaxWidth(), color = verde)
                        Text(l.estadosSuelo.getOrNull(parametro.indice) ?: "", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Este valor solo se monitorea: el ESP32 no tiene bomba de riego conectada.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Parametro.Temperatura -> {
                        Text("%.1f °C".format(l.temperatura), color = verde, style = MaterialTheme.typography.titleLarge)
                        l.humedadAmbiente?.let { Text("Humedad del aire: ${it.roundToInt()}%") }
                        Text(
                            "En automático el ventilador se enciende sobre 25 °C y la ventana se abre entre 20 y 27 °C.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { enviar(Comandos.MAXIMO, Comandos.ABRIR) }, enabled = habilitado) { Text("Ventilar") }
                            OutlinedButton(onClick = { enviar(Comandos.AUTOMATICO) }, enabled = habilitado) { Text("Automático") }
                        }
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
                if (!puedeActivar && parametro !is Parametro.Planta) {
                    Text("Tu rol solo permite ver los datos.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                aviso?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}
