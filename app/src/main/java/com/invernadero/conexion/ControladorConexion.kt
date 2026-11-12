package com.invernadero.conexion

import android.content.Context
import android.os.Build
import com.invernadero.datos.AjustesRepo
import com.invernadero.datos.BaseDatos
import com.invernadero.datos.ConfigConexion
import com.invernadero.datos.ModoConexion
import com.invernadero.datos.Rol
import com.invernadero.datos.Sesion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val desde: Long = System.currentTimeMillis()
)

/**
 * Cerebro de la comunicación:
 *  - elige la fuente según los ajustes (Simulador / WiFi / Bluetooth / Remoto)
 *  - publica la última lectura y las métricas
 *  - guarda el historial en SQLite cada 30 s
 *  - si "compartir" está activo, actúa como CENTRAL para otros teléfonos
 *  - controla el acceso a los comandos por rol y lo registra en auditoría
 */
class ControladorConexion(
    private val context: Context,
    private val db: BaseDatos,
    private val ajustes: AjustesRepo,
    private val sesion: Sesion
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

    fun reiniciar(config: ConfigConexion = ajustes.cargar()) {
        detener()
        val nueva: FuenteEsp32 = when (config.modo) {
            ModoConexion.SIMULADOR -> SimuladorEsp32(config.sensoresSimulados)
            ModoConexion.WIFI -> Esp32Wifi(config.ipEsp32.trim(), config.secreto)
            ModoConexion.BLUETOOTH -> Esp32Bluetooth(context, config.nombreBt.trim(), config.secreto)
            ModoConexion.REMOTO -> RemotoTcp(config.ipCentral.trim(), config.secreto, Build.MODEL ?: "Android")
        }
        fuente = nueva
        val compartir = config.compartir && config.modo != ModoConexion.REMOTO
        _estado.value = EstadoConexion(modo = config.modo, descripcion = nueva.nombre, mensaje = "Conectando…", compartiendo = compartir)

        trabajo = scope.launch {
            if (compartir) {
                val c = CentralServidor(context, config.secreto) { codigo, usuario, rol, origen ->
                    ejecutar(codigo, usuario, rol, "teléfono remoto $origen")
                }
                central = c
                c.iniciar(this)
                launch { c.conectados.collect { n -> _estado.update { it.copy(telefonosConectados = n) } } }
            }
            launch { vigilarCortes() }
            nueva.lecturas().collect { ev ->
                when (ev) {
                    is EventoFuente.Dato -> alRecibir(ev.lectura, config.modo)
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
        ultimaRecepcion = 0L; ultimoGuardado = 0L; enCorte = false; sumaLatencia = 0L; sumaIntervalos = 0L
    }

    private fun alRecibir(l: LecturaEsp32, modo: ModoConexion) {
        val ahora = System.currentTimeMillis()
        val intervalo = if (ultimaRecepcion == 0L) 0L else ahora - ultimaRecepcion
        ultimaRecepcion = ahora
        enCorte = false
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

    /** Si pasan más de 6 s sin datos se cuenta un "corte" (métrica de estabilidad). */
    private suspend fun vigilarCortes() {
        while (true) {
            delay(1000)
            val sin = System.currentTimeMillis() - ultimaRecepcion
            if (ultimaRecepcion > 0 && sin > 6000 && !enCorte) {
                enCorte = true
                _estado.update { it.copy(conectado = false, cortes = it.cortes + 1, mensaje = it.mensaje ?: "Sin datos") }
            }
        }
    }

    /** Comando desde ESTE teléfono (usuario de la sesión actual). */
    suspend fun enviar(codigo: String): Boolean {
        val u = sesion.usuario.value ?: return false
        return ejecutar(codigo.trim().uppercase(), u.nombre, u.rol, "este teléfono")
    }

    private suspend fun ejecutar(codigo: String, usuario: String, rol: Rol, origen: String): Boolean {
        val f = fuente
        val resultado = when {
            !rol.puedeActivar -> "DENEGADO (rol ${rol.etiqueta})"
            !Comandos.esValido(codigo) -> "DENEGADO (comando inválido)"
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
