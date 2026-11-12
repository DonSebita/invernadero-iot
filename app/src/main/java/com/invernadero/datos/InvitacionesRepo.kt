package com.invernadero.datos

import android.content.ContentValues
import java.security.MessageDigest
import java.security.SecureRandom

/** Un pase de visita: alguien de confianza entra sin cuenta durante un tiempo limitado. */
data class Invitacion(
    val id: Long,
    val creador: String,
    val puedeActivar: Boolean,
    val creada: Long,
    val vence: Long,
    val usos: Int,
    val ultimoVisitante: String?
)

sealed interface ResultadoInvitado {
    data class Correcto(val usuario: Usuario) : ResultadoInvitado
    data class Bloqueado(val segundos: Long) : ResultadoInvitado
    data object Incorrecto : ResultadoInvitado
}

/**
 * Pases de visita temporales.
 *  - La clave (8 caracteres al azar ≈ 40 bits) se muestra una sola vez; en la base solo queda su SHA-256.
 *  - 5 claves erróneas seguidas bloquean el acceso de invitados por 5 minutos (en todo el teléfono).
 *  - El pase vence solo, y su creador o un administrador puede revocarlo en cualquier momento:
 *    la sesión del invitado se cierra en menos de 15 s.
 *  - Un invitado nunca recibe más permisos que quien lo invitó, y nunca el de administrador.
 * Llamar fuera del hilo principal.
 */
class InvitacionesRepo(private val db: BaseDatos) {

    companion object {
        const val MAX_ACTIVAS_POR_PERSONA = 5
        const val MAX_INTENTOS = 5
        const val BLOQUEO_MS = 5 * 60_000L
        private const val ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

        fun normalizar(codigo: String): String = codigo.uppercase().filter { it in ALFABETO }

        /** Nombre visible del visitante: 2 a 24 letras, espacios o números. */
        fun validarVisitante(nombre: String): String? =
            if (nombre.length !in 2..24 || !nombre.all { it.isLetterOrDigit() || it == ' ' })
                "Escribe tu nombre (2 a 24 letras) para que tu anfitrión sepa quién pasó por aquí"
            else null
    }

    private val azar = SecureRandom()
    private var fallos = 0
    private var bloqueadoHasta = 0L

    private fun hash(normal: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(normal.toByteArray())

    /** Crea un pase y devuelve la clave en formato XXXX-XXXX, o un error si ya tiene demasiados activos. */
    fun crear(creador: String, duracionMs: Long, puedeActivar: Boolean): Result<String> {
        if (activas(creador).size >= MAX_ACTIVAS_POR_PERSONA) {
            return Result.failure(IllegalStateException("Ya tienes $MAX_ACTIVAS_POR_PERSONA pases abiertos. Revoca uno antes de crear otro."))
        }
        val ahora = System.currentTimeMillis()
        repeat(5) {
            val codigo = String(CharArray(8) { ALFABETO[azar.nextInt(ALFABETO.length)] })
            val cv = ContentValues().apply {
                put("codigo_hash", hash(codigo))
                put("creador", creador)
                put("puede_activar", if (puedeActivar) 1 else 0)
                put("creada", ahora)
                put("vence", ahora + duracionMs)
            }
            if (db.writableDatabase.insert("invitaciones", null, cv) != -1L) {
                return Result.success(codigo.chunked(4).joinToString("-"))
            }
        }
        return Result.failure(IllegalStateException("No se pudo crear el pase. Inténtalo de nuevo."))
    }

    @Synchronized
    fun entrar(visitante: String, codigo: String): ResultadoInvitado {
        val ahora = System.currentTimeMillis()
        if (bloqueadoHasta > ahora) return ResultadoInvitado.Bloqueado((bloqueadoHasta - ahora) / 1000 + 1)

        val normal = normalizar(codigo)
        val fila = if (normal.length == 8) buscarPorHash(hash(normal), ahora) else null
        if (fila == null) {
            fallos++
            if (fallos >= MAX_INTENTOS) {
                fallos = 0
                bloqueadoHasta = ahora + BLOQUEO_MS
                return ResultadoInvitado.Bloqueado(BLOQUEO_MS / 1000)
            }
            return ResultadoInvitado.Incorrecto
        }
        fallos = 0
        val cv = ContentValues().apply {
            put("usos", fila.usos + 1)
            put("ultimo_visitante", visitante)
        }
        db.writableDatabase.update("invitaciones", cv, "id = ?", arrayOf(fila.id.toString()))
        val rol = if (fila.puedeActivar) Rol.OPERADOR else Rol.VISOR
        return ResultadoInvitado.Correcto(
            Usuario(id = -fila.id, nombre = "$visitante (invitado)", rol = rol, invitado = true, vence = fila.vence, anfitrion = fila.creador)
        )
    }

    private fun buscarPorHash(h: ByteArray, ahora: Long): Invitacion? {
        // SQLite compara BLOB con BLOB: se pasa como literal hexadecimal X'..' generado aquí (no viene del usuario)
        val hex = h.joinToString("") { "%02x".format(it) }
        return db.readableDatabase.rawQuery(
            "SELECT id, creador, puede_activar, creada, vence, usos, ultimo_visitante FROM invitaciones " +
                "WHERE codigo_hash = X'$hex' AND revocada = 0 AND vence > ?",
            arrayOf(ahora.toString())
        ).use { c -> if (c.moveToFirst()) leer(c) else null }
    }

    private fun leer(c: android.database.Cursor) = Invitacion(
        id = c.getLong(0), creador = c.getString(1), puedeActivar = c.getInt(2) == 1,
        creada = c.getLong(3), vence = c.getLong(4), usos = c.getInt(5),
        ultimoVisitante = if (c.isNull(6)) null else c.getString(6)
    )

    /** Pases vigentes; de una persona o (creador = null) de todo el invernadero. */
    fun activas(creador: String? = null): List<Invitacion> {
        val ahora = System.currentTimeMillis().toString()
        val (filtro, args) = if (creador == null) "" to arrayOf(ahora) else " AND creador = ?" to arrayOf(ahora, creador)
        return db.readableDatabase.rawQuery(
            "SELECT id, creador, puede_activar, creada, vence, usos, ultimo_visitante FROM invitaciones " +
                "WHERE revocada = 0 AND vence > ?$filtro ORDER BY vence",
            args
        ).use { c -> buildList { while (c.moveToNext()) add(leer(c)) } }
    }

    /** Solo el creador o un administrador pueden revocar. */
    fun revocar(id: Long, quien: Usuario): Boolean {
        val cv = ContentValues().apply { put("revocada", 1) }
        val n = if (quien.rol.puedeAdministrar && !quien.invitado) {
            db.writableDatabase.update("invitaciones", cv, "id = ?", arrayOf(id.toString()))
        } else {
            db.writableDatabase.update("invitaciones", cv, "id = ? AND creador = ?", arrayOf(id.toString(), quien.nombre))
        }
        return n > 0
    }

    /** ¿Sigue abierto el pase con el que entró este invitado? */
    fun vigente(idPase: Long): Boolean =
        db.readableDatabase.rawQuery(
            "SELECT 1 FROM invitaciones WHERE id = ? AND revocada = 0 AND vence > ?",
            arrayOf(idPase.toString(), System.currentTimeMillis().toString())
        ).use { it.moveToFirst() }
}
