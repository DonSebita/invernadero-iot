package com.invernadero.conexion

import android.os.SystemClock
import com.invernadero.datos.Rol
import com.invernadero.seguridad.Cripto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * ESP32 en la misma red WiFi (HTTP local).
 *  - Cada respuesta trae la cabecera X-Firma = HMAC-SHA256(clave, cuerpo): si no coincide, se descarta.
 *  - Cada comando va firmado con HMAC(clave, comando + nonce). El ESP32 cambia el nonce después de
 *    cada comando, así un comando capturado en la red no se puede volver a usar (anti-repetición).
 *  - La clave compartida nunca viaja por la red.
 */
class Esp32Wifi(private val ip: String, private val secreto: String) : FuenteEsp32 {
    override val nombre = "WiFi · ESP32 $ip"

    @Volatile private var nonce = ""

    override fun lecturas(): Flow<EventoFuente> = flow {
        while (true) {
            val evento = try {
                EventoFuente.Dato(pedir("/datos"))
            } catch (e: SecurityException) {
                EventoFuente.Falla("Seguridad: ${e.message}")
            } catch (e: Exception) {
                EventoFuente.Falla("El ESP32 no responde (${e.javaClass.simpleName})")
            }
            emit(evento)
            delay(2000)
        }
    }.flowOn(Dispatchers.IO)

    private fun pedir(ruta: String): LecturaEsp32 {
        val inicio = SystemClock.elapsedRealtime()
        val con = URL("http://$ip$ruta").openConnection() as HttpURLConnection
        con.connectTimeout = 2500
        con.readTimeout = 2500
        con.useCaches = false
        try {
            val codigo = con.responseCode
            if (codigo != 200) throw IOException("HTTP $codigo")
            val cuerpo = con.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val firma = con.getHeaderField("X-Firma") ?: throw SecurityException("respuesta sin firma")
            if (!Cripto.igualesSeguro(firma, Cripto.hmacHex(secreto, cuerpo))) {
                throw SecurityException("firma inválida (¿la clave compartida es distinta?)")
            }
            val l = parsearLectura(cuerpo).copy(
                latenciaMs = SystemClock.elapsedRealtime() - inicio,
                recibidoEn = System.currentTimeMillis()
            )
            nonce = l.nonce
            return l
        } finally {
            con.disconnect()
        }
    }

    override suspend fun enviar(codigo: String, usuario: String, rol: Rol): Boolean = withContext(Dispatchers.IO) {
        // 2 intentos: si otro teléfono mandó un comando antes, el nonce cambió y hay que renovarlo
        repeat(2) {
            try {
                if (nonce.isEmpty()) pedir("/datos")
                val firma = Cripto.hmacHex(secreto, codigo + nonce)
                pedir("/comando?c=" + URLEncoder.encode(codigo, "UTF-8") + "&f=" + firma)
                return@withContext true
            } catch (e: Exception) {
                nonce = ""
            }
        }
        false
    }
}
