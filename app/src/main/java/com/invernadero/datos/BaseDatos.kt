package com.invernadero.datos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.invernadero.conexion.LecturaEsp32

data class RegistroLectura(
    val ts: Long,
    val temperatura: Float,
    val humedadAire: Float?,
    val humedadSuelo: Float,     // promedio de los sensores
    val pwm: Int,
    val ventana: Float
)

data class RegistroAuditoria(
    val ts: Long,
    val usuario: String,
    val accion: String,
    val detalle: String,
    val resultado: String
)

/**
 * Base de datos local SQLite.
 *  - usuarios:   credenciales (solo hash + sal), rol, intentos fallidos, bloqueo
 *  - lecturas:   historial de sensores (1 cada 30 s, se borra lo de más de 7 días)
 *  - auditoria:  quién hizo qué y cuándo (login, comandos, cambios de ajustes)
 * Todas las consultas usan parámetros (?) para evitar inyección SQL.
 */
class BaseDatos(context: Context) : SQLiteOpenHelper(context, "invernadero.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE usuarios(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                usuario TEXT NOT NULL UNIQUE COLLATE NOCASE,
                hash BLOB NOT NULL,
                sal BLOB NOT NULL,
                iteraciones INTEGER NOT NULL,
                rol TEXT NOT NULL,
                intentos INTEGER NOT NULL DEFAULT 0,
                bloqueado_hasta INTEGER NOT NULL DEFAULT 0,
                creado INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE lecturas(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                temperatura REAL,
                humedad_aire REAL,
                humedades TEXT,
                ventilador INTEGER,
                pwm INTEGER,
                ventana REAL,
                origen TEXT)"""
        )
        db.execSQL("CREATE INDEX idx_lecturas_ts ON lecturas(ts)")
        db.execSQL(
            """CREATE TABLE auditoria(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                usuario TEXT,
                accion TEXT,
                detalle TEXT,
                resultado TEXT)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ───── Lecturas ─────
    fun guardarLectura(l: LecturaEsp32, origen: String) {
        val cv = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("temperatura", l.temperatura)
            l.humedadAmbiente?.let { put("humedad_aire", it) }
            put("humedades", l.humedades.joinToString(";"))
            put("ventilador", if (l.ventiladorEncendido) 1 else 0)
            put("pwm", (l.velocidadVentilador * 255).toInt())
            put("ventana", l.anguloVentana)
            put("origen", origen)
        }
        val w = writableDatabase
        w.insert("lecturas", null, cv)
        // Minimización de datos: solo se guardan 7 días
        w.delete("lecturas", "ts < ?", arrayOf((System.currentTimeMillis() - 7L * 24 * 3600 * 1000).toString()))
    }

    fun ultimasLecturas(n: Int): List<RegistroLectura> =
        readableDatabase.rawQuery(
            "SELECT ts, temperatura, humedad_aire, humedades, pwm, ventana FROM lecturas ORDER BY ts DESC LIMIT ?",
            arrayOf(n.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val hs = c.getString(3).orEmpty().split(";").mapNotNull { it.toFloatOrNull() }
                    add(
                        RegistroLectura(
                            ts = c.getLong(0),
                            temperatura = c.getFloat(1),
                            humedadAire = if (c.isNull(2)) null else c.getFloat(2),
                            humedadSuelo = if (hs.isEmpty()) 0f else hs.average().toFloat(),
                            pwm = c.getInt(4),
                            ventana = c.getFloat(5)
                        )
                    )
                }
            }
        }

    // ───── Auditoría ─────
    fun auditar(usuario: String, accion: String, detalle: String, resultado: String) {
        val cv = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("usuario", usuario.take(40))
            put("accion", accion)
            put("detalle", detalle.take(200))
            put("resultado", resultado)
        }
        writableDatabase.insert("auditoria", null, cv)
    }

    fun ultimaAuditoria(n: Int): List<RegistroAuditoria> =
        readableDatabase.rawQuery(
            "SELECT ts, usuario, accion, detalle, resultado FROM auditoria ORDER BY ts DESC LIMIT ?",
            arrayOf(n.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(RegistroAuditoria(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(),
                        c.getString(3).orEmpty(), c.getString(4).orEmpty()))
                }
            }
        }
}
