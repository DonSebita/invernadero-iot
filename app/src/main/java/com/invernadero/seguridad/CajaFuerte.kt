package com.invernadero.seguridad

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda ajustes CIFRADOS con AES-256-GCM. La clave vive en el Android Keystore
 * (hardware seguro del teléfono): no se puede extraer ni copiar a otro equipo.
 */
class CajaFuerte(context: Context) {
    private val prefs = context.getSharedPreferences("ajustes_cifrados", Context.MODE_PRIVATE)
    private val alias = "invernadero_clave_maestra"

    private fun clave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun guardar(nombre: String, valor: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, clave())
        val datos = c.iv + c.doFinal(valor.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(nombre, Base64.encodeToString(datos, Base64.NO_WRAP)).apply()
    }

    fun borrar(nombre: String) { prefs.edit().remove(nombre).apply() }

    fun leer(nombre: String): String? {
        val b64 = prefs.getString(nombre, null) ?: return null
        return try {
            val d = Base64.decode(b64, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(128, d, 0, 12))
            String(c.doFinal(d, 12, d.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            null   // dato alterado o clave perdida: se ignora
        }
    }
}
