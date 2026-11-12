package com.invernadero.ui.monitoreo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invernadero.Contenedor
import com.invernadero.conexion.EstadoConexion
import com.invernadero.conexion.LecturaEsp32
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────
//  Pantalla de MONITOREO: caja 3D animada con los datos en vivo
// ─────────────────────────────────────────────────────────────
@Composable
fun PantallaMonitoreo(c: Contenedor, modoTarde: Boolean, alCambiarModo: (Boolean) -> Unit) {
    val lectura by c.conexion.lectura.collectAsState()
    val estado by c.conexion.estado.collectAsState()

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

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(paleta.fondo))) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BarraSuperior(paleta, modoTarde, alCambiarModo)
            EscenaInvernadero(lectura, tiempo, aspas, velocidad, anguloVentana, paleta, Modifier.fillMaxWidth().weight(1f))
            BarraEstado(lectura, estado, paleta)
        }
    }
}

@Composable
private fun BarraSuperior(p: Paleta, modoTarde: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.tarjeta).padding(horizontal = 16.dp, vertical = 8.dp),
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.tarjeta).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (e.conectado) p.verde else Color(0xFFE53935)))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(e.descripcion, color = p.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                e.mensaje ?: if (e.compartiendo) "Compartiendo con ${e.telefonosConectados} teléfono(s)" else "Conectado",
                color = p.texto, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (lectura != null) {
            Text("${lectura.humedades.size} sensores", color = p.verde, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Canvas: caja 3D centrada + etiquetas estilo Paint con latido
// ─────────────────────────────────────────────────────────────
@Composable
private fun EscenaInvernadero(
    lectura: LecturaEsp32?,
    tiempo: State<Float>,
    aspas: State<Float>,
    velocidad: State<Float>,
    anguloVentana: State<Float>,
    paleta: Paleta,
    modifier: Modifier = Modifier
) {
    val medidor = rememberTextMeasurer()
    Canvas(modifier) {
        if (lectura == null) {
            val txt = medidor.measure("Esperando datos…", TextStyle(color = paleta.texto, fontSize = 16.sp))
            drawText(txt, topLeft = center - Offset(txt.size.width / 2f, txt.size.height / 2f))
            return@Canvas
        }
        val margenEtiquetas = 84.dp.toPx()
        val escala = min((size.width - 2 * margenEtiquetas) / 1.95f, size.height / 1.9f)
        val cam = Camara(YAW_GRADOS, PITCH_GRADOS, escala, center + Offset(0f, size.height * 0.04f))

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
                add(Etiqueta(a, "${lectura.humedades[i].roundToInt()}%", estado ?: "Humedad suelo ${i + 1}"))
            }
            val aire = lectura.humedadAmbiente?.let { " · aire ${it.roundToInt()}%" } ?: ""
            add(Etiqueta(anclas.temperatura, "%.1f °C".format(lectura.temperatura), "Temperatura$aire"))
            add(Etiqueta(
                anclas.ventilador,
                if (lectura.ventiladorEncendido) "${(lectura.velocidadVentilador * 100).roundToInt()}%" else "OFF",
                "Ventilador · ${lectura.modoVentilador.lowercase()}"
            ))
            add(Etiqueta(anclas.ventana, "${lectura.anguloVentana.roundToInt()}°", "Ventana · ${lectura.modoVentana.lowercase()}"))
        }
        dibujarEtiquetas(etiquetas, medidor, paleta, tiempo.value)
    }
}

private data class Etiqueta(val ancla: Offset, val valor: String, val titulo: String)

/** Círculo sobre el objeto → línea diagonal → línea horizontal → dato (como el dibujo en Paint). */
private fun DrawScope.dibujarEtiquetas(lista: List<Etiqueta>, medidor: TextMeasurer, p: Paleta, t: Float) {
    val estiloValor = TextStyle(color = p.verde, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    val estiloTitulo = TextStyle(color = p.texto, fontSize = 11.sp)
    val margen = 4.dp.toPx()
    val separacion = 46.dp.toPx()
    val subida = 48.dp.toPx()
    val grosor = 1.6.dp.toPx()

    lista.withIndex()
        .groupBy { it.value.ancla.x < size.width / 2f }
        .forEach { (izquierda, grupo) ->
            val orden = grupo.sortedBy { it.value.ancla.y }
            val ys = FloatArray(orden.size)
            var minimo = 24.dp.toPx()
            orden.forEachIndexed { k, e ->
                ys[k] = max(e.value.ancla.y - subida, minimo)
                minimo = ys[k] + separacion
            }
            val exceso = (ys.lastOrNull() ?: 0f) - (size.height - 28.dp.toPx())
            if (exceso > 0f) for (k in ys.indices) ys[k] -= exceso

            orden.forEachIndexed { k, (indice, e) ->
                val valor = medidor.measure(e.valor, estiloValor)
                val titulo = medidor.measure(e.titulo, estiloTitulo)
                val anchoTexto = max(valor.size.width, titulo.size.width).toFloat()
                val y = ys[k]
                val hueco = 6.dp.toPx()
                val xFin = if (izquierda) margen + anchoTexto + hueco else size.width - margen - anchoTexto - hueco
                val dy = abs(e.ancla.y - y)
                val codoX = if (izquierda) max(e.ancla.x - dy * 0.6f, xFin + 8.dp.toPx())
                            else min(e.ancla.x + dy * 0.6f, xFin - 8.dp.toPx())
                val codo = Offset(codoX, y)

                drawLine(p.linea, e.ancla, codo, grosor, StrokeCap.Round)
                drawLine(p.linea, codo, Offset(xFin, y), grosor, StrokeCap.Round)

                val xValor = if (izquierda) xFin - hueco - valor.size.width else xFin + hueco
                val xTitulo = if (izquierda) xFin - hueco - titulo.size.width else xFin + hueco
                drawText(valor, topLeft = Offset(xValor, y - valor.size.height))
                drawText(titulo, topLeft = Offset(xTitulo, y + 1.dp.toPx()))

                dibujarLatido(e.ancla, t + indice * 0.17f, p)
            }
        }
}

/** Círculo que late como un corazón (doble golpe) con onda expansiva. */
private fun DrawScope.dibujarLatido(c: Offset, t: Float, p: Paleta) {
    val periodo = 1.1f
    val f = (t % periodo) / periodo
    fun golpe(centro: Float, ancho: Float) = exp(-((f - centro) / ancho).let { it * it })
    val latido = golpe(0.06f, 0.045f) + 0.6f * golpe(0.26f, 0.05f)
    val base = 6.dp.toPx()
    drawCircle(p.verde.copy(alpha = 0.5f * (1f - f)), base + 16.dp.toPx() * f, c, style = Stroke(2.dp.toPx()))
    val r = base * (1f + 0.35f * latido)
    drawCircle(p.linea, r, c, style = Stroke(2.dp.toPx()))
    drawCircle(p.verde, r * 0.45f, c)
}
