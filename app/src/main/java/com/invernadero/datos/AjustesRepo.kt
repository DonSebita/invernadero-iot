package com.invernadero.datos

import com.invernadero.seguridad.CajaFuerte

/** En este orden aparecen en Ajustes. NUBE no se ofrece: es el modo interno de los invitados en su propio teléfono. */
enum class ModoConexion(val etiqueta: String, val ayuda: String) {
    WIFI("WiFi + nube", "En la red del invernadero ves todo al instante (cada 2 s). Lejos de ella, lo que el ESP32 sube a Firebase (cada 2 min)"),
    BLUETOOTH("Bluetooth → ESP32", "Directo y sin router (corto alcance, ~10 m). Si se corta, también muestra la nube"),
    REMOTO("Otro teléfono (central)", "Recibe los datos de otro teléfono que ya está conectado al ESP32, por la misma red WiFi"),
    SIMULADOR("Simulador (demostración)", "Datos de prueba, sin ESP32. Es el modo inicial hasta que configures tu ESP32"),
    NUBE("Desde la nube", "Lo último que subió el ESP32 a Firebase (cada 2 min). Solo mirar");

    /** Modos que el usuario puede elegir en Ajustes. */
    companion object { val elegibles get() = entries.filter { it != NUBE } }
}

data class ConfigConexion(
    val modo: ModoConexion = ModoConexion.SIMULADOR,
    val ipEsp32: String = "192.168.1.200",
    val nombreBt: String = "Invernadero",
    val ipCentral: String = "",
    val compartir: Boolean = false,        // este teléfono actúa como central para otros
    val secreto: String = "",              // clave compartida con el ESP32 y los otros teléfonos
    val sensoresSimulados: Int = 3
)

/** Todos los ajustes se guardan cifrados (Android Keystore). */
class AjustesRepo(private val caja: CajaFuerte) {

    fun cargar(): ConfigConexion {
        val d = ConfigConexion()
        return ConfigConexion(
            modo = runCatching { ModoConexion.valueOf(caja.leer("modo") ?: "") }.getOrDefault(d.modo),
            ipEsp32 = caja.leer("ipEsp32") ?: d.ipEsp32,
            nombreBt = caja.leer("nombreBt") ?: d.nombreBt,
            ipCentral = caja.leer("ipCentral") ?: d.ipCentral,
            compartir = caja.leer("compartir") == "1",
            secreto = caja.leer("secreto") ?: d.secreto,
            sensoresSimulados = caja.leer("sensoresSimulados")?.toIntOrNull() ?: d.sensoresSimulados
        )
    }

    /** false = nunca se guardó la conexión en este teléfono (p. ej. un segundo teléfono recién instalado). */
    val configurado: Boolean get() = caja.leer("modo") != null

    fun guardar(c: ConfigConexion) {
        caja.guardar("modo", c.modo.name)
        caja.guardar("ipEsp32", c.ipEsp32.trim())
        caja.guardar("nombreBt", c.nombreBt.trim())
        caja.guardar("ipCentral", c.ipCentral.trim())
        caja.guardar("compartir", if (c.compartir) "1" else "0")
        caja.guardar("secreto", c.secreto)
        caja.guardar("sensoresSimulados", c.sensoresSimulados.toString())
    }

    var modoTarde: Boolean
        get() = caja.leer("modoTarde") == "1"
        set(v) = caja.guardar("modoTarde", if (v) "1" else "0")

    /**
     * Dónde dejó el usuario cada tarjeta de Monitoreo: centro en fracción del ancho/alto de la escena
     * (así sirve en cualquier pantalla). Formato guardado: "clave:x,y;clave:x,y".
     */
    var posicionesTarjetas: Map<String, Pair<Float, Float>>
        get() = caja.leer("posicionesTarjetas").orEmpty().split(";").mapNotNull { item ->
            val (clave, xy) = item.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            val (x, y) = xy.split(",").mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 2 } ?: return@mapNotNull null
            clave to (x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f))
        }.toMap()
        set(v) = caja.guardar("posicionesTarjetas", v.entries.joinToString(";") { (k, p) -> "$k:${p.first},${p.second}" })

    /** Veces que se mostró en Monitoreo la pista "mantén presionada una tarjeta para moverla". */
    var pistaTarjetasVista: Int
        get() = caja.leer("pistaTarjetasVista")?.toIntOrNull() ?: 0
        set(v) = caja.guardar("pistaTarjetasVista", v.toString())

    /** Veces que se abrió Monitoreo (para repetir la pista de vez en cuando). */
    var aperturasMonitoreo: Int
        get() = caja.leer("aperturasMonitoreo")?.toIntOrNull() ?: 0
        set(v) = caja.guardar("aperturasMonitoreo", v.toString())
}
