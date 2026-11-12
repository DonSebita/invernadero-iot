package com.invernadero.datos

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sesión en memoria (nunca se guarda en disco). Expira tras 10 minutos sin tocar la pantalla. */
class Sesion {
    companion object { const val INACTIVIDAD_MAX_MS = 10 * 60_000L }

    private val _usuario = MutableStateFlow<Usuario?>(null)
    val usuario: StateFlow<Usuario?> = _usuario.asStateFlow()

    @Volatile private var ultimaActividad = 0L

    fun iniciar(u: Usuario) {
        ultimaActividad = System.currentTimeMillis()
        _usuario.value = u
    }

    fun cerrar() { _usuario.value = null }

    fun tocar() { ultimaActividad = System.currentTimeMillis() }

    fun expirada() = _usuario.value != null && System.currentTimeMillis() - ultimaActividad > INACTIVIDAD_MAX_MS
}
