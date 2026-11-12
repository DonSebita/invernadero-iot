package com.invernadero.nube

import android.content.Context
import android.util.Patterns
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.invernadero.conexion.LecturaEsp32
import com.invernadero.conexion.ParametrosControl
import com.invernadero.datos.BaseDatos
import com.invernadero.datos.RegistroLectura
import com.invernadero.datos.Rol
import com.invernadero.datos.Usuario
import com.invernadero.datos.normalizarNombre
import com.invernadero.seguridad.CajaFuerte
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Error con un mensaje listo para mostrar. [bloqueado] = se alcanzó el límite de intentos. */
class ErrorNube(mensaje: String, val bloqueado: Boolean = false, val claveIncorrecta: Boolean = false) : Exception(mensaje)

/** Un pase de visita guardado en la nube (la clave nunca: solo su SHA-256). */
data class PaseNube(
    val hash: String,
    val puedeActivar: Boolean,
    val creada: Long,
    val vence: Long,
    val usos: Int,
    val ultimoVisitante: String?
)

/** Aviso que sube el ESP32 cuando pasa algo importante (calor, frío, tierra seca, sensor caído…). */
data class AlertaNube(val ts: Long, val tipo: String, val detalle: String)

/** Lo que hay que pegar en el sketch del ESP32 para que suba datos. La clave se muestra una sola vez. */
data class ConfigEsp32(val apiKey: String, val url: String, val invernadero: String, val correo: String, val clave: String) {
    fun comoCodigo(): String = """
        const char* FIREBASE_API_KEY = "$apiKey";
        const char* FIREBASE_URL     = "$url";
        const char* INVERNADERO_ID   = "$invernadero";
        const char* ESP32_CORREO     = "$correo";
        const char* ESP32_CLAVE      = "$clave";
    """.trimIndent()
}

/**
 * Límite de intentos en este dispositivo (además del que aplica Firebase en su servidor).
 * 5 fallos seguidos → 5 minutos de espera. Se guarda cifrado, así que reiniciar la app no lo salta.
 */
class Freno(private val caja: CajaFuerte, private val nombre: String) {
    companion object {
        const val MAX_INTENTOS = 5
        const val BLOQUEO_MS = 5 * 60_000L
    }

    /** Segundos que faltan para poder intentar de nuevo (0 = ya se puede). */
    @Synchronized fun espera(): Long {
        val hasta = caja.leer("freno_${nombre}_hasta")?.toLongOrNull() ?: 0L
        val resta = hasta - System.currentTimeMillis()
        return if (resta > 0) resta / 1000 + 1 else 0
    }

    /** Anota un fallo. Devuelve los segundos de bloqueo si con este se llegó al límite, o 0. */
    @Synchronized fun fallo(): Long {
        val n = (caja.leer("freno_${nombre}_fallos")?.toIntOrNull() ?: 0) + 1
        if (n >= MAX_INTENTOS) {
            caja.guardar("freno_${nombre}_fallos", "0")
            caja.guardar("freno_${nombre}_hasta", (System.currentTimeMillis() + BLOQUEO_MS).toString())
            return BLOQUEO_MS / 1000
        }
        caja.guardar("freno_${nombre}_fallos", n.toString())
        return 0
    }

    @Synchronized fun exito() = caja.guardar("freno_${nombre}_fallos", "0")
}

/**
 * Todo lo que GreenBox guarda o lee en Firebase (modelo HÍBRIDO):
 *  - Tiempo real: sigue siendo local (WiFi/Bluetooth con el ESP32, cada 2 s). No pasa por aquí.
 *  - Nube: cuentas (Firebase Authentication), el "estado" que sube el ESP32 cada 2 min, un resumen
 *    cada 5 min, avisos, pases de visita y una copia de la auditoría (Realtime Database).
 *
 * Estructura en la Realtime Database:
 *   usuarios/{uid}                       → nombre, invernadero
 *   invernaderos/{inv}/dueno|nombre|dispositivo
 *   invernaderos/{inv}/estado            → último dato del ESP32 (se sobrescribe)
 *   invernaderos/{inv}/resumenes/{id}    → promedio/mín/máx de 5 min
 *   invernaderos/{inv}/alertas/{id}
 *   invernaderos/{inv}/pases/{sha256}    → pases de visita
 *   invernaderos/{inv}/visitas/{uid}     → quién entró con qué pase
 *   invernaderos/{inv}/auditoria/{id}    → solo se agrega, nunca se borra
 *   pasesIndice/{sha256}                 → a qué invernadero abre cada pase
 * Quién puede leer o escribir cada cosa lo deciden las reglas de firebase/database.rules.json.
 */
class Nube(private val context: Context, db: BaseDatos, private val caja: CajaFuerte) {

    companion object {
        const val RECORDAR_MS = 30L * 24 * 3600 * 1000      // "Recordarme": 30 días desde el último uso
        const val MAX_PASES = 5
        const val LARGO_PASE = 12                            // ≈ 59 bits: no se puede adivinar por internet
        const val DIAS_EN_NUBE = 30                          // resúmenes y avisos más viejos se borran
        private const val ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        private const val ALFANUM = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789"

        fun normalizarPase(t: String): String = t.uppercase().filter { it in ALFABETO }
        fun formatearPase(t: String): String = normalizarPase(t).take(LARGO_PASE).chunked(4).joinToString("-")

        fun correoValido(t: String) = Patterns.EMAIL_ADDRESS.matcher(t.trim()).matches()

        /** Nombre visible del visitante: 2 a 24 letras, espacios o números. */
        fun validarVisitante(nombre: String): String? =
            if (nombre.length !in 2..24 || !nombre.all { it.isLetterOrDigit() || it == ' ' })
                "Escribe tu nombre (2 a 24 letras) para que tu anfitrión sepa quién pasó por aquí"
            else null

        fun sha256(texto: String): String =
            MessageDigest.getInstance("SHA-256").digest(texto.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private val auth: FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull()
    private val bd: FirebaseDatabase? = runCatching { FirebaseDatabase.getInstance() }.getOrNull()
    private val azar = SecureRandom()
    private val segundoPlano = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val frenoEntrar = Freno(caja, "entrar")
    val frenoVisitas = Freno(caja, "visitas")

    /** Quién tiene la sesión abierta en la nube (null = nadie). */
    @Volatile var actual: Usuario? = null
        private set

    init {
        auth?.setLanguageCode("es")          // correos de verificación y recuperación en español
        // Cada registro de auditoría local también se copia a la nube (solo se puede agregar, no borrar)
        db.espejo = { usuario, accion, detalle, resultado -> espejarAuditoria(usuario, accion, detalle, resultado) }
    }

    private fun a(): FirebaseAuth = auth
        ?: throw ErrorNube("Falta configurar Firebase: agrega app/google-services.json y vuelve a compilar.")

    private fun ref(ruta: String): DatabaseReference = (bd
        ?: throw ErrorNube("Falta la Realtime Database: créala en la consola de Firebase y descarga de nuevo google-services.json."))
        .getReference(ruta)

    // ───────────────────────── Lecturas de la base ─────────────────────────

    /** Lee una vez. Si hay copia en el teléfono la usa (sirve sin Internet). */
    private suspend fun leer(q: Query, esperaMs: Long = 10_000): DataSnapshot = withTimeoutOrNull(esperaMs) {
        suspendCancellableCoroutine<DataSnapshot> { cont ->
            val oyente = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) { if (cont.isActive) cont.resume(s) }
                override fun onCancelled(e: DatabaseError) { if (cont.isActive) cont.resumeWithException(ErrorNube(mensajeBase(e))) }
            }
            q.addListenerForSingleValueEvent(oyente)
            cont.invokeOnCancellation { q.removeEventListener(oyente) }
        }
    } ?: throw ErrorNube("La nube no responde. Revisa tu conexión a Internet.")

    /** Lee directo del servidor (para lo que no puede estar desactualizado: pases, revocaciones). */
    private suspend fun leerServidor(q: Query, esperaMs: Long = 10_000): DataSnapshot =
        withTimeoutOrNull(esperaMs) {
            try { q.get().await() } catch (e: CancellationException) { throw e } catch (e: Exception) { throw ErrorNube(mensajeGeneral(e)) }
        } ?: throw ErrorNube("La nube no responde. Revisa tu conexión a Internet.")

    /** Escritura que espera la confirmación del servidor, con tiempo máximo. */
    private suspend fun conTiempo(esperaMs: Long = 15_000, bloque: suspend () -> Unit) {
        val listo = try {
            withTimeoutOrNull(esperaMs) { bloque(); true }
        } catch (e: ErrorNube) { throw e } catch (e: CancellationException) { throw e } catch (e: Exception) { throw ErrorNube(mensajeGeneral(e)) }
        if (listo == null) throw ErrorNube("La nube no confirmó el cambio. Revisa tu conexión a Internet.")
    }

    private fun mensajeBase(e: DatabaseError): String = when (e.code) {
        DatabaseError.PERMISSION_DENIED -> "La nube no dio permiso (revisa las reglas de seguridad de la Realtime Database)."
        DatabaseError.DISCONNECTED, DatabaseError.NETWORK_ERROR -> "Sin conexión con la nube."
        else -> "Error de la nube: ${e.message}"
    }

    private fun mensajeGeneral(e: Exception): String = when {
        e is FirebaseNetworkException -> "Sin Internet: revisa tu conexión."
        e.message?.contains("Permission denied", ignoreCase = true) == true ->
            "La nube no dio permiso (revisa las reglas de seguridad de la Realtime Database)."
        else -> "Error de la nube: ${e.message ?: e.javaClass.simpleName}"
    }

    /** Mismo mensaje si falla el correo o la contraseña: no revela qué cuentas existen. */
    private fun mensajeAuth(e: Exception): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "Esa contraseña es muy débil: usa al menos 8 caracteres, con letras y números."
        is FirebaseAuthUserCollisionException -> "Ese correo ya tiene una cuenta. Entra o recupera tu contraseña."
        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> "Correo o contraseña incorrectos"
        is FirebaseTooManyRequestsException -> "Demasiados intentos desde este dispositivo. Espera unos minutos."
        is FirebaseNetworkException -> "Sin Internet: para entrar necesitas conexión (después funciona sin ella)."
        else -> "No se pudo entrar: ${e.message ?: e.javaClass.simpleName}"
    }

    // ───────────────────────── Cuentas ─────────────────────────

    private fun revisarFreno() {
        val s = frenoEntrar.espera()
        if (s > 0) throw ErrorNube("Demasiados intentos. La puerta descansa ${(s + 59) / 60} min antes de volver a abrirse.", bloqueado = true)
    }

    private fun falloDeEntrada(e: Exception): ErrorNube {
        if (e is FirebaseAuthInvalidCredentialsException || e is FirebaseAuthInvalidUserException) {
            val s = frenoEntrar.fallo()
            if (s > 0) return ErrorNube("Demasiados intentos. La puerta descansa ${(s + 59) / 60} min antes de volver a abrirse.", bloqueado = true)
        }
        return ErrorNube(mensajeAuth(e), bloqueado = e is FirebaseTooManyRequestsException)
    }

    suspend fun entrarConCorreo(correo: String, clave: String, recordar: Boolean): Usuario {
        revisarFreno()
        val res = try { a().signInWithEmailAndPassword(correo.trim(), clave).await() } catch (e: Exception) { throw falloDeEntrada(e) }
        frenoEntrar.exito()
        return prepararCuenta(res.user ?: throw ErrorNube("No se pudo entrar"), null, recordar)
    }

    suspend fun crearCuenta(nombre: String, correo: String, clave: String, recordar: Boolean): Usuario {
        val res = try { a().createUserWithEmailAndPassword(correo.trim(), clave).await() } catch (e: Exception) { throw ErrorNube(mensajeAuth(e)) }
        val user = res.user ?: throw ErrorNube("No se pudo crear la cuenta")
        runCatching { user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(nombre).build()).await() }
        runCatching { user.sendEmailVerification().await() }      // confirma que el correo es suyo
        return prepararCuenta(user, nombre, recordar)
    }

    /** [idToken] viene de Credential Manager ("Continuar con Google"). */
    suspend fun entrarConGoogle(idToken: String, recordar: Boolean): Usuario {
        revisarFreno()
        val credencial = GoogleAuthProvider.getCredential(idToken, null)
        val res = try { a().signInWithCredential(credencial).await() } catch (e: Exception) { throw ErrorNube(mensajeAuth(e)) }
        return prepararCuenta(res.user ?: throw ErrorNube("No se pudo entrar con Google"), null, recordar)
    }

    /** Envía el enlace para crear una contraseña nueva. No dice si el correo tiene cuenta o no. */
    suspend fun recuperar(correo: String) {
        try {
            a().sendPasswordResetEmail(correo.trim()).await()
        } catch (e: FirebaseAuthInvalidUserException) {
            // Silencio a propósito: no revelar qué correos tienen cuenta
        } catch (e: FirebaseNetworkException) {
            throw ErrorNube("Sin Internet: revisa tu conexión.")
        } catch (e: FirebaseTooManyRequestsException) {
            throw ErrorNube("Demasiadas solicitudes. Espera unos minutos.")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw ErrorNube("Ese correo no parece válido")
        }
    }

    private fun limpiarNombre(t: String): String =
        normalizarNombre(t).filter { it.isLetterOrDigit() || it in " ._-" }.take(30).ifBlank { "Jardinero" }
            .let { if (it.length < 2) "Jardinero" else it }

    /** Primera vez: crea el invernadero de la cuenta en la nube. Siempre: deja la sesión lista. */
    private suspend fun prepararCuenta(user: FirebaseUser, nombreSugerido: String?, recordar: Boolean): Usuario {
        try {
            val perfil = leer(ref("usuarios/${user.uid}"))
            val nombreGuardado = perfil.child("nombre").getValue(String::class.java)
            val invGuardado = perfil.child("invernadero").getValue(String::class.java)
            val (nombre, inv) = if (nombreGuardado != null && invGuardado != null) {
                nombreGuardado to invGuardado
            } else {
                // Primera vez: se crea el invernadero de esta cuenta
                val n = limpiarNombre(nombreSugerido ?: user.displayName ?: user.email?.substringBefore('@') ?: "Jardinero")
                val nuevo = ref("invernaderos").push()
                val clave = nuevo.key ?: throw ErrorNube("No se pudo crear el invernadero en la nube")
                conTiempo {
                    // Primero el invernadero (las reglas exigen que exista y sea suyo antes de anotarlo en el perfil)
                    nuevo.setValue(mapOf("dueno" to user.uid, "nombre" to "Invernadero de $n".take(60), "creado" to ServerValue.TIMESTAMP)).await()
                    ref("usuarios/${user.uid}").setValue(mapOf("nombre" to n, "invernadero" to clave)).await()
                }
                n to clave
            }
            val u = Usuario(uid = user.uid, nombre = nombre, rol = Rol.OPERADOR, correo = user.email, invernadero = inv)
            guardarPerfilLocal(u)
            if (recordar) {
                caja.guardar("nube_recordar", u.uid)
                caja.guardar("nube_recordar_hasta", (System.currentTimeMillis() + RECORDAR_MS).toString())
            } else {
                olvidarRecordado()
            }
            actual = u
            segundoPlano.launch { runCatching { limpiarViejos(u) } }
            return u
        } catch (e: Exception) {
            a().signOut()
            throw if (e is ErrorNube) e else ErrorNube(mensajeGeneral(e))
        }
    }

    private fun guardarPerfilLocal(u: Usuario) =
        caja.guardar("nube_perfil", listOf(u.uid, u.nombre, u.invernadero, u.correo.orEmpty()).joinToString("\n"))

    private fun perfilLocal(): Usuario? {
        val p = caja.leer("nube_perfil")?.split("\n") ?: return null
        if (p.size < 4) return null
        return Usuario(uid = p[0], nombre = p[1], rol = Rol.OPERADOR, invernadero = p[2], correo = p[3].ifBlank { null })
    }

    private fun olvidarRecordado() {
        caja.borrar("nube_recordar")
        caja.borrar("nube_recordar_hasta")
    }

    /**
     * Al abrir la app: si la cuenta quedó con "Recordarme" (y no han pasado 30 días sin uso) se entra directo,
     * también sin Internet. Si no, se cierra la sesión que Firebase haya guardado.
     */
    fun restaurar(): Usuario? {
        val user = auth?.currentUser ?: return null
        val recordado = caja.leer("nube_recordar")
        val hasta = caja.leer("nube_recordar_hasta")?.toLongOrNull() ?: 0L
        val u = perfilLocal()?.takeIf { it.uid == user.uid }
        if (user.isAnonymous || recordado != user.uid || hasta < System.currentTimeMillis() || u == null) {
            if (user.isAnonymous) runCatching { user.delete() }
            olvidarRecordado()
            auth?.signOut()
            return null
        }
        caja.guardar("nube_recordar_hasta", (System.currentTimeMillis() + RECORDAR_MS).toString())
        actual = u
        segundoPlano.launch { runCatching { limpiarViejos(u) } }
        return u
    }

    /** Cerrar sesión: olvida el "Recordarme" y, si era una visita, borra su identidad anónima. */
    suspend fun salir() {
        val user = auth?.currentUser
        actual = null
        olvidarRecordado()
        if (user?.isAnonymous == true) runCatching { withTimeoutOrNull(5_000) { user.delete().await() } }
        auth?.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    // ───────────────────────── Pases de visita ─────────────────────────

    private fun claveMala() = ErrorNube(
        "Esa clave no abre la puerta: puede que esté mal escrita, haya vencido o la hayan revocado.", claveIncorrecta = true
    )

    suspend fun entrarDeVisita(visitante: String, codigo: String): Usuario {
        val s = frenoVisitas.espera()
        if (s > 0) throw ErrorNube("Demasiadas claves incorrectas. La puerta de visitas se abre de nuevo en ${(s + 59) / 60} min.", bloqueado = true)
        val hash = sha256(normalizarPase(codigo))
        val fa = a()
        if (fa.currentUser?.isAnonymous != true) {
            fa.signOut()
            try { fa.signInAnonymously().await() } catch (e: Exception) { throw ErrorNube(mensajeAuth(e)) }
        }
        val uid = fa.currentUser?.uid ?: throw ErrorNube("No se pudo abrir la visita")
        try {
            val inv = leerServidor(ref("pasesIndice/$hash")).getValue(String::class.java) ?: throw claveMala()
            val p = leerServidor(ref("invernaderos/$inv/pases/$hash"))
            val vence = p.child("vence").getValue(Long::class.java) ?: throw claveMala()
            if (p.child("revocado").getValue(Boolean::class.java) == true || vence <= System.currentTimeMillis()) throw claveMala()
            val puede = p.child("puedeActivar").getValue(Boolean::class.java) == true
            val anfitrion = p.child("creadorNombre").getValue(String::class.java) ?: "tu anfitrión"
            conTiempo {
                ref("invernaderos/$inv/visitas/$uid")
                    .setValue(mapOf("pase" to hash, "nombre" to visitante, "ts" to ServerValue.TIMESTAMP)).await()
            }
            frenoVisitas.exito()
            val u = Usuario(
                uid = uid, nombre = "$visitante (invitado)", rol = if (puede) Rol.OPERADOR else Rol.VISOR,
                invernadero = inv, invitado = true, vence = vence, anfitrion = anfitrion, paseHash = hash
            )
            actual = u
            return u
        } catch (e: Exception) {
            runCatching { withTimeoutOrNull(5_000) { fa.currentUser?.delete()?.await() } }
            fa.signOut()
            if (e is ErrorNube && e.claveIncorrecta) {
                val b = frenoVisitas.fallo()
                if (b > 0) throw ErrorNube("Demasiadas claves incorrectas. La puerta de visitas se abre de nuevo en ${(b + 59) / 60} min.", bloqueado = true)
            }
            throw if (e is ErrorNube) e else ErrorNube(mensajeGeneral(e))
        }
    }

    /** ¿Sigue abierto el pase de este invitado? Sin Internet se confía en la hora de vencimiento. */
    suspend fun paseVigente(u: Usuario): Boolean {
        if (!u.invitado) return true
        if (System.currentTimeMillis() > u.vence) return false
        val h = u.paseHash ?: return false
        val p = runCatching { leerServidor(ref("invernaderos/${u.invernadero}/pases/$h"), 5_000) }.getOrNull() ?: return true
        return p.exists() && p.child("revocado").getValue(Boolean::class.java) != true
    }

    /** Crea un pase y devuelve la clave XXXX-XXXX-XXXX (se muestra una sola vez) y su vencimiento. */
    suspend fun crearPase(u: Usuario, duracionMs: Long, puedeActivar: Boolean): Pair<String, Long> {
        if (u.invitado) throw ErrorNube("Los invitados no pueden crear pases")
        if (pasesAbiertos(u).size >= MAX_PASES) throw ErrorNube("Ya tienes $MAX_PASES pases abiertos. Revoca uno antes de crear otro.")
        val codigo = String(CharArray(LARGO_PASE) { ALFABETO[azar.nextInt(ALFABETO.length)] })
        val hash = sha256(codigo)
        val vence = System.currentTimeMillis() + duracionMs
        conTiempo {
            ref("invernaderos/${u.invernadero}/pases/$hash").setValue(
                mapOf(
                    "creador" to u.uid, "creadorNombre" to u.nombre.take(40), "puedeActivar" to puedeActivar,
                    "creada" to ServerValue.TIMESTAMP, "vence" to vence
                )
            ).await()
            ref("pasesIndice/$hash").setValue(u.invernadero).await()
        }
        return formatearPase(codigo) to vence
    }

    suspend fun pasesAbiertos(u: Usuario): List<PaseNube> {
        val base = "invernaderos/${u.invernadero}"
        val pases = leerServidor(ref("$base/pases"))
        val visitas = runCatching { leerServidor(ref("$base/visitas")) }.getOrNull()
        val porPase = visitas?.children?.mapNotNull { v ->
            val h = v.child("pase").getValue(String::class.java) ?: return@mapNotNull null
            Triple(h, v.child("nombre").getValue(String::class.java), v.child("ts").getValue(Long::class.java) ?: 0L)
        }?.groupBy { it.first }.orEmpty()
        val ahora = System.currentTimeMillis()
        return pases.children.mapNotNull { p ->
            val h = p.key ?: return@mapNotNull null
            val vence = p.child("vence").getValue(Long::class.java) ?: return@mapNotNull null
            if (vence <= ahora || p.child("revocado").getValue(Boolean::class.java) == true) return@mapNotNull null
            val usos = porPase[h].orEmpty()
            PaseNube(
                hash = h,
                puedeActivar = p.child("puedeActivar").getValue(Boolean::class.java) == true,
                creada = p.child("creada").getValue(Long::class.java) ?: 0L,
                vence = vence,
                usos = usos.size,
                ultimoVisitante = usos.maxByOrNull { it.third }?.second
            )
        }.sortedBy { it.vence }
    }

    /** La visita de quien lo esté usando termina en menos de 15 s (la app lo revisa). */
    suspend fun revocarPase(u: Usuario, hash: String) {
        conTiempo {
            ref("invernaderos/${u.invernadero}/pases/$hash/revocado").setValue(true).await()
            ref("pasesIndice/$hash").removeValue().await()
        }
    }

    // ───────────────────────── ESP32 en la nube ─────────────────────────

    /** uid de la cuenta del ESP32 vinculado, o null. */
    suspend fun dispositivo(u: Usuario): String? =
        leer(ref("invernaderos/${u.invernadero}/dispositivo")).getValue(String::class.java)

    /**
     * Crea una cuenta propia para el ESP32 (correo interno + clave al azar de 24 caracteres) y la autoriza
     * a escribir SOLO en el estado, los resúmenes y los avisos de este invernadero. Si ya había uno,
     * el anterior deja de poder subir datos. Se usa una instancia aparte de Firebase para no cerrar tu sesión.
     */
    suspend fun vincularEsp32(u: Usuario): ConfigEsp32 {
        if (u.invitado) throw ErrorNube("Los invitados no pueden vincular el ESP32")
        val opciones = FirebaseApp.getInstance().options
        val url = opciones.databaseUrl
            ?: throw ErrorNube("Falta la URL de la Realtime Database: créala y descarga de nuevo google-services.json.")
        val aparte = FirebaseApp.getApps(context).firstOrNull { it.name == "vincular-esp32" }
            ?: FirebaseApp.initializeApp(context, opciones, "vincular-esp32")
        val authEsp32 = FirebaseAuth.getInstance(aparte)
        val sufijo = String(CharArray(10) { ALFANUM.lowercase()[azar.nextInt(ALFANUM.length)] })
        val correo = "esp32-$sufijo@dispositivo.greenbox.local"
        val clave = String(CharArray(24) { ALFANUM[azar.nextInt(ALFANUM.length)] })
        val creado: String? = try {
            authEsp32.createUserWithEmailAndPassword(correo, clave).await().user?.uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ErrorNube(mensajeAuth(e))
        } finally {
            authEsp32.signOut()
        }
        val uidEsp32 = creado ?: throw ErrorNube("No se pudo crear la cuenta del ESP32")
        conTiempo { ref("invernaderos/${u.invernadero}/dispositivo").setValue(uidEsp32).await() }
        return ConfigEsp32(opciones.apiKey, url.trimEnd('/'), u.invernadero, correo, clave)
    }

    /** Último estado que subió el ESP32 (se actualiza solo). null = aún no sube nada. */
    fun estado(inv: String): Flow<LecturaEsp32?> = escuchar({ ref("invernaderos/$inv/estado") }) { s ->
        if (s.exists()) lecturaDeNube(s) else null
    }

    /** Resúmenes de 5 min desde [desde]. Tras la primera carga solo baja lo nuevo. */
    fun resumenes(inv: String, desde: Long): Flow<List<RegistroLectura>> =
        escuchar({ ref("invernaderos/$inv/resumenes").orderByChild("ts").startAt(desde.toDouble()) }) { s ->
            s.children.mapNotNull { r ->
                val ts = num(r, "ts")?.toLong() ?: return@mapNotNull null
                val hs = r.child("humedades").children.mapNotNull { (it.value as? Number)?.toFloat() }
                RegistroLectura(
                    ts = ts,
                    temperatura = num(r, "tProm")?.toFloat() ?: 0f,
                    humedadAire = num(r, "haProm")?.toFloat(),
                    humedadSuelo = num(r, "hsProm")?.toFloat() ?: (if (hs.isEmpty()) 0f else hs.average().toFloat()),
                    pwm = num(r, "pwmProm")?.toInt() ?: 0,
                    ventana = num(r, "ventanaProm")?.toFloat() ?: 0f,
                    humedades = hs
                )
            }.sortedBy { it.ts }
        }

    /** Los últimos avisos del ESP32, del más nuevo al más viejo. */
    fun alertas(inv: String, cuantas: Int = 10): Flow<List<AlertaNube>> =
        escuchar({ ref("invernaderos/$inv/alertas").orderByChild("ts").limitToLast(cuantas) }) { s ->
            s.children.mapNotNull { a ->
                AlertaNube(
                    ts = num(a, "ts")?.toLong() ?: return@mapNotNull null,
                    tipo = a.child("tipo").getValue(String::class.java) ?: "",
                    detalle = a.child("detalle").getValue(String::class.java) ?: ""
                )
            }.sortedByDescending { it.ts }
        }

    private fun <T> escuchar(consulta: () -> Query, convertir: (DataSnapshot) -> T): Flow<T> = callbackFlow {
        val q = consulta()
        val oyente = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(convertir(s)) }
            override fun onCancelled(e: DatabaseError) { close(ErrorNube(mensajeBase(e))) }
        }
        q.addValueEventListener(oyente)
        awaitClose { q.removeEventListener(oyente) }
    }

    private fun num(s: DataSnapshot, clave: String): Double? = (s.child(clave).value as? Number)?.toDouble()

    private fun lecturaDeNube(s: DataSnapshot): LecturaEsp32 {
        val pwm = num(s, "pwm") ?: 0.0
        val p = s.child("parametros")
        return LecturaEsp32(
            humedades = s.child("humedades").children.mapNotNull { (it.value as? Number)?.toFloat() },
            temperatura = num(s, "temperatura")?.toFloat() ?: 0f,
            ventiladorEncendido = pwm > 0,
            anguloVentana = num(s, "ventana")?.toFloat() ?: 0f,
            velocidadVentilador = (pwm / 255.0).toFloat(),
            humedadAmbiente = num(s, "humedadAire")?.toFloat(),
            estadosSuelo = s.child("estadosSuelo").children.mapNotNull { it.getValue(String::class.java) },
            modoVentilador = s.child("modoVentilador").getValue(String::class.java) ?: "AUTO",
            modoVentana = s.child("modoVentana").getValue(String::class.java) ?: "AUTO",
            parametros = if (!p.exists()) null else ParametrosControl(
                ventiladorSobre = num(p, "ventiladorSobre")?.toInt() ?: 25,
                ventanaDesde = num(p, "ventanaDesde")?.toInt() ?: 20,
                ventanaHasta = num(p, "ventanaHasta")?.toInt() ?: 27,
                humedadMaxima = num(p, "humedadMaxima")?.toInt() ?: 85
            ),
            recibidoEn = num(s, "ts")?.toLong() ?: 0L,
            desdeNube = true
        )
    }

    // ───────────────────────── Auditoría y limpieza ─────────────────────────

    private fun espejarAuditoria(usuario: String, accion: String, detalle: String, resultado: String) {
        val u = actual ?: return
        val yo = auth?.currentUser ?: return
        if (yo.uid != u.uid || u.invernadero.isEmpty()) return
        runCatching {
            // Sin esperar respuesta: si no hay Internet, Firebase lo guarda y lo sube después
            ref("invernaderos/${u.invernadero}/auditoria").push().setValue(
                mapOf(
                    "ts" to ServerValue.TIMESTAMP, "uid" to yo.uid,
                    "usuario" to usuario.take(60).ifBlank { "?" }, "accion" to accion.take(20).ifBlank { "?" },
                    "detalle" to detalle.take(200), "resultado" to resultado.take(60).ifBlank { "?" }
                )
            )
        }
    }

    /** Minimización de datos: borra resúmenes y avisos de más de 30 días y pases vencidos hace más de 7. */
    private suspend fun limpiarViejos(u: Usuario) {
        if (u.invitado || u.invernadero.isEmpty()) return
        val base = "invernaderos/${u.invernadero}"
        val corte = System.currentTimeMillis() - DIAS_EN_NUBE * 86_400_000L
        for (nodo in listOf("resumenes", "alertas")) {
            val viejos = leerServidor(ref("$base/$nodo").orderByChild("ts").endAt(corte.toDouble()).limitToFirst(500))
            val borrar = viejos.children.mapNotNull { it.key }.associateWith { null }
            if (borrar.isNotEmpty()) ref("$base/$nodo").updateChildren(borrar).await()
        }
        val semana = System.currentTimeMillis() - 7 * 86_400_000L
        val pases = leerServidor(ref("$base/pases"))
        val vencidos = pases.children.filter { (it.child("vence").getValue(Long::class.java) ?: 0L) < semana }.mapNotNull { it.key }.toSet()
        if (vencidos.isEmpty()) return
        val visitas = leerServidor(ref("$base/visitas"))
        vencidos.forEach { h ->
            ref("$base/pases/$h").removeValue().await()
            runCatching { ref("pasesIndice/$h").removeValue().await() }
        }
        visitas.children.filter { v -> v.child("pase").getValue(String::class.java)?.let { it in vencidos } == true }.forEach { v ->
            v.key?.let { ref("$base/visitas/$it").removeValue().await() }
        }
    }
}
