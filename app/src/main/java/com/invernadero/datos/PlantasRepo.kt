package com.invernadero.datos

import com.invernadero.seguridad.CajaFuerte
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Plantas del invernadero (una por sensor de humedad de suelo del ESP32, en el mismo orden).
 * - Nombres que les pone el usuario (vacío = "Planta N").
 * - Plantas añadidas que todavía esperan su sensor (aparecen solas cuando el ESP32 lo detecta).
 * - Plantas quitadas: su sensor sigue conectado, pero no se muestran en Monitoreo.
 * Todo se guarda cifrado en este teléfono.
 */
class PlantasRepo(private val caja: CajaFuerte) {
    private val _nombres = MutableStateFlow(caja.leer(CLAVE)?.split(SEPARADOR) ?: emptyList())
    val nombres: StateFlow<List<String>> = _nombres

    private val _esperando = MutableStateFlow(caja.leer(CLAVE_ESPERANDO)?.toIntOrNull()?.coerceIn(0, MAX_PLANTAS) ?: 0)
    /** Cuántas plantas añadió el usuario que aún no tienen sensor conectado. */
    val esperando: StateFlow<Int> = _esperando

    private val _quitadas = MutableStateFlow(
        caja.leer(CLAVE_QUITADAS).orEmpty().split(",").mapNotNull { it.toIntOrNull() }.filter { it >= 0 }.toSet()
    )
    /** Índices de sensor que el usuario quitó de la vista. */
    val quitadas: StateFlow<Set<Int>> = _quitadas

    private var sensoresConocidos = caja.leer(CLAVE_SENSORES)?.toIntOrNull()

    /** Guarda el nombre (máx. 24 caracteres, sin saltos de línea). Devuelve el nombre guardado. */
    fun renombrar(indice: Int, nombre: String): String {
        val limpio = nombre.replace(Regex("[\\r\\n]"), " ").trim().take(MAX_LARGO)
        val lista = _nombres.value.toMutableList()
        while (lista.size <= indice) lista.add("")
        lista[indice] = limpio
        _nombres.value = lista
        caja.guardar(CLAVE, lista.joinToString(SEPARADOR))
        return limpio
    }

    /** Añade una planta que espera su sensor. [mostradas]: plantas que se ven ahora. Falso si ya se llegó al máximo. */
    fun anadir(mostradas: Int): Boolean {
        if (mostradas >= MAX_PLANTAS) return false
        guardarEsperando(_esperando.value + 1)
        return true
    }

    /**
     * Quita la planta [indice], que todavía no tenía sensor ([sensores] = sensores conectados).
     * Los nombres de las que esperan detrás de ella se corren un lugar.
     */
    fun quitarEsperando(indice: Int, sensores: Int) {
        val ultimo = sensores + _esperando.value - 1
        if (indice < sensores || indice > ultimo) return
        val lista = _nombres.value.toMutableList()
        while (lista.size <= ultimo) lista.add("")
        for (i in indice until ultimo) lista[i] = lista[i + 1]
        lista[ultimo] = ""
        _nombres.value = lista
        caja.guardar(CLAVE, lista.joinToString(SEPARADOR))
        guardarEsperando(_esperando.value - 1)
    }

    /** Deja de mostrar la planta del sensor [indice] (el sensor sigue conectado). */
    fun quitar(indice: Int) = guardarQuitadas(_quitadas.value + indice)

    /** Vuelve a mostrar la planta del sensor [indice]. */
    fun restaurar(indice: Int) = guardarQuitadas(_quitadas.value - indice)

    /**
     * Se llama con cada lectura: si el ESP32 detectó sensores nuevos, ocupan el lugar de las
     * plantas que esperaban sensor.
     */
    fun sincronizar(sensores: Int) {
        val antes = sensoresConocidos
        if (antes == sensores) return
        if (antes != null && sensores > antes) guardarEsperando(_esperando.value - (sensores - antes))
        sensoresConocidos = sensores
        caja.guardar(CLAVE_SENSORES, sensores.toString())
    }

    private fun guardarEsperando(n: Int) {
        _esperando.value = n.coerceIn(0, MAX_PLANTAS)
        caja.guardar(CLAVE_ESPERANDO, _esperando.value.toString())
    }

    private fun guardarQuitadas(s: Set<Int>) {
        _quitadas.value = s
        caja.guardar(CLAVE_QUITADAS, s.sorted().joinToString(","))
    }

    companion object {
        const val MAX_LARGO = 24
        const val MAX_PLANTAS = 12
        private const val CLAVE = "nombresPlantas"
        private const val CLAVE_ESPERANDO = "plantasEsperando"
        private const val CLAVE_QUITADAS = "plantasQuitadas"
        private const val CLAVE_SENSORES = "sensoresConocidos"
        private const val SEPARADOR = "\n"
    }
}

/** "Tomate cherry" si tiene nombre; si no, "Planta 2". */
fun nombrePlanta(nombres: List<String>, indice: Int): String =
    nombres.getOrNull(indice)?.takeIf { it.isNotBlank() } ?: "Planta ${indice + 1}"
