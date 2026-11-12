package com.invernadero.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────
//  Contenedores
// ─────────────────────────────────────────────────────────────

/**
 * Bloque de contenido: superficie tonal plana (sin sombra) con un borde fino.
 * Título en Bricolage; subtítulo opcional y una acción opcional a la derecha.
 */
@Composable
fun Seccion(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    accion: (@Composable () -> Unit)? = null,
    contenido: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainerLow,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(titulo, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, modifier = Modifier.semantics { heading() })
                    if (subtitulo != null) Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                if (accion != null) accion()
            }
            contenido()
        }
    }
}

/** Encabezado de pantalla: título grande + bajada opcional. */
@Composable
fun Encabezado(titulo: String, bajada: String? = null, modifier: Modifier = Modifier, extra: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
        if (bajada != null) Text(bajada, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (extra != null) extra()
    }
}

/** Píldora de estado (punto + texto), p. ej. "WiFi directo · conectado". */
@Composable
fun PildoraEstado(texto: String, ok: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier, shape = CircleShape, color = if (ok) cs.secondaryContainer else cs.errorContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) cs.secondary else cs.error))
            Spacer(Modifier.width(8.dp))
            Text(texto, style = MaterialTheme.typography.labelMedium, color = if (ok) cs.onSecondaryContainer else cs.onErrorContainer, maxLines = 1)
        }
    }
}

/** RadioButton con texto, accesible (toda la fila es tocable). */
@Composable
fun OpcionRadio(texto: String, seleccionado: Boolean, habilitado: Boolean = true, ayuda: String? = null, alElegir: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = seleccionado, enabled = habilitado, role = Role.RadioButton, onClick = alElegir)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = seleccionado, onClick = null, enabled = habilitado)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(texto, style = MaterialTheme.typography.bodyLarge)
            if (ayuda != null) Text(ayuda, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Respuesta inmediata de los controles
// ─────────────────────────────────────────────────────────────

/**
 * Lo que el usuario acaba de pedir (mover un deslizador, prender el ventilador…).
 * El ESP32 confirma recién en la próxima lectura (~2 s); mientras tanto el control muestra lo pedido
 * en vez de volver al valor anterior. Se suelta al confirmarse, si el envío falla o a los 8 s.
 */
class Pedido<T> {
    var valor by mutableStateOf<T?>(null)
}

@Composable
fun <T> rememberPedido(): Pedido<T> = remember { Pedido() }

/** Valor a mostrar: lo pedido mientras no se confirme; si no, el [real]. */
@Composable
fun <T> Pedido<T>.mostrar(real: T, igual: (T, T) -> Boolean = { a, b -> a == b }): T {
    val p = valor
    LaunchedEffect(p) {
        if (p != null) {
            delay(8_000)
            valor = null
        }
    }
    val confirmado = p != null && igual(real, p)
    LaunchedEffect(confirmado) { if (confirmado) valor = null }
    return p ?: real
}

/** Fila "etiqueta ........ valor": etiqueta tenue, valor firme y con cifras alineadas. */
@Composable
fun Dato(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(etiqueta, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
    }
}

// ─────────────────────────────────────────────────────────────
//  Gráfica de serie temporal
// ─────────────────────────────────────────────────────────────

data class PuntoSerie(val ts: Long, val valor: Float)

private fun formatoValor(v: Float) = "%.1f".format(v)

/**
 * Gráfica de una variable en el tiempo:
 *  - resumen arriba (valor actual grande + cambio en el periodo),
 *  - eje Y con valores "redondos" y líneas guía, eje X con horas/fechas reales,
 *  - curva suave que no inventa picos (interpolación monótona) y se corta donde faltan datos,
 *  - banda opcional de referencia (p. ej. el rango de la ventana automática),
 *  - toca o arrastra el dedo sobre la gráfica para leer un punto exacto,
 *  - mínimo, promedio y máximo abajo.
 */
@Composable
fun GraficaSerie(
    puntos: List<PuntoSerie>,
    color: Color,
    unidad: String,
    nombre: String,
    etiquetaEje: (Long) -> String,
    modifier: Modifier = Modifier,
    limites: ClosedFloatingPointRange<Float>? = null,
    spanMinimo: Float = 4f,
    banda: ClosedFloatingPointRange<Float>? = null,
    etiquetaBanda: String? = null,
    desde: Long? = null,      // inicio del periodo elegido: el eje X lo cubre entero aunque falten datos
    hasta: Long? = null,
) {
    val cs = MaterialTheme.colorScheme
    val tipo = MaterialTheme.typography
    if (puntos.size < 2) {
        Box(
            modifier.fillMaxWidth().height(140.dp).clip(MaterialTheme.shapes.medium).background(cs.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Aún no hay datos suficientes.\nSe guarda una lectura cada 30 s.",
                style = tipo.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
            )
        }
        return
    }
    val valores = puntos.map { it.valor }
    val actual = valores.last()
    val minimo = valores.min()
    val maximo = valores.max()
    val promedio = valores.average().toFloat()
    val cambio = actual - valores.first()
    val u = unidad.trim()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatoValor(actual), style = tipo.headlineLarge, color = cs.onSurface)
            Text(u, style = tipo.titleMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (abs(cambio) < 0.05f) "Estable" else "%+.1f %s".format(cambio, u),
                    style = tipo.labelLarge, color = cs.onSurface
                )
                Text("en el periodo", style = tipo.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        LienzoSerie(puntos, color, u, nombre, etiquetaEje, limites, spanMinimo, banda, etiquetaBanda, desde, hasta)
        Row(Modifier.fillMaxWidth()) {
            Estadistica("Mínimo", "${formatoValor(minimo)} $u", Modifier.weight(1f))
            Estadistica("Promedio", "${formatoValor(promedio)} $u", Modifier.weight(1f))
            Estadistica("Máximo", "${formatoValor(maximo)} $u", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Estadistica(etiqueta: String, valor: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LienzoSerie(
    puntos: List<PuntoSerie>,
    color: Color,
    unidad: String,
    nombre: String,
    etiquetaEje: (Long) -> String,
    limites: ClosedFloatingPointRange<Float>?,
    spanMinimo: Float,
    banda: ClosedFloatingPointRange<Float>?,
    etiquetaBanda: String?,
    desde: Long?,
    hasta: Long?,
) {
    val cs = MaterialTheme.colorScheme
    val medidor = rememberTextMeasurer()
    val estiloEje = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    val estiloBanda = MaterialTheme.typography.labelSmall.copy(color = color)
    val estiloTip = MaterialTheme.typography.labelSmall.copy(color = cs.inverseOnSurface)
    val estiloTipValor = MaterialTheme.typography.titleSmall.copy(color = cs.inverseOnSurface)
    val guia = cs.outlineVariant
    val fondoTip = cs.inverseSurface
    val fondoPunto = cs.surfaceContainerLow
    val cursor = cs.onSurfaceVariant.copy(alpha = 0.5f)
    val formatoTip = remember { SimpleDateFormat("EEE d · HH:mm", Locale.forLanguageTag("es")) }

    val escala = remember(puntos, limites, spanMinimo) {
        escalaBonita(puntos.minOf { it.valor }, puntos.maxOf { it.valor }, spanMinimo, limites)
    }
    val cortes = remember(puntos) { indicesDeCorte(puntos) }
    val t0 = minOf(desde ?: puntos.first().ts, puntos.first().ts)
    val duracion = (maxOf(hasta ?: puntos.last().ts, puntos.last().ts) - t0).coerceAtLeast(1L)
    // Área del dibujo en px [izq, arriba, der, abajo]: la escribe el Canvas y la leen los gestos
    val area = remember { FloatArray(4) }
    var seleccion by remember(puntos) { mutableStateOf<Int?>(null) }

    fun indiceCercano(x: Float): Int {
        val ancho = (area[2] - area[0]).coerceAtLeast(1f)
        val t = t0 + ((x - area[0]) / ancho).coerceIn(0f, 1f) * duracion
        var lo = 0
        var hi = puntos.lastIndex
        while (hi - lo > 1) {
            val m = (lo + hi) / 2
            if (puntos[m].ts < t) lo = m else hi = m
        }
        return if (abs(puntos[lo].ts - t) <= abs(puntos[hi].ts - t)) lo else hi
    }

    val descripcion = "$nombre: ahora ${formatoValor(puntos.last().valor)} $unidad, " +
        "mínimo ${formatoValor(puntos.minOf { it.valor })}, máximo ${formatoValor(puntos.maxOf { it.valor })}"

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .semantics { contentDescription = descripcion }
            .pointerInput(puntos) {
                detectTapGestures { p ->
                    val i = indiceCercano(p.x)
                    seleccion = if (seleccion == i) null else i
                }
            }
            .pointerInput(puntos) {
                detectHorizontalDragGestures(onDragStart = { p -> seleccion = indiceCercano(p.x) }) { cambio, _ ->
                    seleccion = indiceCercano(cambio.position.x)
                    cambio.consume()
                }
            }
    ) {
        val etiquetasY = escala.ticks.map { medidor.measure(escala.formatear(it), estiloEje) }
        val separacion = 8.dp.toPx()
        val anchoEje = (etiquetasY.maxOfOrNull { it.size.width } ?: 0) + separacion
        val muestraX = medidor.measure(etiquetaEje(t0), estiloEje)
        val izq = anchoEje
        val der = size.width - 6.dp.toPx()
        val arriba = 6.dp.toPx()
        val abajo = size.height - muestraX.size.height - separacion
        area[0] = izq; area[1] = arriba; area[2] = der; area[3] = abajo

        fun xDe(ts: Long) = izq + (ts - t0).toFloat() / duracion * (der - izq)
        fun yDe(v: Float) = abajo - (v - escala.lo) / (escala.hi - escala.lo) * (abajo - arriba)

        // Banda de referencia
        if (banda != null) {
            val yA = yDe(banda.endInclusive.coerceIn(escala.lo, escala.hi))
            val yB = yDe(banda.start.coerceIn(escala.lo, escala.hi))
            if (yB - yA > 1f) {
                drawRect(color.copy(alpha = 0.09f), Offset(izq, yA), Size(der - izq, yB - yA))
                if (etiquetaBanda != null) {
                    val et = medidor.measure(etiquetaBanda, estiloBanda)
                    if (yB - yA > et.size.height + 4.dp.toPx()) {
                        drawText(et, topLeft = Offset(der - et.size.width - 6.dp.toPx(), yA + 2.dp.toPx()))
                    }
                }
            }
        }

        // Líneas guía + valores del eje Y
        val trazo = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
        escala.ticks.forEachIndexed { k, v ->
            val y = yDe(v)
            drawLine(guia, Offset(izq, y), Offset(der, y), 1.dp.toPx(), pathEffect = if (k == 0) null else trazo)
            val et = etiquetasY[k]
            drawText(et, topLeft = Offset(anchoEje - separacion - et.size.width, y - et.size.height / 2f))
        }

        // Eje X: inicio, mitad y fin del periodo
        val yTexto = abajo + separacion * 0.6f
        val fin = medidor.measure(etiquetaEje(t0 + duracion), estiloEje)
        drawText(muestraX, topLeft = Offset(izq, yTexto))
        drawText(fin, topLeft = Offset(der - fin.size.width, yTexto))
        val medio = medidor.measure(etiquetaEje(t0 + duracion / 2), estiloEje)
        val xMedio = (izq + der) / 2f - medio.size.width / 2f
        if (xMedio > izq + muestraX.size.width + separacion && xMedio + medio.size.width < der - fin.size.width - separacion) {
            drawText(medio, topLeft = Offset(xMedio, yTexto))
        }

        // Curva por tramos (se corta donde no hubo lecturas)
        val n = puntos.size
        val xs = FloatArray(n) { xDe(puntos[it].ts) }

        // Parte del periodo sin registros (p. ej. la app es nueva o el invernadero estuvo apagado)
        val sinDatos = xs[0] - izq
        if (sinDatos > 72.dp.toPx()) {
            drawRect(guia.copy(alpha = 0.25f), Offset(izq, arriba), Size(sinDatos, abajo - arriba))
            val et = medidor.measure("sin registros", estiloEje)
            if (et.size.width < sinDatos - 8.dp.toPx()) {
                drawText(et, topLeft = Offset(izq + sinDatos / 2f - et.size.width / 2f, (arriba + abajo) / 2f - et.size.height / 2f))
            }
        }
        val ys = FloatArray(n) { yDe(puntos[it].valor) }
        val grosor = 2.5.dp.toPx()
        var inicio = 0
        for (i in 1..n) {
            if (i < n && i !in cortes) continue
            val a = inicio
            val b = i - 1
            inicio = i
            if (a == b) {
                drawCircle(color, grosor, Offset(xs[a], ys[a]))
                continue
            }
            val linea = Path().apply { curvaMonotona(xs, ys, a, b) }
            val relleno = Path().apply {
                addPath(linea)
                lineTo(xs[b], abajo)
                lineTo(xs[a], abajo)
                close()
            }
            drawPath(relleno, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)), startY = arriba, endY = abajo))
            drawPath(linea, color, style = Stroke(grosor, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Último valor
        val ultimo = Offset(xs[n - 1], ys[n - 1])
        drawCircle(fondoPunto, 6.dp.toPx(), ultimo)
        drawCircle(color, 4.dp.toPx(), ultimo)

        // Punto elegido con el dedo
        val sel = seleccion
        if (sel != null && sel in puntos.indices) {
            val p = Offset(xs[sel], ys[sel])
            drawLine(cursor, Offset(p.x, arriba), Offset(p.x, abajo), 1.dp.toPx())
            drawCircle(fondoPunto, 7.dp.toPx(), p)
            drawCircle(color, 5.dp.toPx(), p)

            val v = medidor.measure("${formatoValor(puntos[sel].valor)} $unidad", estiloTipValor)
            val t = medidor.measure(formatoTip.format(Date(puntos[sel].ts)), estiloTip)
            val relleno = 10.dp.toPx()
            val w = max(v.size.width, t.size.width) + relleno * 2
            val h = v.size.height + t.size.height + relleno * 1.4f
            val bx = (p.x - w / 2f).coerceIn(izq, max(izq, der - w))
            val by = if (p.y - arriba > h + 12.dp.toPx()) arriba else abajo - h
            drawRoundRect(fondoTip, Offset(bx, by), Size(w, h), CornerRadius(12.dp.toPx()))
            drawText(v, topLeft = Offset(bx + relleno, by + relleno * 0.7f))
            drawText(t, topLeft = Offset(bx + relleno, by + relleno * 0.7f + v.size.height))
        }
    }
}

/** Escala del eje Y con pasos "redondos" (1, 2, 2.5, 5 × 10ⁿ). */
private class Escala(val lo: Float, val hi: Float, val paso: Float) {
    val ticks: List<Float> = buildList {
        var k = 0
        while (true) {
            val v = lo + k * paso
            if (v > hi + paso * 0.01f) break
            add(v)
            k++
        }
    }
    fun formatear(v: Float) = if (paso >= 1f) "%.0f".format(v) else "%.1f".format(v)
}

private fun pasoBonito(rango: Float, divisiones: Int): Float {
    val crudo = (rango / divisiones).coerceAtLeast(0.01f)
    val magnitud = 10f.pow(floor(log10(crudo)))
    val n = crudo / magnitud
    val f = when {
        n <= 1f -> 1f
        n <= 2f -> 2f
        n <= 2.5f -> 2.5f
        n <= 5f -> 5f
        else -> 10f
    }
    return f * magnitud
}

private fun escalaBonita(min: Float, max: Float, spanMinimo: Float, limites: ClosedFloatingPointRange<Float>?): Escala {
    var a = min
    var b = max
    if (b - a < spanMinimo) {
        val c = (a + b) / 2f
        a = c - spanMinimo / 2f
        b = c + spanMinimo / 2f
    }
    if (limites != null) {
        if (a < limites.start) { b += limites.start - a; a = limites.start }
        if (b > limites.endInclusive) { a -= b - limites.endInclusive; b = limites.endInclusive }
        a = a.coerceAtLeast(limites.start)
    }
    val paso = pasoBonito(b - a, 4)
    var lo = floor(a / paso) * paso
    var hi = ceil(b / paso) * paso
    if (limites != null) {
        lo = lo.coerceAtLeast(limites.start)
        hi = hi.coerceAtMost(limites.endInclusive)
    }
    if (hi - lo < paso) hi = lo + paso
    return Escala(lo, hi, paso)
}

/** Índices donde empieza un tramo nuevo porque antes hubo un hueco largo sin lecturas. */
private fun indicesDeCorte(p: List<PuntoSerie>): Set<Int> {
    if (p.size < 3) return emptySet()
    val difs = (1 until p.size).map { p[it].ts - p[it - 1].ts }.sorted()
    val umbral = max(difs[difs.size / 2] * 4, 120_000L)
    return (1 until p.size).filter { p[it].ts - p[it - 1].ts > umbral }.toSet()
}

/** Curva cúbica monótona (Fritsch–Carlson): pasa por todos los puntos y no inventa picos. */
private fun Path.curvaMonotona(xs: FloatArray, ys: FloatArray, a: Int, b: Int) {
    val n = b - a + 1
    moveTo(xs[a], ys[a])
    if (n == 2) { lineTo(xs[b], ys[b]); return }
    val d = FloatArray(n - 1) { k ->
        val h = xs[a + k + 1] - xs[a + k]
        if (h <= 0f) 0f else (ys[a + k + 1] - ys[a + k]) / h
    }
    val m = FloatArray(n)
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (k in 1 until n - 1) m[k] = if (d[k - 1] * d[k] <= 0f) 0f else (d[k - 1] + d[k]) / 2f
    for (k in 0 until n - 1) {
        if (d[k] == 0f) {
            m[k] = 0f; m[k + 1] = 0f
        } else {
            val al = m[k] / d[k]
            val be = m[k + 1] / d[k]
            val s = al * al + be * be
            if (s > 9f) {
                val t = 3f / sqrt(s)
                m[k] = t * al * d[k]
                m[k + 1] = t * be * d[k]
            }
        }
    }
    for (k in 0 until n - 1) {
        val x0 = xs[a + k]
        val x1 = xs[a + k + 1]
        val h = (x1 - x0) / 3f
        cubicTo(x0 + h, ys[a + k] + m[k] * h, x1 - h, ys[a + k + 1] - m[k + 1] * h, x1, ys[a + k + 1])
    }
}

fun formatoFecha(ts: Long): String = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(ts))

/** Campo de contraseña con "Ver/Ocultar". Sin autocorrección ni sugerencias del teclado. */
@Composable
fun CampoClave(
    valor: String,
    etiqueta: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    ayuda: String? = null,
    accionFinal: (() -> Unit)? = null
) {
    var ver by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        supportingText = ayuda?.let { { Text(it) } },
        visualTransformation = if (ver) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (accionFinal != null) ImeAction.Done else ImeAction.Next,
            autoCorrectEnabled = false
        ),
        keyboardActions = KeyboardActions(onDone = { accionFinal?.invoke() }),
        trailingIcon = { TextButton(onClick = { ver = !ver }) { Text(if (ver) "Ocultar" else "Ver") } }
    )
}

/** Copia texto marcándolo como sensible (Android 13+ no lo muestra en la vista previa del portapapeles). */
fun copiarSensible(contexto: Context, etiqueta: String, texto: String) {
    val clip = ClipData.newPlainText(etiqueta, texto)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    contexto.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}
