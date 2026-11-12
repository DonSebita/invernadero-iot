package com.invernadero.datos

import android.content.ContentValues
import android.database.DatabaseUtils
import com.invernadero.seguridad.Contrasenas

/** Control de acceso por roles (principio de mínimo privilegio). */
enum class Rol(val etiqueta: String) {
    ADMIN("Administrador"),      // todo + gestionar usuarios y ajustes
    OPERADOR("Operador"),        // monitorear y activar
    VISOR("Solo lectura");       // solo monitorear

    val puedeActivar get() = this != VISOR
    val puedeAdministrar get() = this == ADMIN
}

data class Usuario(val id: Long, val nombre: String, val rol: Rol)

sealed interface ResultadoLogin {
    data class Correcto(val usuario: Usuario) : ResultadoLogin
    data class Bloqueado(val segundos: Long) : ResultadoLogin
    data object Incorrecto : ResultadoLogin
}

/** Llamar siempre fuera del hilo principal (el hash tarda a propósito). */
class UsuariosRepo(private val db: BaseDatos) {

    companion object {
        const val MAX_INTENTOS = 5
        const val BLOQUEO_MS = 5 * 60_000L
        private val SAL_FALSA = ByteArray(16)
    }

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
        return if (db.writableDatabase.insert("usuarios", null, cv) == -1L) "Ese usuario ya existe" else null
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
            val intentos = c.getInt(6) + 1
            if (intentos >= MAX_INTENTOS) {
                actualizarIntentos(id, 0, ahora + BLOQUEO_MS)
                return ResultadoLogin.Bloqueado(BLOQUEO_MS / 1000)
            }
            actualizarIntentos(id, intentos, 0)
            return ResultadoLogin.Incorrecto
        }
    }

    private fun actualizarIntentos(id: Long, intentos: Int, bloqueadoHasta: Long) {
        val cv = ContentValues().apply {
            put("intentos", intentos)
            put("bloqueado_hasta", bloqueadoHasta)
        }
        db.writableDatabase.update("usuarios", cv, "id = ?", arrayOf(id.toString()))
    }

    fun listar(): List<Usuario> =
        db.readableDatabase.rawQuery("SELECT id, usuario, rol FROM usuarios ORDER BY usuario", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Usuario(c.getLong(0), c.getString(1), runCatching { Rol.valueOf(c.getString(2)) }.getOrDefault(Rol.VISOR)))
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
}
