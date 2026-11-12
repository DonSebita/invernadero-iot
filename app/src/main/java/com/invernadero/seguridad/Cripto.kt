package com.invernadero.seguridad

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Criptografía de las comunicaciones.
 *  - ESP32 <-> teléfono: HMAC-SHA256 con la clave compartida (autenticidad + integridad).
 *  - Teléfono <-> teléfono: AES-256-GCM (confidencialidad + integridad) con una subclave derivada.
 */
object Cripto {
    private val azar = SecureRandom()

    fun hmac(clave: ByteArray, mensaje: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(clave, "HmacSHA256"))
        return mac.doFinal(mensaje)
    }

    fun hex(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    /** Mismo cálculo que hace el ESP32 (mbedtls) en el sketch. */
    fun hmacHex(secreto: String, mensaje: String) =
        hex(hmac(secreto.toByteArray(Charsets.UTF_8), mensaje.toByteArray(Charsets.UTF_8)))

    fun igualesSeguro(a: String, b: String) =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    /** Separación de claves: la clave entre teléfonos no es la misma que usa el ESP32. */
    fun claveEnlace(secreto: String): ByteArray =
        hmac(secreto.toByteArray(Charsets.UTF_8), "enlace-entre-moviles-v1".toByteArray(Charsets.UTF_8))

    fun cifrar(clave: ByteArray, texto: String): String {
        val iv = ByteArray(12).also { azar.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(clave, "AES"), GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + c.doFinal(texto.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    /** Devuelve null si el mensaje fue alterado o se cifró con otra clave. */
    fun descifrar(clave: ByteArray, b64: String): String? = try {
        val d = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(clave, "AES"), GCMParameterSpec(128, d, 0, 12))
        String(c.doFinal(d, 12, d.size - 12), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    fun idAleatorio(): String = hex(ByteArray(8).also { azar.nextBytes(it) })
}
