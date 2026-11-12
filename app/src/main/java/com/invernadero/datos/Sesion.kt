package com.invernadero.datos

import com.invernadero.nube.Reloj

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sesión en memoria (nunca se guarda en disco). Expira tras 10 minutos sin tocar la pantalla
 * y, si es un invitado, también cuando vence su pase de visita.
 */
class Sesion {
    companion object { const val INACTIVIDAD_MAX_MS = 10 * 60_000L }

    private val _usuario = MutableStateFlow<Usuario?>(null)
    val usuario: StateFlow<Usuario?> = _usuario.asStateFlow()

    @Volatile private var ultimaActividad = 0L

    /** true si entró con "Recordarme": no se cierra por inactividad (sí al cerrar sesión). */
    @Volatile var recordada = false
        private set

    fun iniciar(u: Usuario, recordar: Boolean = false) {
        ultimaActividad = System.currentTimeMillis()
        recordada = recordar && !u.invitado
        _usuario.value = u
    }

    fun cerrar() { _usuario.value = null }

    fun tocar() { ultimaActividad = System.currentTimeMillis() }

    fun expirada(): Boolean {
        val u = _usuario.value ?: return false
        val ahora = System.currentTimeMillis()
        return (!recordada && ahora - ultimaActividad > INACTIVIDAD_MAX_MS) || (u.invitado && Reloj.ahora() > u.vence)
    }
}
