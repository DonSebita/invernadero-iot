package com.invernadero.conexion

import android.content.Context
import android.os.Build
import com.invernadero.datos.AjustesRepo
import com.invernadero.datos.BaseDatos
import com.invernadero.datos.ConfigConexion
import com.invernadero.datos.ModoConexion
import com.invernadero.datos.Rol
import com.invernadero.datos.Sesion
import com.invernadero.nube.Nube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

/** Estado y métricas de estabilidad de la conexión (se muestran en Historial). */
data class EstadoConexion(
    val modo: ModoConexion = ModoConexion.SIMULADOR,
    val descripcion: String = "",
    val conectado: Boolean = false,
    val mensaje: String? = null,
    val recibidos: Int = 0,
    val fallas: Int = 0,
    val cortes: Int = 0,
    val latenciaPromedioMs: Long = 0,
    val latenciaMaxMs: Long = 0,
    val intervaloPromedioMs: Long = 0,
    val comandosOk: Int = 0,
    val comandosFallidos: Int = 0,
    val comandosDenegados: Int = 0,
    val telefonosConectados: Int = 0,
    val compartiendo: Boolean = false,
    val desdeNube: Boolean = false,      // lo que se ve viene de Firebase (solo mirar)
    val nubeTs: Long = 0L,               // cuándo subió el ESP32 ese dato
    val desde: Long = System.currentTimeMillis()
)

/** "recién", "hace 3 min", "hace 2 h", "hace 4 días". */
fun haceCuanto(ts: Long): String {
    if (ts <= 0L) return "sin datos"
    val min = (System.currentTimeMillis() - ts) / 60_000L
    return when {
        min < 1 -> "recién"
        min < 60 -> "hace $min min"
        min < 48 * 60 -> "hace ${min / 60} h"
        else -> "hace ${min / (24 * 60)} días"
    }
}

/** Texto corto del estado para las píldoras de Controles y Ajustes. */
fun EstadoConexion.textoCorto(): String = when {
    desdeNube -> "${if (modo == ModoConexion.NUBE) descripcion else "Sin conexión local · nube"} · ${haceCuanto(nubeTs)}"
    conectado -> "$descripcion · conectado"
    else -> "$descripcion · ${mensaje ?: "sin conexión"}"
}

/**
 * Cerebro de la comunicación:
 *  - elige la fuente según los ajustes (Simulador / WiFi / Bluetooth / Remoto)
 *  - publica la última lectura y las métricas
 *  - guarda el historial en SQLite cada 30 s
 *  - si "compartir" está activo, actúa como CENTRAL para otros teléfonos
 *  - controla el acceso a los comandos por rol y lo registra en auditoría
 *  - MODELO HÍBRIDO: si se pierde la conexión local (o en modo "Desde la nube") muestra el último
 *    estado que el ESP32 subió a Firebase. Desde la nube solo se mira: las órdenes siempre van por la red local.
 */
class ControladorConexion(
    private val context: Context,
    private val db: BaseDatos,
    private val ajustes: AjustesRepo,
    private val sesion: Sesion,
    private val nube: Nube
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _lectura = MutableStateFlow<LecturaEsp32?>(null)
    val lectura: StateFlow<LecturaEsp32?> = _lectura.asStateFlow()

    private val _estado = MutableStateFlow(EstadoConexion())
    val estado: StateFlow<EstadoConexion> = _estado.asStateFlow()

    private var trabajo: Job? = null
    @Volatile private var fuente: FuenteEsp32? = null
    @Volatile private var central: CentralServidor? = null

    @Volatile private var ultimaRecepcion = 0L
    @Volatile private var ultimoGuardado = 0L
    @Volatile private var enCorte = false
    @Volatile private var sumaLatencia = 0L
    @Volatile private var sumaIntervalos = 0L
    @Volatile private var inicio = 0L
    @Volatile private var ultimaNube: LecturaEsp32? = null

    fun reiniciar(config: ConfigConexion = ajustes.cargar()) {
        detener()
        val invernadero = sesion.usuario.value?.invernadero?.takeIf { it.isNotEmpty() }
        // Un teléfono donde nunca se configuró la conexión (otro teléfono tuyo, o el de un invitado) mira desde la nube
        val modo = if (!ajustes.configurado && invernadero != null) ModoConexion.NUBE else config.modo
        val nueva: FuenteEsp32 = when (modo) {
            ModoConexion.SIMULADOR -> SimuladorEsp32(config.sensoresSimulados)
            ModoConexion.WIFI -> Esp32Wifi(config.ipEsp32.trim(), config.secreto)
            ModoConexion.BLUETOOTH -> Esp32Bluetooth(context, config.nombreBt.trim(), config.secreto)
            ModoConexion.REMOTO -> RemotoTcp(config.ipCentral.trim(), config.secreto, Build.MODEL ?: "Android")
            ModoConexion.NUBE -> FuenteNube(nube, invernadero)
        }
        fuente = nueva
        inicio = System.currentTimeMillis()
        val compartir = config.compartir && modo != ModoConexion.REMOTO && modo != ModoConexion.NUBE
        // Respaldo: con WiFi, Bluetooth o central, si no llegan datos locales se muestra la nube
        val respaldo = invernadero != null && modo != ModoConexion.SIMULADOR && modo != ModoConexion.NUBE
        _estado.value = EstadoConexion(modo = modo, descripcion = nueva.nombre, mensaje = "Conectando…", compartiendo = compartir)

        trabajo = scope.launch {
            if (compartir) {
                val c = CentralServidor(context, config.secreto) { codigo, usuario, rol, origen ->
                    ejecutar(codigo, usuario, rol, "teléfono remoto $origen")
                }
                central = c
                c.iniciar(this)
                launch { c.conectados.collect { n -> _estado.update { it.copy(telefonosConectados = n) } } }
            }
            if (respaldo && invernadero != null) launch {
                nube.estado(invernadero).catch { }.collect { l -> ultimaNube = l }
            }
            if (modo != ModoConexion.NUBE) launch { vigilarCortes(respaldo) }
            nueva.lecturas().collect { ev ->
                when (ev) {
                    is EventoFuente.Dato -> if (ev.lectura.desdeNube) mostrarNube(ev.lectura) else alRecibir(ev.lectura, modo)
                    is EventoFuente.Falla -> _estado.update { it.copy(fallas = it.fallas + 1, mensaje = ev.mensaje) }
                }
            }
        }
    }

    fun detener() {
        trabajo?.cancel()
        trabajo = null
        central?.detener()
        central = null
        fuente = null
        _lectura.value = null
        ultimaNube = null
        ultimaRecepcion = 0L; ultimoGuardado = 0L; enCorte = false; sumaLatencia = 0L; sumaIntervalos = 0L
    }

    /** Publica el último estado de la nube (no cuenta para las métricas de estabilidad ni se guarda en SQLite). */
    private fun mostrarNube(l: LecturaEsp32) {
        _lectura.value = l
        val viejo = System.currentTimeMillis() - l.recibidoEn > 10 * 60_000L
        _estado.update {
            it.copy(
                desdeNube = true, nubeTs = l.recibidoEn, conectado = !viejo,
                mensaje = if (viejo) "El ESP32 no sube datos ${haceCuanto(l.recibidoEn)}" else null
            )
        }
    }

    private fun alRecibir(l: LecturaEsp32, modo: ModoConexion) {
        val ahora = System.currentTimeMillis()
        val intervalo = if (ultimaRecepcion == 0L) 0L else ahora - ultimaRecepcion
        ultimaRecepcion = ahora
        enCorte = false
        if (_estado.value.desdeNube) _estado.update { it.copy(desdeNube = false) }   // volvió la conexión local
        sumaLatencia += l.latenciaMs
        sumaIntervalos += intervalo
        val latSuma = sumaLatencia
        val intSuma = sumaIntervalos
        _lectura.value = l
        _estado.update { e ->
            val n = e.recibidos + 1
            e.copy(
                conectado = true,
                mensaje = null,
                recibidos = n,
                latenciaPromedioMs = latSuma / n,
                latenciaMaxMs = max(e.latenciaMaxMs, l.latenciaMs),
                intervaloPromedioMs = if (n > 1) intSuma / (n - 1) else 0
            )
        }
        central?.let { c -> scope.launch(Dispatchers.IO) { c.difundir(l) } }
        if (ahora - ultimoGuardado >= 30_000) {
            ultimoGuardado = ahora
            scope.launch(Dispatchers.IO) { db.guardarLectura(l, modo.name) }
        }
    }

    /**
     * Si pasan más de 6 s sin datos se cuenta un "corte" (métrica de estabilidad).
     * Con [respaldo], tras 8 s sin datos locales se muestra lo último que hay en la nube.
     */
    private suspend fun vigilarCortes(respaldo: Boolean) {
        while (true) {
            delay(1000)
            val ahora = System.currentTimeMillis()
            val sin = ahora - ultimaRecepcion
            if (ultimaRecepcion > 0 && sin > 6000 && !enCorte) {
                enCorte = true
                _estado.update { it.copy(conectado = false, cortes = it.cortes + 1, mensaje = it.mensaje ?: "Sin datos") }
            }
            val localQuieto = ahora - max(ultimaRecepcion, inicio) > 8000
            val enNube = ultimaNube
            if (respaldo && localQuieto && enNube != null && _lectura.value !== enNube) mostrarNube(enNube)
        }
    }

    /** Comando desde ESTE teléfono (usuario de la sesión actual). */
    suspend fun enviar(codigo: String): Boolean {
        val u = sesion.usuario.value ?: return false
        return ejecutar(codigo.trim().uppercase(), u.nombre, u.rol, "este teléfono", reglasPermitidas = !u.invitado)
    }

    private suspend fun ejecutar(
        codigo: String, usuario: String, rol: Rol, origen: String, reglasPermitidas: Boolean = true
    ): Boolean {
        val f = fuente
        val resultado = when {
            !rol.puedeActivar -> "DENEGADO (rol ${rol.etiqueta})"
            Comandos.esRegla(codigo) && !reglasPermitidas -> "DENEGADO (un pase de visita no cambia las reglas)"
            !Comandos.esValido(codigo) -> "DENEGADO (comando inválido)"
            f is FuenteNube || _estado.value.desdeNube -> "FALLA (desde la nube solo se mira: conéctate a la red del invernadero)"
            f == null -> "FALLA (sin conexión)"
            withTimeoutOrNull(8000) { f?.enviar(codigo, usuario, rol) } == true -> "OK"
            else -> "FALLA (sin respuesta)"
        }
        val ok = resultado == "OK"
        _estado.update {
            when {
                ok -> it.copy(comandosOk = it.comandosOk + 1)
                resultado.startsWith("DENEGADO") -> it.copy(comandosDenegados = it.comandosDenegados + 1)
                else -> it.copy(comandosFallidos = it.comandosFallidos + 1)
            }
        }
        withContext(Dispatchers.IO) {
            db.auditar(usuario, "COMANDO", "$codigo · ${Comandos.descripcion(codigo)} · desde $origen", resultado)
        }
        return ok
    }
}
