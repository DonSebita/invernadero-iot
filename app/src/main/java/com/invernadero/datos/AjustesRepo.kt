package com.invernadero.datos

import com.invernadero.seguridad.CajaFuerte

enum class ModoConexion(val etiqueta: String, val ayuda: String) {
    SIMULADOR("Simulador", "Datos de prueba, sin hardware"),
    WIFI("WiFi → ESP32", "Misma red WiFi que el ESP32 (más alcance, varios teléfonos)"),
    BLUETOOTH("Bluetooth → ESP32", "Directo y sin router (corto alcance, ~10 m)"),
    REMOTO("Otro teléfono (central)", "Recibe los datos desde el teléfono central por WiFi")
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
}
