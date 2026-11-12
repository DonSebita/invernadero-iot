package com.invernadero.conexion

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.invernadero.datos.Rol
import com.invernadero.seguridad.Cripto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Bluetooth clásico (SPP) directo con el ESP32. El ESP32 debe estar EMPAREJADO antes
 * (Ajustes del teléfono > Bluetooth). El emparejamiento cifra el enlace; además:
 *   ESP32 -> app:  "D|<json>|<hmac(json)>"
 *   app -> ESP32:  "C|<comando>|<hmac(comando + nonce)>"
 */
@SuppressLint("MissingPermission")
class Esp32Bluetooth(
    private val context: Context,
    private val nombreDispositivo: String,
    private val secreto: String
) : FuenteEsp32 {
    override val nombre = "Bluetooth · $nombreDispositivo"

    private val spp: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    @Volatile private var salida: OutputStream? = null
    @Volatile private var nonce = ""

    private fun tienePermiso() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    override fun lecturas(): Flow<EventoFuente> = flow {
        while (true) {
            val adaptador = context.getSystemService(BluetoothManager::class.java)?.adapter
            val problema = when {
                !tienePermiso() -> "Falta el permiso de Bluetooth (ve a Ajustes y pulsa Guardar)"
                adaptador == null -> "Este teléfono no tiene Bluetooth"
                !adaptador.isEnabled -> "Bluetooth apagado"
                else -> null
            }
            val dispositivo = if (problema == null) adaptador!!.bondedDevices.firstOrNull { it.name == nombreDispositivo } else null
            if (problema != null || dispositivo == null) {
                emit(EventoFuente.Falla(problema ?: "Empareja \"$nombreDispositivo\" en los ajustes de Bluetooth del teléfono"))
                delay(4000)
                continue
            }

            var socket: BluetoothSocket? = null
            try {
                socket = dispositivo.createRfcommSocketToServiceRecord(spp)
                socket.connect()
                salida = socket.outputStream
                val lector = socket.inputStream.bufferedReader(Charsets.UTF_8)
                while (true) {
                    val linea = lector.readLine() ?: break
                    procesarLinea(linea)?.let { emit(it) }
                }
            } catch (e: IOException) {
                emit(EventoFuente.Falla("Bluetooth desconectado: ${e.message}"))
            } finally {
                salida = null
                runCatching { socket?.close() }
            }
            delay(3000)
        }
    }.flowOn(Dispatchers.IO)

    private fun procesarLinea(linea: String): EventoFuente? {
        if (!linea.startsWith("D|")) return null      // mensajes de texto del sketch (logs) se ignoran
        val corte = linea.lastIndexOf('|')
        if (corte <= 2) return null
        val json = linea.substring(2, corte)
        val firma = linea.substring(corte + 1).trim()
        if (!Cripto.igualesSeguro(firma, Cripto.hmacHex(secreto, json))) {
            return EventoFuente.Falla("Seguridad: firma inválida por Bluetooth")
        }
        val l = runCatching { parsearLectura(json) }.getOrNull() ?: return EventoFuente.Falla("Dato mal formado")
        nonce = l.nonce
        return EventoFuente.Dato(l)
    }

    override suspend fun enviar(codigo: String, usuario: String, rol: Rol): Boolean = withContext(Dispatchers.IO) {
        val s = salida ?: return@withContext false
        try {
            val linea = "C|$codigo|${Cripto.hmacHex(secreto, codigo + nonce)}\n"
            synchronized(this@Esp32Bluetooth) {
                s.write(linea.toByteArray(Charsets.UTF_8))
                s.flush()
            }
            true
        } catch (e: IOException) {
            false
        }
    }
}
