package com.invernadero.datos

/** Control de acceso por roles (principio de mínimo privilegio). */
enum class Rol(val etiqueta: String) {
    ADMIN("Administrador"),      // reservado (no hay panel de administración en esta app)
    OPERADOR("Operador"),        // monitorear y activar
    VISOR("Solo lectura");       // solo monitorear

    val puedeActivar get() = this != VISOR
    val puedeAdministrar get() = this == ADMIN
}

/**
 * Quien está usando la app.
 *  - Cuenta: entra con correo y contraseña o con Google (Firebase Authentication). [uid] es su id en Firebase.
 *  - Invitado: entra con un pase de visita guardado en la nube; Firebase le da una identidad anónima
 *    temporal ([uid]) que se borra al terminar la visita.
 * [invernadero] es el id del invernadero en la Realtime Database (cada cuenta tiene el suyo).
 */
data class Usuario(
    val uid: String,
    val nombre: String,
    val rol: Rol,
    val correo: String? = null,
    val invernadero: String = "",
    val invitado: Boolean = false,
    val vence: Long = 0L,
    val anfitrion: String? = null,
    val paseHash: String? = null
)

/** Deja un nombre con espacios simples y sin espacios al borde. */
fun normalizarNombre(texto: String): String = texto.trim().replace(Regex("\\s+"), " ")
