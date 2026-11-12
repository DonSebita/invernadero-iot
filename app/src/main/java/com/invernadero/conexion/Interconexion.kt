package com.invernadero.conexion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.invernadero.datos.Rol
import com.invernadero.seguridad.Cripto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.math.abs

/*
 * INTERCONEXIÓN ENTRE TELÉFONOS (WiFi, TCP puerto 5050)
 *
 *   ESP32 ──WiFi/BT──> Teléfono CENTRAL ──WiFi (TCP cifrado)──> Teléfono(s) REMOTO(S)
 *                            ^                                        │
 *                            └────────── comandos (cifrados) ─────────┘
 *
 * - El central se anuncia en la red con NSD (_invernadero._tcp) y los remotos lo encuentran solos.
 * - Cada mensaje va cifrado con AES-256-GCM (clave derivada de la clave compartida).
 * - Cada mensaje lleva id aleatorio + hora: se rechazan repetidos y viejos (> 60 s).
 * - Máximo 5 teléfonos conectados al central (protección ante saturación).
 */

const val PUERTO_CENTRAL = 5050
const val TIPO_NSD = "_invernadero._tcp"
private const val VIGENCIA_MS = 60_000L
private const val MAX_CLIENTES = 5

object Mensajes {
    fun crear(tipo: String, id: String = Cripto.idAleatorio(), contenido: JSONObject.() -> Unit = {}): String =
        JSONObject().apply {
            put("tipo", tipo)
            put("id", id)
            put("ts", System.currentTimeMillis())
            contenido()
        }.toString()

    fun vigente(o: JSONObject) = abs(System.currentTimeMillis() - o.optLong("ts", 0)) < VIGENCIA_MS
}

sealed interface Recibido {
    data object Cerrado : Recibido
    data object Rechazado : Recibido
    data class Mensaje(val json: JSONObject) : Recibido
}

/** Un socket TCP donde cada línea es un mensaje cifrado. */
class Enlace(private val socket: Socket, private val clave: ByteArray) {
    private val escritor = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
    private val lector = socket.getInputStream().bufferedReader(Charsets.UTF_8)
    @Volatile var nombre: String = socket.inetAddress?.hostAddress ?: "?"

    @Synchronized
    fun enviar(textoPlano: String): Boolean = try {
        escritor.write(Cripto.cifrar(clave, textoPlano))
        escritor.write("\n")
        escritor.flush()
        true
    } catch (e: IOException) {
        false
    }

    fun recibir(): Recibido {
        val linea = lector.readLine() ?: return Recibido.Cerrado
        val texto = Cripto.descifrar(clave, linea) ?: return Recibido.Rechazado
        val o = runCatching { JSONObject(texto) }.getOrNull() ?: return Recibido.Rechazado
        return if (Mensajes.vigente(o)) Recibido.Mensaje(o) else Recibido.Rechazado
    }

    fun cerrar() { runCatching { socket.close() } }
}

// ─────────────────────────────────────────────────────────────
//  CENTRAL: comparte las lecturas y recibe comandos de los remotos
// ─────────────────────────────────────────────────────────────
class CentralServidor(
    private val context: Context,
    secreto: String,
    private val alComando: suspend (codigo: String, usuario: String, rol: Rol, origen: String) -> Boolean
) {
    private val clave = Cripto.claveEnlace(secreto)
    private val enlaces = CopyOnWriteArrayList<Enlace>()
    private val _conectados = MutableStateFlow(0)
    val conectados: StateFlow<Int> = _conectados.asStateFlow()

    @Volatile private var servidor: ServerSocket? = null
    private var nsd: NsdManager? = null
    private var registro: NsdManager.RegistrationListener? = null
    private val vistos = object : LinkedHashMap<String, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 500
    }

    fun iniciar(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val ss = try { ServerSocket(PUERTO_CENTRAL) } catch (e: IOException) { return@launch }
            servidor = ss
            registrarNsd()
            while (isActive && !ss.isClosed) {
                val s = try { ss.accept() } catch (e: IOException) { break }
                if (enlaces.size >= MAX_CLIENTES) { runCatching { s.close() }; continue }
                val e = Enlace(s, clave)
                enlaces += e
                _conectados.value = enlaces.size
                launch { atender(e) }
            }
        }
    }

    private suspend fun atender(e: Enlace) {
        try {
            while (true) {
                val r = e.recibir()
                if (r is Recibido.Cerrado) break
                if (r !is Recibido.Mensaje) continue                 // no autenticado o viejo: se descarta
                val o = r.json
                if (!marcarVisto(o.optString("id"))) continue        // repetido: posible ataque de repetición
                when (o.optString("tipo")) {
                    "hola" -> e.nombre = o.optString("dispositivo", e.nombre).take(40)
                    "comando" -> {
                        val rol = runCatching { Rol.valueOf(o.optString("rol")) }.getOrDefault(Rol.VISOR)
                        val ok = alComando(o.optString("codigo"), o.optString("usuario").take(30), rol, e.nombre)
                        e.enviar(Mensajes.crear("respuesta") { put("ref", o.optString("id")); put("ok", ok) })
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            quitar(e)
        }
    }

    @Synchronized
    private fun marcarVisto(id: String): Boolean {
        if (id.isEmpty() || vistos.containsKey(id)) return false
        vistos[id] = true
        return true
    }

    fun difundir(l: LecturaEsp32) {
        if (enlaces.isEmpty()) return
        val texto = Mensajes.crear("lectura") { put("datos", l.aJson()) }
        for (e in enlaces) if (!e.enviar(texto)) quitar(e)
    }

    private fun quitar(e: Enlace) {
        enlaces.remove(e)
        e.cerrar()
        _conectados.value = enlaces.size
    }

    private fun registrarNsd() {
        val m = context.getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "Invernadero"
            serviceType = TIPO_NSD
            port = PUERTO_CENTRAL
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, codigo: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, codigo: Int) {}
        }
        runCatching { m.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
        nsd = m
        registro = l
    }

    fun detener() {
        runCatching { registro?.let { nsd?.unregisterService(it) } }
        registro = null
        runCatching { servidor?.close() }
        enlaces.forEach { quitar(it) }
    }
}

// ─────────────────────────────────────────────────────────────
//  REMOTO: recibe del central y le manda comandos
// ─────────────────────────────────────────────────────────────
class RemotoTcp(
    private val ipCentral: String,
    secreto: String,
    private val dispositivo: String
) : FuenteEsp32 {
    override val nombre = "Teléfono central · $ipCentral"

    private val clave = Cripto.claveEnlace(secreto)
    @Volatile private var enlace: Enlace? = null
    private val pendientes = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    override fun lecturas(): Flow<EventoFuente> = flow {
        while (true) {
            if (ipCentral.isBlank()) {
                emit(EventoFuente.Falla("Configura o busca la IP del teléfono central en Ajustes"))
                delay(4000)
                continue
            }
            var e: Enlace? = null
            var falla = "El teléfono central cerró la conexión"
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ipCentral, PUERTO_CENTRAL), 3000)
                s.soTimeout = 10_000        // el central manda datos cada 2 s; 10 s sin nada = caído
                e = Enlace(s, clave)
                enlace = e
                e.enviar(Mensajes.crear("hola") { put("dispositivo", dispositivo) })
                var rechazados = 0
                while (true) {
                    when (val r = e.recibir()) {
                        Recibido.Cerrado -> break
                        Recibido.Rechazado -> {
                            rechazados++
                            if (rechazados == 3) emit(EventoFuente.Falla("No se pueden descifrar los datos: la clave compartida no coincide"))
                        }
                        is Recibido.Mensaje -> when (r.json.optString("tipo")) {
                            "lectura" -> runCatching { parsearLectura(r.json.getJSONObject("datos").toString()) }
                                .getOrNull()?.let { emit(EventoFuente.Dato(it)) }
                            "respuesta" -> pendientes.remove(r.json.optString("ref"))?.complete(r.json.optBoolean("ok"))
                        }
                    }
                }
            } catch (ex: SocketTimeoutException) {
                falla = "El central dejó de enviar datos"
            } catch (ex: IOException) {
                falla = "No se pudo conectar al central ($ipCentral): ${ex.message}"
            } finally {
                enlace = null
                e?.cerrar()
            }
            emit(EventoFuente.Falla(falla))
            delay(3000)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun enviar(codigo: String, usuario: String, rol: Rol): Boolean {
        val e = enlace ?: return false
        val id = Cripto.idAleatorio()
        val espera = CompletableDeferred<Boolean>()
        pendientes[id] = espera
        val texto = Mensajes.crear("comando", id) {
            put("codigo", codigo)
            put("usuario", usuario)
            put("rol", rol.name)
        }
        val enviado = withContext(Dispatchers.IO) { e.enviar(texto) }
        if (!enviado) {
            pendientes.remove(id)
            return false
        }
        val ok = withTimeoutOrNull(7000) { espera.await() }
        pendientes.remove(id)
        return ok == true
    }
}

// ─────────────────────────────────────────────────────────────
//  Utilidades de red
// ─────────────────────────────────────────────────────────────

/** Busca el teléfono central en la red WiFi (NSD / mDNS). Devuelve su IP o null. */
suspend fun buscarCentral(context: Context, tiempoMs: Long = 8000): String? = withTimeoutOrNull(tiempoMs) {
    suspendCancellableCoroutine { cont ->
        val m = context.getSystemService(NsdManager::class.java)
        if (m == null) { cont.resume(null); return@suspendCancellableCoroutine }
        lateinit var descubridor: NsdManager.DiscoveryListener
        fun terminar(ip: String?) {
            runCatching { m.stopServiceDiscovery(descubridor) }
            if (cont.isActive) cont.resume(ip)
        }
        descubridor = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(tipo: String) {}
            override fun onDiscoveryStopped(tipo: String) {}
            override fun onStartDiscoveryFailed(tipo: String, codigo: Int) { if (cont.isActive) cont.resume(null) }
            override fun onStopDiscoveryFailed(tipo: String, codigo: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.contains("_invernadero")) return
                @Suppress("DEPRECATION")
                m.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, codigo: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val ip = info.host?.hostAddress
                        if (ip != null && ip != ipLocal()) terminar(ip)   // que no se encuentre a sí mismo
                    }
                })
            }
        }
        m.discoverServices(TIPO_NSD, NsdManager.PROTOCOL_DNS_SD, descubridor)
        cont.invokeOnCancellation { runCatching { m.stopServiceDiscovery(descubridor) } }
    }
}

/** IP de este teléfono en la red WiFi (para decírsela a los remotos). */
fun ipLocal(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()

fun esIpValida(ip: String): Boolean {
    val partes = ip.trim().split(".")
    return partes.size == 4 && partes.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true && p.length <= 3 }
}
