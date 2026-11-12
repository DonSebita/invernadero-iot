package com.invernadero.conexion

import com.invernadero.datos.Rol
import com.invernadero.nube.Nube
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Modo "Desde la nube": muestra el último estado que subió el ESP32 a Firebase (cada 2 min).
 * Sirve desde cualquier lugar con Internet, pero es SOLO PARA MIRAR: las órdenes al ESP32 van siempre
 * por la red local (WiFi o Bluetooth), firmadas con HMAC, nunca a través de la nube.
 */
class FuenteNube(private val nube: Nube, private val invernadero: String?) : FuenteEsp32 {
    override val nombre = "Nube · Firebase"

    override fun lecturas(): Flow<EventoFuente> {
        val inv = invernadero ?: return flowOf(EventoFuente.Falla("Inicia sesión para ver tu invernadero en la nube"))
        return flow {
            while (true) {        // si la nube corta la escucha (p. ej. sin permiso), se reintenta en 30 s
                emitAll(
                    nube.estado(inv)
                        .map { l -> if (l == null) EventoFuente.Falla("El ESP32 aún no sube datos a la nube") else EventoFuente.Dato(l) }
                        .catch { e -> emit(EventoFuente.Falla(e.message ?: "Sin acceso a la nube")) }
                )
                delay(30_000)
            }
        }
    }

    override suspend fun enviar(codigo: String, usuario: String, rol: Rol): Boolean = false
}
