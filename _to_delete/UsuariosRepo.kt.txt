package com.invernadero.datos

import android.content.ContentValues
import android.database.DatabaseUtils
import com.invernadero.seguridad.Contrasenas
import java.security.MessageDigest
import java.security.SecureRandom

/** ID visible de una cuenta (GB-0001). Sirve para recuperarla aunque cambie de nombre o no lo recuerdes exacto. */
fun idCuenta(id: Long): String = "GB-" + id.toString().padStart(4, '0')

/** "GB-0007", "gb7", "GB 0007" → 7. Null si no parece un ID de cuenta. */
fun parsearIdCuenta(texto: String): Long? =
    Regex("^GB[- ]?0*(\\d{1,9})$", RegexOption.IGNORE_CASE).find(texto.trim())?.groupValues?.get(1)?.toLongOrNull()

/** Deja un nombre con espacios simples y sin espacios al borde. */
fun normalizarNombre(texto: String): String = texto.trim().replace(Regex("\\s+"), " ")

/** Control de acceso por roles (principio de mínimo privilegio). */
enum class Rol(val etiqueta: String) {
    ADMIN("Administrador"),      // todo + gestionar cuentas y ajustes
    OPERADOR("Operador"),        // monitorear y activar
    VISOR("Solo lectura");       // solo monitorear

    val puedeActivar get() = this != VISOR
    val puedeAdministrar get() = this == ADMIN
}

/**
 * Quien está usando la app. Un invitado no tiene cuenta: entra con un pase de visita temporal
 * (id negativo = -id del pase), con el rol que le dio su anfitrión y una hora de término.
 */
data class Usuario(
    val id: Long,
    val nombre: String,
    val rol: Rol,
    val invitado: Boolean = false,
    val vence: Long = 0L,
    val anfitrion: String? = null
) {
    /** ID visible de la cuenta; los invitados no tienen. */
    val idVisible: String? get() = if (invitado) null else idCuenta(id)
}

/** Datos de una cuenta para el panel del administrador (nunca incluye hashes). */
data class CuentaInfo(
    val usuario: Usuario,
    val creado: Long,
    val bloqueadoHasta: Long,
    val tieneSemilla: Boolean
) {
    val bloqueada get() = bloqueadoHasta > System.currentTimeMillis()
}

sealed interface ResultadoLogin {
    data class Correcto(val usuario: Usuario) : ResultadoLogin
    data class Bloqueado(val segundos: Long) : ResultadoLogin
    data object Incorrecto : ResultadoLogin
}

sealed interface ResultadoRecuperacion {
    /** La semilla usada se consume: se entrega una nueva que se muestra una sola vez. */
    data class Correcto(val nombre: String, val nuevaSemilla: String) : ResultadoRecuperacion
    data class Bloqueado(val segundos: Long) : ResultadoRecuperacion
    data class Invalida(val mensaje: String) : ResultadoRecuperacion
    data object Incorrecto : ResultadoRecuperacion
}

/** Llamar siempre fuera del hilo principal (el hash tarda a propósito). */
class UsuariosRepo(private val db: BaseDatos) {

    companion object {
        const val MAX_INTENTOS = 5
        const val RECORDAR_MS = 30L * 24 * 3600 * 1000      // "Recordarme": 30 días desde el último uso
        const val BLOQUEO_MS = 5 * 60_000L
        private val SAL_FALSA = ByteArray(16)

        /** Sin 0/O, 1/I/L para que se pueda dictar o copiar a mano sin errores. */
        private const val ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

        /** Árboles y arbustos nativos de Chile: la semilla empieza con uno para que sea fácil de reconocer. */
        private val ESPECIES = listOf(
            "QUILLAY", "PEUMO", "BOLDO", "CANELO", "COIHUE", "LINGUE", "MAITEN",
            "ARRAYAN", "ULMO", "RAULI", "LAUREL", "LITRE", "MOLLE", "ALERCE"
        )

        /** Quita guiones/espacios y pasa a mayúsculas: "quillay 7k4p…" == "QUILLAY-7K4P-…". */
        fun normalizarSemilla(texto: String): String = texto.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }
    }

    private val azar = SecureRandom()

    fun hayUsuarios(): Boolean = DatabaseUtils.queryNumEntries(db.readableDatabase, "usuarios") > 0

    /** Devuelve un mensaje de error o null si se creó. */
    fun registrar(nombre: String, clave: CharArray, rol: Rol): String? {
        Contrasenas.validarPolitica(nombre, String(clave))?.let { return it }
        val sal = Contrasenas.nuevaSal()
        val cv = ContentValues().apply {
            put("usuario", nombre)
            put("hash", Contrasenas.hash(clave, sal))
            put("sal", sal)
            put("iteraciones", Contrasenas.ITERACIONES)
            put("rol", rol.name)
            put("creado", System.currentTimeMillis())
        }
        return if (db.writableDatabase.insert("usuarios", null, cv) == -1L) "Ese nombre ya florece en este invernadero. Prueba con otro." else null
    }

    /**
     * Crear cuenta desde el acceso. Por ahora GreenBox es solo para usuarios: toda cuenta nueva
     * puede ver y controlar sus plantas (rol Operador). Devuelve el rol asignado o el error.
     */
    fun registrarDesdeAcceso(nombre: String, clave: CharArray): Result<Rol> {
        val rol = Rol.OPERADOR
        val e = registrar(nombre, clave, rol)
        return if (e == null) Result.success(rol) else Result.failure(IllegalArgumentException(e))
    }

    fun buscar(nombre: String): Usuario? =
        db.readableDatabase.rawQuery("SELECT id, usuario, rol FROM usuarios WHERE usuario = ?", arrayOf(nombre)).use { c ->
            if (!c.moveToFirst()) null
            else Usuario(c.getLong(0), c.getString(1), runCatching { Rol.valueOf(c.getString(2)) }.getOrDefault(Rol.VISOR))
        }

    fun login(nombre: String, clave: CharArray): ResultadoLogin {
        val w = db.writableDatabase
        w.rawQuery(
            "SELECT id, usuario, hash, sal, iteraciones, rol, intentos, bloqueado_hasta FROM usuarios WHERE usuario = ?",
            arrayOf(nombre)
        ).use { c ->
            if (!c.moveToFirst()) {
                // Se calcula un hash igual para que no se note (por tiempo) si el usuario existe
                Contrasenas.hash(clave, SAL_FALSA)
                return ResultadoLogin.Incorrecto
            }
            val id = c.getLong(0)
            val ahora = System.currentTimeMillis()
            val bloqueadoHasta = c.getLong(7)
            if (bloqueadoHasta > ahora) return ResultadoLogin.Bloqueado((bloqueadoHasta - ahora) / 1000 + 1)

            if (Contrasenas.verificar(clave, c.getBlob(3), c.getBlob(2), c.getInt(4))) {
                actualizarIntentos(id, 0, 0)
                val rol = runCatching { Rol.valueOf(c.getString(5)) }.getOrDefault(Rol.VISOR)
                return ResultadoLogin.Correcto(Usuario(id, c.getString(1), rol))
            }
            return registrarFallo(id, c.getInt(6), ahora)
        }
    }

    private fun registrarFallo(id: Long, intentosPrevios: Int, ahora: Long): ResultadoLogin {
        val intentos = intentosPrevios + 1
        if (intentos >= MAX_INTENTOS) {
            actualizarIntentos(id, 0, ahora + BLOQUEO_MS)
            return ResultadoLogin.Bloqueado(BLOQUEO_MS / 1000)
        }
        actualizarIntentos(id, intentos, 0)
        return ResultadoLogin.Incorrecto
    }

    private fun actualizarIntentos(id: Long, intentos: Int, bloqueadoHasta: Long) {
        val cv = ContentValues().apply {
            put("intentos", intentos)
            put("bloqueado_hasta", bloqueadoHasta)
        }
        db.writableDatabase.update("usuarios", cv, "id = ?", arrayOf(id.toString()))
    }

    // ───── Semilla de recuperación (funciona sin Internet ni correo) ─────

    /**
     * Genera una semilla nueva para la cuenta (la anterior deja de servir) y devuelve el texto
     * para mostrarlo UNA sola vez. En la base solo queda su hash PBKDF2, igual que la contraseña.
     * Formato: ESPECIE-XXXX-XXXX-XXXX (12 caracteres al azar ≈ 59 bits).
     */
    fun nuevaSemilla(nombre: String): String {
        val grupos = List(3) { String(CharArray(4) { ALFABETO[azar.nextInt(ALFABETO.length)] }) }
        val semilla = (listOf(ESPECIES[azar.nextInt(ESPECIES.size)]) + grupos).joinToString("-")
        val sal = Contrasenas.nuevaSal()
        val normal = normalizarSemilla(semilla).toCharArray()
        val cv = ContentValues().apply {
            put("recuperacion_hash", Contrasenas.hash(normal, sal))
            put("recuperacion_sal", sal)
        }
        normal.fill('\u0000')
        db.writableDatabase.update("usuarios", cv, "usuario = ?", arrayOf(nombre))
        return semilla
    }

    /** "Olvidé mi contraseña": usuario + semilla → contraseña nueva. Comparte el contador de intentos con el login. */
    fun restablecerConSemilla(nombreOId: String, semilla: String, nueva: CharArray): ResultadoRecuperacion {
        val w = db.writableDatabase
        val idBuscado = parsearIdCuenta(nombreOId)
        w.rawQuery(
            "SELECT id, usuario, recuperacion_hash, recuperacion_sal, intentos, bloqueado_hasta FROM usuarios WHERE " +
                if (idBuscado != null) "id = ?" else "usuario = ?",
            arrayOf(idBuscado?.toString() ?: normalizarNombre(nombreOId))
        ).use { c ->
            val normal = normalizarSemilla(semilla).toCharArray()
            try {
                if (!c.moveToFirst() || c.isNull(2)) {
                    Contrasenas.hash(normal, SAL_FALSA)
                    return ResultadoRecuperacion.Incorrecto
                }
                val id = c.getLong(0)
                val nombreReal = c.getString(1)
                val ahora = System.currentTimeMillis()
                val bloqueadoHasta = c.getLong(5)
                if (bloqueadoHasta > ahora) return ResultadoRecuperacion.Bloqueado((bloqueadoHasta - ahora) / 1000 + 1)

                if (!Contrasenas.verificar(normal, c.getBlob(3), c.getBlob(2), Contrasenas.ITERACIONES)) {
                    return when (val r = registrarFallo(id, c.getInt(4), ahora)) {
                        is ResultadoLogin.Bloqueado -> ResultadoRecuperacion.Bloqueado(r.segundos)
                        else -> ResultadoRecuperacion.Incorrecto
                    }
                }
                Contrasenas.validarPolitica(nombreReal, String(nueva))?.let { return ResultadoRecuperacion.Invalida(it) }
                cambiarClave(id, nueva)
                return ResultadoRecuperacion.Correcto(nombreReal, nuevaSemilla(nombreReal))
            } finally {
                normal.fill('\u0000')
            }
        }
    }

    private fun cambiarClave(id: Long, clave: CharArray) {
        val sal = Contrasenas.nuevaSal()
        val cv = ContentValues().apply {
            put("hash", Contrasenas.hash(clave, sal))
            put("sal", sal)
            put("iteraciones", Contrasenas.ITERACIONES)
            put("intentos", 0)
            put("bloqueado_hasta", 0)
            putNull("recordar_hash")          // una contraseña nueva cierra las sesiones recordadas
            put("recordar_vence", 0)
        }
        db.writableDatabase.update("usuarios", cv, "id = ?", arrayOf(id.toString()))
    }

    // ───── "Recordarme en este teléfono" ─────

    private fun sha256(texto: String) = MessageDigest.getInstance("SHA-256").digest(texto.toByteArray())

    /** Crea un token al azar (256 bits). En la base queda solo su SHA-256; el token va cifrado en el Keystore. */
    fun recordar(id: Long): String {
        val bytes = ByteArray(32).also { azar.nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        val cv = ContentValues().apply {
            put("recordar_hash", sha256(token))
            put("recordar_vence", System.currentTimeMillis() + RECORDAR_MS)
        }
        db.writableDatabase.update("usuarios", cv, "id = ?", arrayOf(id.toString()))
        return token
    }

    /** Si el token es válido y no venció, devuelve la cuenta (con su rol actual) y renueva los 30 días. */
    fun sesionRecordada(id: Long, token: String): Usuario? {
        val ok = db.readableDatabase.rawQuery(
            "SELECT recordar_hash, recordar_vence, bloqueado_hasta FROM usuarios WHERE id = ?",
            arrayOf(id.toString())
        ).use { c ->
            c.moveToFirst() && !c.isNull(0) && c.getLong(1) > System.currentTimeMillis() &&
                c.getLong(2) <= System.currentTimeMillis() &&
                MessageDigest.isEqual(c.getBlob(0), sha256(token))
        }
        if (!ok) return null
        val cv = ContentValues().apply { put("recordar_vence", System.currentTimeMillis() + RECORDAR_MS) }
        db.writableDatabase.update("usuarios", cv, "id = ?", arrayOf(id.toString()))
        return listar().firstOrNull { it.id == id }
    }

    fun olvidar(id: Long) {
        val cv = ContentValues().apply { putNull("recordar_hash"); put("recordar_vence", 0) }
        db.writableDatabase.update("usuarios", cv, "id = ?", arrayOf(id.toString()))
    }

    // ───── Administración de cuentas ─────

    fun listar(): List<Usuario> = listarDetalle().map { it.usuario }

    fun listarDetalle(): List<CuentaInfo> =
        db.readableDatabase.rawQuery(
            "SELECT id, usuario, rol, creado, bloqueado_hasta, recuperacion_hash IS NOT NULL FROM usuarios ORDER BY usuario",
            null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val u = Usuario(c.getLong(0), c.getString(1), runCatching { Rol.valueOf(c.getString(2)) }.getOrDefault(Rol.VISOR))
                    add(CuentaInfo(u, c.getLong(3), c.getLong(4), c.getInt(5) == 1))
                }
            }
        }

    /** No deja borrar al último administrador. */
    fun eliminar(id: Long): String? {
        val lista = listar()
        val objetivo = lista.firstOrNull { it.id == id } ?: return "No existe"
        if (objetivo.rol == Rol.ADMIN && lista.count { it.rol == Rol.ADMIN } <= 1) return "Debe quedar al menos un administrador"
        db.writableDatabase.delete("usuarios", "id = ?", arrayOf(id.toString()))
        return null
    }

    /** No deja quitarle el rol al último administrador. */
    fun cambiarRol(id: Long, rol: Rol): String? {
        val lista = listar()
        val objetivo = lista.firstOrNull { it.id == id } ?: return "No existe"
        if (objetivo.rol == Rol.ADMIN && rol != Rol.ADMIN && lista.count { it.rol == Rol.ADMIN } <= 1) {
            return "Debe quedar al menos un administrador"
        }
        val cv = ContentValues().apply { put("rol", rol.name) }
        db.writableDatabase.update("usuarios", cv, "id = ?", arrayOf(id.toString()))
        return null
    }

    fun desbloquear(id: Long) = actualizarIntentos(id, 0, 0)

    /**
     * Para quien perdió contraseña y semilla: el administrador genera una contraseña temporal
     * (se muestra una sola vez) y la cuenta queda desbloqueada.
     */
    fun restablecerPorAdmin(id: Long): String {
        val letras = "abcdefghjkmnpqrstuvwxyz"
        val digitos = "23456789"
        val todo = letras + digitos
        var clave: CharArray
        do {
            clave = CharArray(10) { todo[azar.nextInt(todo.length)] }
        } while (clave.none { it.isDigit() } || clave.none { it.isLetter() })
        cambiarClave(id, clave)
        return String(clave).also { clave.fill('\u0000') }
    }
}
