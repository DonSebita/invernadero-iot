package com.invernadero.ui.monitoreo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────
//  Vista: 30° girada a la derecha y 15° mirando hacia abajo
// ─────────────────────────────────────────────────────────────
const val YAW_GRADOS = 30f
const val PITCH_GRADOS = 15f

// Medidas de la caja (unidades libres; la altura es 1)
private const val ANCHO = 1.6f
private const val ALTO = 1.0f
private const val FONDO = 1.0f
private const val SUELO = 0.18f          // grosor de la tierra
private const val HX = ANCHO / 2f
private const val HY = ALTO / 2f
private const val HZ = FONDO / 2f

// Ventana en la cara IZQUIERDA (x = -HX), bisagra arriba
private const val VENT_Z = 0.26f
private const val VENT_ARRIBA = 0.36f
private const val VENT_ABAJO = -0.06f

// Ventilador en la cara DERECHA (x = +HX)
private const val VENTILADOR_Y = 0.08f
private const val VENTILADOR_R = 0.2f

private val PI_F = PI.toFloat()

// ─────────────────────────────────────────────────────────────
//  Matemática 3D mínima
// ─────────────────────────────────────────────────────────────
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun times(k: Float) = Vec3(x * k, y * k, z * k)
}

private class Cara(val puntos: List<Vec3>, val normal: Vec3)

/** Gira el mundo (yaw sobre Y, luego pitch sobre X) y lo proyecta a la pantalla con algo de perspectiva. */
class Camara(
    yawGrados: Float,
    pitchGrados: Float,
    private val escala: Float,      // píxeles por unidad
    private val centro: Offset,
    private val distancia: Float = 7f
) {
    private val cy = cos(yawGrados * PI_F / 180f)
    private val sy = sin(yawGrados * PI_F / 180f)
    private val cp = cos(pitchGrados * PI_F / 180f)
    private val sp = sin(pitchGrados * PI_F / 180f)

    fun rotar(p: Vec3): Vec3 {
        val x1 = p.x * cy + p.z * sy
        val z1 = -p.x * sy + p.z * cy
        val y2 = p.y * cp - z1 * sp
        val z2 = p.y * sp + z1 * cp
        return Vec3(x1, y2, z2)          // z2 > 0 = más cerca de la cámara
    }

    fun proyectar(p: Vec3): Offset {
        val r = rotar(p)
        val f = distancia / (distancia - r.z)
        return Offset(centro.x + r.x * escala * f, centro.y - r.y * escala * f)
    }

    fun escalaEn(p: Vec3) = escala * distancia / (distancia - rotar(p).z)
    fun profundidad(p: Vec3) = rotar(p).z
    fun mira(normal: Vec3) = rotar(normal).z > 0f

    /** Luz que viene de arriba a la izquierda: 0 = sombra, 1 = iluminado. */
    fun luz(normal: Vec3): Float {
        val n = rotar(normal)
        return ((-0.35f * n.x + 0.8f * n.y + 0.5f * n.z) * 0.5f + 0.5f).coerceIn(0f, 1f)
    }

    fun camino(pts: List<Vec3>, cerrar: Boolean = true) = Path().apply {
        pts.forEachIndexed { i, p ->
            val o = proyectar(p)
            if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
        }
        if (cerrar) close()
    }
}

private fun prisma(x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float) = listOf(
    Cara(listOf(Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x0, y1, z1)), Vec3(0f, 0f, 1f)),   // frente
    Cara(listOf(Vec3(x1, y0, z0), Vec3(x0, y0, z0), Vec3(x0, y1, z0), Vec3(x1, y1, z0)), Vec3(0f, 0f, -1f)),  // atrás
    Cara(listOf(Vec3(x0, y0, z0), Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x0, y1, z0)), Vec3(-1f, 0f, 0f)),  // izquierda
    Cara(listOf(Vec3(x1, y0, z1), Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1)), Vec3(1f, 0f, 0f)),   // derecha
    Cara(listOf(Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0), Vec3(x0, y1, z0)), Vec3(0f, 1f, 0f)),   // tapa
    Cara(listOf(Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y0, z1), Vec3(x0, y0, z1)), Vec3(0f, -1f, 0f)),  // base
)

private fun bezier(p0: Vec3, p1: Vec3, p2: Vec3, t: Float): Vec3 {
    val u = 1f - t
    return p0 * (u * u) + p1 * (2f * u * t) + p2 * (t * t)
}

private fun mezcla(a: Float, b: Float, f: Float) = a + (b - a) * f

// ─────────────────────────────────────────────────────────────
//  Datos que necesita la escena y puntos que devuelve para las etiquetas
// ─────────────────────────────────────────────────────────────
data class DatosEscena(
    val humedades: List<Float>,
    val temperatura: Float,
    val velocidadVentilador: Float,   // 0..1 (animado)
    val anguloAspas: Float,           // grados
    val anguloVentana: Float,         // grados (animado)
    val tiempo: Float                 // segundos
)

data class Anclas(
    val plantas: List<Offset>,
    val temperatura: Offset,
    val ventilador: Offset,
    val ventana: Offset,
    val techo: Float,         // y en pantalla del punto más alto de la caja
    val base: Float           // y en pantalla del punto más bajo de la caja
)

// ─────────────────────────────────────────────────────────────
//  Escena completa (orden de dibujo = de atrás hacia adelante)
// ─────────────────────────────────────────────────────────────
fun DrawScope.dibujarEscena(cam: Camara, d: DatosEscena, p: Paleta): Anclas {
    val calor = ((d.temperatura - 18f) / 14f).coerceIn(0f, 1f)
    val tinte = lerp(p.tinteCaja, Color(0xFFFF8A65), calor * 0.35f)   // el plástico se "calienta" de color
    val caja = prisma(-HX, HX, -HY, HY, -HZ, HZ)

    // Sombra en el piso
    drawPath(
        cam.camino(listOf(
            Vec3(-HX + 0.1f, -HY, -HZ - 0.04f), Vec3(HX + 0.14f, -HY, -HZ - 0.04f),
            Vec3(HX + 0.14f, -HY, HZ + 0.08f), Vec3(-HX + 0.1f, -HY, HZ + 0.08f)
        )),
        Color.Black.copy(alpha = 0.18f)
    )

    // 1. Caras traseras (se ven a través del plástico)
    caja.filter { !cam.mira(it.normal) }.forEach { dibujarCara(cam, it, tinte, p, frontal = false) }

    // 2. Cosas pegadas a las paredes traseras
    val anclaTemp = dibujarTermometro(cam, calor)
    val anclaVentilador = dibujarVentilador(cam, d.anguloAspas, d.velocidadVentilador, p)

    // 3. Tierra y plantas (una planta por sensor de humedad)
    val posiciones = posicionesPlantas(d.humedades.size)
    dibujarSuelo(cam, d.humedades, posiciones)
    val alto = altoPlantas(d.humedades.size)
    posiciones.indices
        .sortedBy { cam.profundidad(posiciones[it]) }           // las de atrás primero
        .forEach { i ->
            dibujarPlanta(cam, posiciones[i], alto, d.tiempo, d.velocidadVentilador, i * 1.7f, salud(d.humedades[i]))
        }
    val anclasPlantas = posiciones.map { cam.proyectar(it + Vec3(0f, 0.005f, 0.09f)) }   // el sensor, delante de la planta

    // 4. Corriente de aire
    val apertura = (d.anguloVentana / 60f).coerceIn(0f, 1f)
    if (d.velocidadVentilador > 0.01f) dibujarAire(cam, d.tiempo, d.velocidadVentilador, apertura, p)

    // 5. Caras delanteras (plástico) + reflejo
    caja.filter { cam.mira(it.normal) }.forEach { dibujarCara(cam, it, tinte, p, frontal = true) }
    drawPath(
        cam.camino(listOf(
            Vec3(-HX + 0.30f, HY, HZ), Vec3(-HX + 0.48f, HY, HZ),
            Vec3(-HX + 0.22f, -HY, HZ), Vec3(-HX + 0.06f, -HY, HZ)
        )),
        Color.White.copy(alpha = 0.10f)
    )

    // 6. Ventana de la cara izquierda (mira hacia nosotros)
    val anclaVentana = dibujarVentana(cam, d.anguloVentana, p)

    val techo = caja.flatMap { it.puntos }.minOf { cam.proyectar(it).y }
    val base = caja.flatMap { it.puntos }.maxOf { cam.proyectar(it).y }
    return Anclas(anclasPlantas, anclaTemp, anclaVentilador, anclaVentana, techo, base)
}

private fun DrawScope.dibujarCara(cam: Camara, c: Cara, tinte: Color, p: Paleta, frontal: Boolean) {
    val camino = cam.camino(c.puntos)
    val color = lerp(tinte, Color.White, cam.luz(c.normal) * 0.5f)
    drawPath(camino, color.copy(alpha = if (frontal) 0.22f else 0.14f))
    drawPath(
        camino, p.bordeCaja.copy(alpha = if (frontal) 0.95f else 0.4f),
        style = Stroke(if (frontal) 2.dp.toPx() else 1.dp.toPx(), join = StrokeJoin.Round)
    )
}

// ─────────────────────────────────────────────────────────────
//  Tierra
// ─────────────────────────────────────────────────────────────
private fun colorTierra(h: Float) = lerp(Color(0xFFC8A27A), Color(0xFF3E2723), (h / 100f).coerceIn(0f, 1f))

private fun DrawScope.dibujarSuelo(cam: Camara, humedades: List<Float>, pos: List<Vec3>) {
    val media = if (humedades.isEmpty()) 50f else humedades.average().toFloat()
    val base = colorTierra(media)
    prisma(-HX + 0.02f, HX - 0.02f, -HY + 0.004f, -HY + SUELO, -HZ + 0.02f, HZ - 0.02f)
        .filter { cam.mira(it.normal) }
        .forEach { c -> drawPath(cam.camino(c.puntos), lerp(base, Color.Black, (1f - cam.luz(c.normal)) * 0.45f)) }

    // Mancha de humedad propia de cada sensor alrededor de su planta
    if (pos.isEmpty()) return
    val r = min(0.15f, ANCHO / (min(pos.size, 4) * 2.6f))
    pos.forEachIndexed { i, c ->
        val circulo = List(28) { k ->
            val a = 2f * PI_F * k / 28
            Vec3(c.x + r * cos(a), c.y + 0.002f, c.z + r * 0.8f * sin(a))
        }
        drawPath(cam.camino(circulo), colorTierra(humedades[i]).copy(alpha = 0.9f))
    }
}

// ─────────────────────────────────────────────────────────────
//  Plantas
// ─────────────────────────────────────────────────────────────
private fun filas(n: Int) = ceil(n / 4f).toInt().coerceAtLeast(1)

private fun posicionesPlantas(n: Int): List<Vec3> {
    if (n <= 0) return emptyList()
    val filas = filas(n)
    val porFila = ceil(n / filas.toFloat()).toInt()
    val y = -HY + SUELO
    return List(n) { i ->
        val f = i / porFila
        val col = i % porFila
        val enEstaFila = min(porFila, n - f * porFila)
        val x = -HX + ANCHO * (col + 1) / (enEstaFila + 1)
        val z = if (filas == 1) 0f else -HZ * 0.55f + FONDO * 0.55f * f / (filas - 1)
        Vec3(x, y, z)
    }
}

/** Con más sensores (más plantas) cada planta es un poco más chica. */
private fun altoPlantas(n: Int) = (ALTO - SUELO) * 0.72f / (1f + 0.18f * (filas(n) - 1))

private fun salud(h: Float) = ((h - 20f) / 40f).coerceIn(0f, 1f)

private fun DrawScope.dibujarPlanta(
    cam: Camara, base: Vec3, h: Float, t: Float, viento: Float, fase: Float, salud: Float
) {
    // Reposo: balanceo leve. Con ventilador: más rápido, más amplio y empujada hacia la ventana (-x)
    val balanceo = sin(t * (1.2f + 4f * viento) + fase) * (h * 0.03f + h * 0.07f * viento) - h * 0.16f * viento
    val altura = h * (0.8f + 0.2f * salud)
    val tope = base + Vec3(balanceo, altura, 0f)
    val control = base + Vec3(balanceo * 0.2f, altura * 0.5f, 0f)
    val esc = cam.escalaEn(base)

    val tallo = Path()
    for (s in 0..12) {
        val o = cam.proyectar(bezier(base, control, tope, s / 12f))
        if (s == 0) tallo.moveTo(o.x, o.y) else tallo.lineTo(o.x, o.y)
    }
    drawPath(tallo, Color(0xFF2E7D32), style = Stroke(h * esc * 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    val colorHoja = lerp(Color(0xFFBFA730), Color(0xFF43A047), salud)   // amarillenta si está seca
    val caida = (1f - salud) * 55f                                       // hojas caídas si falta agua
    val hojas = 5
    for (k in 1..hojas) {
        val f = k / (hojas + 1f)
        val pt = cam.proyectar(bezier(base, control, tope, f))
        val aleteo = sin(t * (2f + 7f * viento) + fase + k) * (4f + 18f * viento) - 14f * viento
        val ang = if (k % 2 == 0) -40f + caida + aleteo else -140f - caida + aleteo
        dibujarHoja(pt, ang, h * esc * 0.24f * (1f - f * 0.45f), colorHoja)
    }
    val brote = sin(t * 3f + fase) * 10f - 15f * viento
    val pTope = cam.proyectar(tope)
    dibujarHoja(pTope, -70f + brote, h * esc * 0.13f, colorHoja)
    dibujarHoja(pTope, -110f + brote, h * esc * 0.13f, colorHoja)
}

private fun DrawScope.dibujarHoja(p: Offset, anguloGrados: Float, tam: Float, color: Color) {
    rotate(anguloGrados, pivot = p) {
        val hoja = Path().apply {
            moveTo(p.x, p.y)
            quadraticBezierTo(p.x + tam * 0.5f, p.y - tam * 0.38f, p.x + tam, p.y)
            quadraticBezierTo(p.x + tam * 0.5f, p.y + tam * 0.38f, p.x, p.y)
            close()
        }
        drawPath(hoja, color)
        drawLine(Color.Black.copy(alpha = 0.18f), p, Offset(p.x + tam * 0.9f, p.y), 1.5f)
    }
}

// ─────────────────────────────────────────────────────────────
//  Termómetro (pared trasera)
// ─────────────────────────────────────────────────────────────
private fun DrawScope.dibujarTermometro(cam: Camara, calor: Float): Offset {
    val arriba = cam.proyectar(Vec3(-0.1f, 0.38f, -HZ + 0.01f))
    val bulbo = cam.proyectar(Vec3(-0.1f, 0.16f, -HZ + 0.01f))
    drawLine(Color.White.copy(alpha = 0.85f), arriba, bulbo, 6.dp.toPx(), StrokeCap.Round)
    val col = lerp(Color(0xFF42A5F5), Color(0xFFE53935), calor)
    val nivel = 0.2f + 0.7f * calor
    drawLine(col, bulbo, Offset(mezcla(bulbo.x, arriba.x, nivel), mezcla(bulbo.y, arriba.y, nivel)), 3.dp.toPx(), StrokeCap.Round)
    drawCircle(col, 5.dp.toPx(), bulbo)
    return bulbo
}

// ─────────────────────────────────────────────────────────────
//  Ventilador (cara derecha). Se dibuja en 3D, así sale ovalado por la perspectiva.
// ─────────────────────────────────────────────────────────────
private fun DrawScope.dibujarVentilador(cam: Camara, angulo: Float, v: Float, p: Paleta): Offset {
    val centro = Vec3(HX, VENTILADOR_Y, 0f)
    val r = VENTILADOR_R
    fun punto(u: Float, w: Float) = Vec3(HX + 0.012f, VENTILADOR_Y + w, u)      // u = eje z, w = eje y
    fun circulo(radio: Float, n: Int = 40) = List(n) { i ->
        val a = 2f * PI_F * i / n
        punto(radio * cos(a), radio * sin(a))
    }

    drawPath(cam.camino(circulo(r * 1.18f)), Color(0xFF37474F))
    drawPath(cam.camino(circulo(r * 1.18f)), Color(0xFF90A4AE), style = Stroke(3.dp.toPx()))
    drawPath(cam.camino(circulo(r)), Color.White.copy(alpha = 0.12f * v))   // disco de "desenfoque" al girar

    val rad = angulo * PI_F / 180f
    repeat(4) { k ->
        val a = rad + k * PI_F / 2f
        val ca = cos(a); val sa = sin(a)
        fun rot(largo: Float, lado: Float) = punto(largo * ca - lado * sa, largo * sa + lado * ca)
        val pts = ArrayList<Vec3>()
        for (s in 0..10) pts += rot(r * s / 10f, r * 0.30f * sin(PI_F * s / 10f))
        for (s in 10 downTo 0) pts += rot(r * s / 10f, -r * 0.10f * sin(PI_F * s / 10f))
        drawPath(cam.camino(pts), Color(0xFFCFD8DC).copy(alpha = 1f - 0.45f * v))
    }
    drawPath(cam.camino(circulo(r * 0.18f, 20)), Color(0xFF263238))

    // LED de estado (rojo apagado / verde encendido)
    drawCircle(lerp(Color(0xFFE53935), p.verde, v), 4.dp.toPx(), cam.proyectar(punto(0f, -r * 1.4f)))
    return cam.proyectar(centro)
}

// ─────────────────────────────────────────────────────────────
//  Corriente de aire: sale del ventilador (derecha) y viaja hacia la ventana (izquierda)
// ─────────────────────────────────────────────────────────────
private fun DrawScope.dibujarAire(cam: Camara, t: Float, v: Float, apertura: Float, p: Paleta) {
    val yVentana = (VENT_ARRIBA + VENT_ABAJO) / 2f
    val largo = ANCHO + 0.35f * apertura           // con la ventana abierta el aire sale de la caja
    val trazo = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    for (i in 0 until 8) {
        val a = i * PI_F / 4f
        val u0 = VENTILADOR_R * 0.65f * cos(a)
        val w0 = VENTILADOR_R * 0.65f * sin(a)
        repeat(3) { j ->
            val prog = (t * (0.30f + 0.4f * v) + j / 3f + i * 0.137f) % 1f
            val pts = (0..12).map { s ->
                val q = (prog - 0.16f + 0.16f * s / 12f).coerceAtLeast(0f)
                var y = VENTILADOR_Y + w0 * (1f + 0.5f * q) + sin(q * 9f + t * 4f + i) * 0.025f
                var z = u0 * (1f + 0.6f * q) + cos(q * 7f + t * 3f + i) * 0.025f
                val atraccion = apertura * q * q            // si la ventana está abierta el aire se dirige a ella
                y = mezcla(y, yVentana, atraccion)
                z = mezcla(z, z * 0.5f, atraccion)
                Vec3(HX - q * largo, y, z)
            }
            val alpha = (v * sin(prog * PI_F) * 0.8f).coerceIn(0f, 1f)
            drawPath(cam.camino(pts, cerrar = false), p.bordeCaja.copy(alpha = alpha), style = trazo)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Ventana (cara izquierda). El ángulo viene del ESP32.
// ─────────────────────────────────────────────────────────────
private fun DrawScope.dibujarVentana(cam: Camara, anguloGrados: Float, p: Paleta): Offset {
    val alto = VENT_ARRIBA - VENT_ABAJO
    val a = anguloGrados.coerceIn(0f, 90f) * PI_F / 180f
    val x = -HX - 0.004f
    val xLibre = x - alto * sin(a)                 // el borde de abajo sale hacia afuera
    val yLibre = VENT_ARRIBA - alto * cos(a)

    val hueco = listOf(
        Vec3(-HX, VENT_ABAJO, -VENT_Z), Vec3(-HX, VENT_ABAJO, VENT_Z),
        Vec3(-HX, VENT_ARRIBA, VENT_Z), Vec3(-HX, VENT_ARRIBA, -VENT_Z)
    )
    drawPath(cam.camino(hueco), Color.Black.copy(alpha = 0.3f * (anguloGrados / 20f).coerceIn(0f, 1f)))
    drawPath(cam.camino(hueco), p.bordeCaja.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx()))

    val bisagra1 = Vec3(x, VENT_ARRIBA, -VENT_Z)
    val bisagra2 = Vec3(x, VENT_ARRIBA, VENT_Z)
    val panel = listOf(bisagra1, bisagra2, Vec3(xLibre, yLibre, VENT_Z), Vec3(xLibre, yLibre, -VENT_Z))
    drawPath(cam.camino(panel), lerp(p.tinteCaja, Color.White, 0.4f).copy(alpha = 0.45f))
    drawPath(cam.camino(panel), p.bordeCaja, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
    drawLine(p.bordeCaja, cam.proyectar(bisagra1), cam.proyectar(bisagra2), 4.dp.toPx(), StrokeCap.Round)

    return cam.proyectar(Vec3((x + xLibre) / 2f, (VENT_ARRIBA + yLibre) / 2f, 0f))
}
