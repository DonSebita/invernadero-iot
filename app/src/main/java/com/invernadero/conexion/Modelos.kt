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
 *  "modoVentana":"AUTO","nonce":"9f2c..."}
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
    val nonce: String = "",                      // número de un solo uso para firmar el próximo comando
    val latenciaMs: Long = 0,
    val recibidoEn: Long = System.currentTimeMillis()
)

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
}

/** Lista blanca de comandos (los mismos de tu sketch + V y W para los sliders). */
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

    private val formato = Regex("^([ACMX0+-]|V\\d{1,3}|W\\d{1,2})$")

    fun esValido(c: String): Boolean {
        if (!formato.matches(c)) return false
        val n = c.drop(1).toIntOrNull()
        return when (c[0]) {
            'V' -> n != null && n in 0..255
            'W' -> n != null && n in 0..90
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
        else -> "Comando desconocido"
    }
}
