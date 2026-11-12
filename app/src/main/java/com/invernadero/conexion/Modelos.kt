package com.invernadero.conexion

import com.invernadero.datos.Rol
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Una lectura completa del invernadero.
 * JSON del ESP32 (GET /datos):
 * {"humedades":[59.3],"estadosSuelo":["Regada (Óptima)"],"temperatura":24.1,"humedadAmbiente":61.0,
 *  "ventilador":true,"velocidadVentilador":200,"anguloVentana":52,"modoVentilador":"AUTO",
 *  "modoVentana":"AUTO","parametros":{"ventiladorSobre":25,"ventanaDesde":20,"ventanaHasta":27,"humedadMaxima":85},
 *  "nonce":"9f2c..."}
 */
data class LecturaEsp32(
    val humedades: List<Float>,
    val temperatura: Float,
    val ventiladorEncendido: Boolean,
    val anguloVentana: Float,                    // 0 = cerrada … 90 = abierta
    val velocidadVentilador: Float = if (ventiladorEncendido) 1f else 0f,   // 0..1 (PWM / 255)
    val humedadAmbiente: Float? = null,
    val estadosSuelo: List<String> = emptyList(),
    val modoVentilador: String = "AUTO",
    val modoVentana: String = "AUTO",
    val parametros: ParametrosControl? = null,   // null = el ESP32 (sketch antiguo) no informa sus reglas
    val nonce: String = "",                      // número de un solo uso para firmar el próximo comando
    val latenciaMs: Long = 0,
    val recibidoEn: Long = System.currentTimeMillis()
)

/**
 * Reglas del modo automático que decide el cliente (se guardan en el ESP32).
 * Rangos iguales a los que acepta el sketch.
 */
data class ParametrosControl(
    val ventiladorSobre: Int = 25,   // °C: sobre esto el ventilador se enciende (a toda potencia en +10 °C)
    val ventanaDesde: Int = 20,      // °C: bajo esto la ventana queda cerrada
    val ventanaHasta: Int = 27,      // °C: sobre esto la ventana queda abierta del todo
    val humedadMaxima: Int = 85,     // %: sobre esto se ventila aunque no haga calor (100 = desactivado)
) {
    val ventilaPorHumedad get() = humedadMaxima < 100

    companion object {
        val FABRICA = ParametrosControl()
        val RANGO_VENTILADOR = 15..40
        val RANGO_DESDE = 5..40
        val RANGO_HASTA = 10..45
        val RANGO_HUMEDAD = 40..99
        const val SEPARACION_MINIMA = 2
    }
}

sealed interface EventoFuente {
    data class Dato(val lectura: LecturaEsp32) : EventoFuente
    data class Falla(val mensaje: String) : EventoFuente
}

/** Cualquier origen de datos: Simulador, WiFi, Bluetooth u otro teléfono. */
interface FuenteEsp32 {
    val nombre: String
    /** Flujo infinito: se reconecta solo si se cae. */
    fun lecturas(): Flow<EventoFuente>
    suspend fun enviar(codigo: String, usuario: String, rol: Rol): Boolean
}

fun parsearLectura(json: String): LecturaEsp32 {
    val o = JSONObject(json)
    val arr = o.getJSONArray("humedades")
    val estados = o.optJSONArray("estadosSuelo")
    val encendido = o.getBoolean("ventilador")
    return LecturaEsp32(
        humedades = List(arr.length()) { arr.getDouble(it).toFloat() },
        temperatura = o.getDouble("temperatura").toFloat(),
        ventiladorEncendido = encendido,
        anguloVentana = o.optDouble("anguloVentana", 0.0).toFloat(),
        velocidadVentilador = if (o.has("velocidadVentilador")) o.getInt("velocidadVentilador") / 255f
                              else if (encendido) 1f else 0f,
        humedadAmbiente = if (o.has("humedadAmbiente")) o.getDouble("humedadAmbiente").toFloat() else null,
        estadosSuelo = if (estados == null) emptyList() else List(estados.length()) { estados.getString(it) },
        modoVentilador = o.optString("modoVentilador", "AUTO"),
        modoVentana = o.optString("modoVentana", "AUTO"),
        parametros = o.optJSONObject("parametros")?.let { p ->
            ParametrosControl(
                ventiladorSobre = p.optInt("ventiladorSobre", 25),
                ventanaDesde = p.optInt("ventanaDesde", 20),
                ventanaHasta = p.optInt("ventanaHasta", 27),
                humedadMaxima = p.optInt("humedadMaxima", 85)
            )
        },
        nonce = o.optString("nonce", "")
    )
}

fun LecturaEsp32.aJson(): JSONObject = JSONObject().apply {
    put("humedades", JSONArray().apply { humedades.forEach { put(it.toDouble()) } })
    put("estadosSuelo", JSONArray(estadosSuelo))
    put("temperatura", temperatura.toDouble())
    humedadAmbiente?.let { put("humedadAmbiente", it.toDouble()) }
    put("ventilador", ventiladorEncendido)
    put("velocidadVentilador", (velocidadVentilador * 255).roundToInt())
    put("anguloVentana", anguloVentana.toDouble())
    put("modoVentilador", modoVentilador)
    put("modoVentana", modoVentana)
    parametros?.let { p ->
        put("parametros", JSONObject().apply {
            put("ventiladorSobre", p.ventiladorSobre)
            put("ventanaDesde", p.ventanaDesde)
            put("ventanaHasta", p.ventanaHasta)
            put("humedadMaxima", p.humedadMaxima)
        })
    }
}

/** Lista blanca de comandos (los mismos de tu sketch + V y W para los sliders + reglas T/L/H/U). */
object Comandos {
    const val ABRIR = "A"
    const val CERRAR = "C"
    const val SUBIR = "+"
    const val BAJAR = "-"
    const val MAXIMO = "M"
    const val APAGAR = "0"
    const val AUTOMATICO = "X"
    fun velocidad(pwm: Int) = "V${pwm.coerceIn(0, 255)}"
    fun ventana(apertura: Int) = "W${apertura.coerceIn(0, 90)}"
    fun ventiladorSobre(c: Int) = "T$c"
    fun ventanaDesde(c: Int) = "L$c"
    fun ventanaHasta(c: Int) = "H$c"
    fun humedadMaxima(pct: Int) = "U$pct"

    private val formato = Regex("^([ACMX0+-]|V\\d{1,3}|W\\d{1,2}|[TLH]\\d{1,2}|U\\d{2,3})$")

    /**
     * Órdenes para pasar de [actual] a [nuevo], solo lo que cambió. La ventana se envía en el orden que
     * mantiene siempre "hasta ≥ desde + 2" (el ESP32 rechaza un paso intermedio inválido).
     */
    fun reglas(actual: ParametrosControl, nuevo: ParametrosControl): List<String> = buildList {
        if (nuevo.ventiladorSobre != actual.ventiladorSobre) add(ventiladorSobre(nuevo.ventiladorSobre))
        val desde = if (nuevo.ventanaDesde != actual.ventanaDesde) ventanaDesde(nuevo.ventanaDesde) else null
        val hasta = if (nuevo.ventanaHasta != actual.ventanaHasta) ventanaHasta(nuevo.ventanaHasta) else null
        if (nuevo.ventanaDesde <= actual.ventanaHasta - ParametrosControl.SEPARACION_MINIMA) {
            desde?.let { add(it) }; hasta?.let { add(it) }
        } else {
            hasta?.let { add(it) }; desde?.let { add(it) }
        }
        if (nuevo.humedadMaxima != actual.humedadMaxima) add(humedadMaxima(nuevo.humedadMaxima))
    }

    /** Cambia una regla del modo automático (no mueve nada en el momento). */
    fun esRegla(c: String) = c.isNotEmpty() && c[0] in "TLHU"

    fun esValido(c: String): Boolean {
        if (!formato.matches(c)) return false
        val n = c.drop(1).toIntOrNull()
        return when (c[0]) {
            'V' -> n != null && n in 0..255
            'W' -> n != null && n in 0..90
            'T' -> n != null && n in ParametrosControl.RANGO_VENTILADOR
            'L' -> n != null && n in ParametrosControl.RANGO_DESDE
            'H' -> n != null && n in ParametrosControl.RANGO_HASTA
            'U' -> n != null && (n in ParametrosControl.RANGO_HUMEDAD || n == 100)
            else -> c.length == 1
        }
    }

    fun descripcion(c: String): String = when {
        c == ABRIR -> "Abrir ventana"
        c == CERRAR -> "Cerrar ventana"
        c == SUBIR -> "Subir ventilador"
        c == BAJAR -> "Bajar ventilador"
        c == MAXIMO -> "Ventilador al máximo"
        c == APAGAR -> "Apagar ventilador"
        c == AUTOMATICO -> "Volver a automático"
        c.startsWith("V") -> "Ventilador PWM ${c.drop(1)}"
        c.startsWith("W") -> "Ventana a ${c.drop(1)}°"
        c.startsWith("T") -> "Regla: ventilador sobre ${c.drop(1)} °C"
        c.startsWith("L") -> "Regla: ventana empieza a abrir en ${c.drop(1)} °C"
        c.startsWith("H") -> "Regla: ventana abierta del todo en ${c.drop(1)} °C"
        c == "U100" -> "Regla: no ventilar por humedad"
        c.startsWith("U") -> "Regla: humedad del aire máxima ${c.drop(1)} %"
        else -> "Comando desconocido"
    }
}
