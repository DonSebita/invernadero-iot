package com.invernadero.ui.monitoreo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.lerp as lerpOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────
//  Fauna del fondo: animales que pasean por las colinas y aves que cruzan el cielo.
//  Todo es decorativo (no depende de los datos del ESP32).
// ─────────────────────────────────────────────────────────────
private const val GRADOS = 180f / PI.toFloat()
private val SILUETA = Color(0xFF2E1C26)      // al atardecer los animales se ven casi a contraluz

internal enum class Especie { CIERVO, ZORRO, CONEJO }

/**
 * Un animal que pasea por una colina y da la vuelta a la pantalla.
 * [escala] y [velocidad] van en "altos de árbol" de su capa (velocidad por segundo).
 * Camina [caminar] segundos y se queda quieto [pausa] segundos (pasta, olfatea o se sienta).
 */
internal class Animal(
    val especie: Especie,
    val escala: Float,
    val velocidad: Float,
    val dir: Float,       // 1 = hacia la derecha, -1 = hacia la izquierda
    val fase: Float,      // 0..1: dónde empieza su paseo
    val caminar: Float,
    val pausa: Float,
)

internal val ANIMALES_LEJOS = listOf(
    Animal(Especie.CIERVO, 0.60f, 0.22f, -1f, 0.30f, 14f, 6f),
)

internal val ANIMALES_MEDIO = listOf(
    Animal(Especie.CIERVO, 0.62f, 0.30f, 1f, 0.05f, 9f, 5f),
    Animal(Especie.ZORRO, 0.34f, 0.55f, -1f, 0.55f, 7f, 3f),
    Animal(Especie.CONEJO, 0.22f, 0.70f, 1f, 0.78f, 3f, 4f),
)

/** En la colina más cercana: más grandes y con todo su color. */
internal val ANIMALES_CERCA = listOf(
    Animal(Especie.CONEJO, 0.20f, 0.75f, -1f, 0.15f, 2.5f, 5f),
    Animal(Especie.ZORRO, 0.30f, 0.50f, 1f, 0.40f, 8f, 4f),
    Animal(Especie.CIERVO, 0.50f, 0.28f, -1f, 0.85f, 11f, 7f),
)

private class Pelaje(val base: Color, val oscuro: Color, val claro: Color)

private fun pelajeDe(e: Especie) = when (e) {
    Especie.CIERVO -> Pelaje(Color(0xFF9C6B45), Color(0xFF5D3D27), Color(0xFFF1E3CF))
    Especie.ZORRO -> Pelaje(Color(0xFFD9772E), Color(0xFF4A2A1A), Color(0xFFFFF3E3))
    Especie.CONEJO -> Pelaje(Color(0xFFA89584), Color(0xFF5E4F44), Color(0xFFFFFFFF))
}

/**
 * Segundos "andados" hasta [t] (arranca y frena con suavidad) y el ritmo actual
 * (0 = quieto, 1 = caminando).
 */
private fun recorrido(t: Float, caminar: Float, pausa: Float): Pair<Float, Float> {
    val r = min(0.8f, caminar / 3f)
    val periodo = caminar + pausa
    val n = floor(t / periodo)
    val s = t - n * periodo
    val (avance, ritmo) = when {
        s < r -> s * s / (2f * r) to s / r
        s < caminar - r -> s - r / 2f to 1f
        s < caminar -> (caminar - r) - (caminar - s) * (caminar - s) / (2f * r) to (caminar - s) / r
        else -> caminar - r to 0f
    }
    return n * (caminar - r) + avance to ritmo
}

/**
 * Dibuja los [animales] caminando sobre [colina]. [neblina] es el color de la colina y
 * [lejania] cuánto se desvanecen en ella (los de lejos se ven más pálidos).
 */
internal fun DrawScope.dibujarAnimales(
    animales: List<Animal>, colina: Colina, altoBase: Float, t: Float, tarde: Float, neblina: Color, lejania: Float
) {
    val w = size.width
    fun tono(c: Color) = lerp(lerp(c, SILUETA, tarde * 0.8f), neblina, lejania)

    for ((i, a) in animales.withIndex()) {
        val alto = altoBase * a.escala
        val (andado, ritmo) = recorrido(t + i * 3.1f, a.caminar, a.pausa)
        val distancia = andado * a.velocidad * altoBase
        val margen = alto * 1.8f
        val vuelta = w + margen * 2f
        val avance = (a.fase * vuelta + distancia) % vuelta - margen
        val x = if (a.dir > 0f) avance else w - avance
        if (x < -margen || x > w + margen) continue

        val pie = Offset(x, sueloEn(colina, x) + altoBase * 0.03f)
        val d = alto * 0.3f
        val inclinacion = atan2(sueloEn(colina, x + d) - sueloEn(colina, x - d), 2f * d) * GRADOS
        val pasos = distancia / (alto * 0.55f) * 2f * PI.toFloat()
        val p = pelajeDe(a.especie)
        val pel = Pelaje(tono(p.base), tono(p.oscuro), tono(p.claro))

        // Se dibujan mirando a la derecha; si va a la izquierda se espeja. Se inclinan con la ladera.
        withTransform({
            rotate(inclinacion, pie)
            scale(a.dir, 1f, pie)
        }) {
            when (a.especie) {
                Especie.CIERVO -> dibujarCiervo(pie, alto, pasos, ritmo, t + i, pel)
                Especie.ZORRO -> dibujarZorro(pie, alto, pasos, ritmo, t + i, pel)
                Especie.CONEJO -> dibujarConejo(pie, alto, pasos, ritmo, t + i, pel)
            }
        }
    }
}

/** Pata recta desde la cadera hasta el suelo, girada [angulo] radianes respecto de la vertical. */
private fun DrawScope.pata(cadera: Offset, suelo: Float, angulo: Float, grosor: Float, color: Color) {
    val largo = suelo - cadera.y
    drawLine(color, cadera, Offset(cadera.x + sin(angulo) * largo, cadera.y + cos(angulo) * largo), grosor, StrokeCap.Round)
}

private fun DrawScope.dibujarCiervo(p: Offset, s: Float, pasos: Float, ritmo: Float, t: Float, c: Pelaje) {
    val salto = abs(sin(pasos)) * s * 0.015f * ritmo
    val cuerpoY = p.y - s * 0.58f - salto
    val amp = 0.35f * ritmo
    val grosor = s * 0.055f
    val pi = PI.toFloat()

    // Patas del lado lejano (más oscuras), cuerpo, patas del lado cercano
    pata(Offset(p.x - s * 0.24f, cuerpoY + s * 0.06f), p.y, sin(pasos) * amp, grosor, c.oscuro)
    pata(Offset(p.x + s * 0.24f, cuerpoY + s * 0.06f), p.y, sin(pasos + pi) * amp, grosor, c.oscuro)
    drawOval(c.claro, Offset(p.x - s * 0.44f, cuerpoY - s * 0.11f), Size(s * 0.10f, s * 0.13f))   // colita
    drawOval(c.base, Offset(p.x - s * 0.38f, cuerpoY - s * 0.14f), Size(s * 0.76f, s * 0.28f))
    pata(Offset(p.x - s * 0.20f, cuerpoY + s * 0.06f), p.y, sin(pasos + pi) * amp, grosor, c.base)
    pata(Offset(p.x + s * 0.28f, cuerpoY + s * 0.06f), p.y, sin(pasos) * amp, grosor, c.base)

    // Quieto: baja la cabeza a pastar y de vez en cuando la levanta a mirar
    val baja = (1f - ritmo) * (sin(t * 0.9f) * 1.5f + 0.8f).coerceIn(0f, 1f)
    val cabeza = lerpOffset(
        Offset(p.x + s * 0.40f, cuerpoY - s * 0.38f + sin(pasos * 2f) * s * 0.015f * ritmo),
        Offset(p.x + s * 0.52f, p.y - s * 0.10f),
        baja
    )
    drawLine(c.base, Offset(p.x + s * 0.24f, cuerpoY - s * 0.03f), cabeza, s * 0.13f, StrokeCap.Round)
    rotate(-35f, cabeza) { drawOval(c.base, Offset(cabeza.x - s * 0.17f, cabeza.y - s * 0.08f), Size(s * 0.11f, s * 0.05f)) }
    drawOval(c.base, Offset(cabeza.x - s * 0.08f, cabeza.y - s * 0.055f), Size(s * 0.22f, s * 0.11f))
    drawCircle(c.oscuro, s * 0.022f, Offset(cabeza.x + s * 0.135f, cabeza.y))

    // Cuernos
    val astas = Stroke(s * 0.025f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val raiz = Offset(cabeza.x - s * 0.03f, cabeza.y - s * 0.05f)
    val cuerno = Path().apply {
        moveTo(raiz.x, raiz.y)
        quadraticBezierTo(raiz.x - s * 0.10f, raiz.y - s * 0.12f, raiz.x - s * 0.06f, raiz.y - s * 0.28f)
        moveTo(raiz.x - s * 0.06f, raiz.y - s * 0.12f)
        lineTo(raiz.x + s * 0.04f, raiz.y - s * 0.20f)
        moveTo(raiz.x - s * 0.08f, raiz.y - s * 0.20f)
        lineTo(raiz.x - s * 0.16f, raiz.y - s * 0.27f)
    }
    drawPath(cuerno, c.oscuro, style = astas)
}

private fun DrawScope.dibujarZorro(p: Offset, s: Float, pasos: Float, ritmo: Float, t: Float, c: Pelaje) {
    val salto = abs(sin(pasos)) * s * 0.03f * ritmo
    val cuerpoY = p.y - s * 0.50f - salto
    val amp = 0.45f * ritmo
    val grosor = s * 0.08f
    val pi = PI.toFloat()

    pata(Offset(p.x - s * 0.26f, cuerpoY + s * 0.08f), p.y, sin(pasos) * amp, grosor, c.oscuro)
    pata(Offset(p.x + s * 0.26f, cuerpoY + s * 0.08f), p.y, sin(pasos + pi) * amp, grosor, c.oscuro)

    // Cola esponjosa que se mece, con la punta blanca
    val meneo = sin(t * 2.5f) * s * 0.06f
    val base = Offset(p.x - s * 0.36f, cuerpoY - s * 0.04f)
    val punta = Offset(p.x - s * 0.98f, cuerpoY + s * 0.12f + meneo)
    val cola = Path().apply {
        moveTo(base.x, base.y - s * 0.10f)
        quadraticBezierTo(p.x - s * 0.75f, cuerpoY - s * 0.24f + meneo * 0.5f, punta.x, punta.y)
        quadraticBezierTo(p.x - s * 0.70f, cuerpoY + s * 0.22f + meneo * 0.5f, base.x, base.y + s * 0.10f)
        close()
    }
    drawPath(cola, c.base)
    drawCircle(c.claro, s * 0.065f, Offset(punta.x + s * 0.06f, punta.y - s * 0.015f))

    drawOval(c.base, Offset(p.x - s * 0.45f, cuerpoY - s * 0.15f), Size(s * 0.90f, s * 0.30f))
    drawOval(c.claro, Offset(p.x - s * 0.10f, cuerpoY + s * 0.03f), Size(s * 0.40f, s * 0.10f))   // panza
    pata(Offset(p.x - s * 0.22f, cuerpoY + s * 0.08f), p.y, sin(pasos + pi) * amp, grosor, c.base)
    pata(Offset(p.x + s * 0.30f, cuerpoY + s * 0.08f), p.y, sin(pasos) * amp, grosor, c.base)

    // Quieto: baja el hocico a olfatear
    val baja = (1f - ritmo) * (sin(t * 1.6f) * 1.2f + 0.6f).coerceIn(0f, 1f)
    val cabeza = lerpOffset(Offset(p.x + s * 0.50f, cuerpoY - s * 0.18f), Offset(p.x + s * 0.62f, p.y - s * 0.12f), baja)
    drawLine(c.base, Offset(p.x + s * 0.30f, cuerpoY - s * 0.02f), cabeza, s * 0.18f, StrokeCap.Round)
    val orejas = Path().apply {
        moveTo(cabeza.x - s * 0.11f, cabeza.y - s * 0.05f)
        lineTo(cabeza.x - s * 0.08f, cabeza.y - s * 0.26f)
        lineTo(cabeza.x - s * 0.01f, cabeza.y - s * 0.08f)
        close()
        moveTo(cabeza.x - s * 0.02f, cabeza.y - s * 0.06f)
        lineTo(cabeza.x + s * 0.03f, cabeza.y - s * 0.25f)
        lineTo(cabeza.x + s * 0.08f, cabeza.y - s * 0.05f)
        close()
    }
    drawPath(orejas, c.base)
    val hocico = Offset(cabeza.x + s * 0.22f, cabeza.y + s * 0.04f)
    val cara = Path().apply {
        moveTo(cabeza.x - s * 0.10f, cabeza.y - s * 0.09f)
        lineTo(hocico.x, hocico.y)
        lineTo(cabeza.x - s * 0.06f, cabeza.y + s * 0.10f)
        close()
    }
    drawPath(cara, c.base)
    drawCircle(c.base, s * 0.10f, cabeza)
    drawCircle(c.oscuro, s * 0.03f, hocico)
}

private fun DrawScope.dibujarConejo(p: Offset, s: Float, pasos: Float, ritmo: Float, t: Float, c: Pelaje) {
    // Avanza a saltitos; quieto, se sienta y mueve las orejas
    val salto = abs(sin(pasos * 0.5f)) * s * 0.45f * ritmo
    val y = p.y - salto
    drawOval(c.base, Offset(p.x - s * 0.32f, y - s * 0.18f), Size(s * 0.40f, s * 0.18f))   // pata trasera
    drawOval(c.base, Offset(p.x - s * 0.38f, y - s * 0.62f), Size(s * 0.68f, s * 0.52f))
    drawLine(c.base, Offset(p.x + s * 0.20f, y - s * 0.25f), Offset(p.x + s * 0.24f + s * 0.10f * ritmo, y - s * 0.03f), s * 0.08f, StrokeCap.Round)
    drawCircle(c.claro, s * 0.11f, Offset(p.x - s * 0.38f, y - s * 0.40f))

    val cabeza = Offset(p.x + s * 0.30f, y - s * 0.66f)
    val atento = if (sin(t * 0.7f) > 0.6f) 1f else 0f
    val tic = sin(t * 9f) * 7f * atento * (1f - ritmo)
    rotate(-8f, cabeza) { drawOval(c.oscuro, Offset(cabeza.x - s * 0.04f, cabeza.y - s * 0.48f), Size(s * 0.11f, s * 0.42f)) }
    rotate(-24f + tic, cabeza) { drawOval(c.base, Offset(cabeza.x - s * 0.07f, cabeza.y - s * 0.50f), Size(s * 0.13f, s * 0.44f)) }
    drawCircle(c.base, s * 0.17f, cabeza)
    drawCircle(c.oscuro, s * 0.03f, Offset(cabeza.x + s * 0.07f, cabeza.y - s * 0.03f))
}

// ─────────────────────────────────────────────────────────────
//  Aves: bandadas en V que cruzan el cielo de vez en cuando, aleteando y planeando
// ─────────────────────────────────────────────────────────────
/** [altura] en fracción del alto; cruzan durante el 60 % de su [periodo] (s) y el resto no se ven. */
private class Bandada(val altura: Float, val periodo: Float, val fase: Float, val dir: Float, val aves: Int, val escala: Float)

private val BANDADAS = listOf(
    Bandada(0.19f, 34f, 0.05f, 1f, 3, 1.0f),
    Bandada(0.38f, 47f, 0.50f, -1f, 2, 0.75f),
    Bandada(0.10f, 61f, 0.80f, -1f, 1, 1.15f),
    Bandada(0.27f, 83f, 0.30f, 1f, 4, 0.65f),
)

internal fun DrawScope.dibujarAves(t: Float, tarde: Float, ref: Float) {
    val w = size.width
    val h = size.height
    val color = lerp(Color(0xFF2B3A4A), Color(0xFF2A1A24), tarde).copy(alpha = 0.8f)
    for ((k, b) in BANDADAS.withIndex()) {
        val prog = (t / b.periodo + b.fase) % 1f
        if (prog > 0.6f) continue
        val tam = ref * 0.028f * b.escala
        val margen = tam * 10f
        val tramo = prog / 0.6f * (w + margen * 2f) - margen
        val xLider = if (b.dir > 0f) tramo else w - tramo
        val yLider = h * b.altura + sin(t * 0.5f + k) * tam * 1.2f
        val planeo = ((sin(t * 0.45f + k * 2f) - 0.4f) / 0.6f).coerceIn(0f, 1f)   // a ratos dejan de aletear
        for (i in 0 until b.aves) {
            val fila = (i + 1) / 2
            val lado = if (i % 2 == 1) -1f else 1f
            val centro = Offset(
                xLider - b.dir * fila * tam * 2.4f,
                yLider + lado * fila * tam * 1.3f + sin(t * 1.1f + i) * tam * 0.25f
            )
            val aleteo = sin(t * 8f + i * 1.7f + k) * (1f - 0.85f * planeo)
            dibujarAve(centro, tam, aleteo, color)
        }
    }
}

private fun DrawScope.dibujarAve(c: Offset, tam: Float, aleteo: Float, color: Color) {
    val punta = c.y + tam * 0.1f - aleteo * tam * 0.55f
    val codo = c.y - tam * 0.30f - aleteo * tam * 0.25f
    val alas = Path().apply {
        moveTo(c.x - tam, punta)
        quadraticBezierTo(c.x - tam * 0.45f, codo, c.x, c.y)
        quadraticBezierTo(c.x + tam * 0.45f, codo, c.x + tam, punta)
    }
    drawPath(alas, color, style = Stroke(tam * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(color, tam * 0.12f, Offset(c.x, c.y + tam * 0.04f))
}
