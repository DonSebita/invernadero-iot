package com.invernadero.seguridad

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Las contraseñas NUNCA se guardan: se guarda un hash PBKDF2-HMAC-SHA256 con sal aleatoria
 * y 120.000 iteraciones (recomendación OWASP / NIST SP 800-132).
 */
object Contrasenas {
    const val ITERACIONES = 120_000
    private const val LARGO_BITS = 256
    private val azar = SecureRandom()

    fun nuevaSal(): ByteArray = ByteArray(16).also { azar.nextBytes(it) }

    fun hash(clave: CharArray, sal: ByteArray, iteraciones: Int = ITERACIONES): ByteArray {
        val spec = PBEKeySpec(clave, sal, iteraciones, LARGO_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()          // se borra la contraseña de la memoria
        }
    }

    /** Comparación en tiempo constante (evita ataques de tiempo). */
    fun verificar(clave: CharArray, sal: ByteArray, esperado: ByteArray, iteraciones: Int): Boolean =
        MessageDigest.isEqual(hash(clave, sal, iteraciones), esperado)

    /** Política de credenciales. Devuelve el problema o null si está bien. */
    fun validarPolitica(usuario: String, clave: String): String? = when {
        usuario.length !in 3..30 || !usuario.matches(Regex("[A-Za-z0-9._-]+")) ->
            "El usuario debe tener de 3 a 30 caracteres (letras, números, punto, guion)"
        clave.length < 8 -> "La contraseña debe tener al menos 8 caracteres"
        !clave.any { it.isLetter() } || !clave.any { it.isDigit() } -> "La contraseña debe combinar letras y números"
        clave.contains(usuario, ignoreCase = true) -> "La contraseña no puede contener el nombre de usuario"
        else -> null
    }
}
