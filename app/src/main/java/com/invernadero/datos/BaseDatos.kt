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
    val ventana: Float,
    val humedades: List<Float> = emptyList()   // una por planta (sensor)
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
 *  - invitaciones: pases de visita temporales (solo se guarda el hash de la clave)
 * Todas las consultas usan parámetros (?) para evitar inyección SQL.
 */
class BaseDatos(context: Context) : SQLiteOpenHelper(context, "invernadero.db", null, 3) {

    companion object {
        /**
         * Junta el historial de este teléfono con los resúmenes de 5 min que el ESP32 subió a la nube.
         * Donde hay datos locales (más detallados) se usan esos; la nube rellena los huecos
         * (p. ej. en otro teléfono, o mientras este estuvo lejos del invernadero).
         */
        fun combinar(locales: List<RegistroLectura>, nube: List<RegistroLectura>, margenMs: Long = 5 * 60_000L): List<RegistroLectura> {
            if (nube.isEmpty()) return locales
            val tiempos = locales.map { it.ts }.sorted()
            fun hayLocalCerca(ts: Long): Boolean {
                var i = tiempos.binarySearch(ts)
                if (i < 0) i = -i - 1
                return (i < tiempos.size && tiempos[i] - ts <= margenMs) || (i > 0 && ts - tiempos[i - 1] <= margenMs)
            }
            return (locales + nube.filter { !hayLocalCerca(it.ts) }).sortedBy { it.ts }
        }

        /** Agrupa en a lo más [puntos] tramos (promedio de cada tramo) para dibujar sin cargar miles de puntos. */
        fun agrupar(todas: List<RegistroLectura>, puntos: Int = 120): List<RegistroLectura> {
            if (todas.size <= puntos) return todas
            val tramo = (todas.size + puntos - 1) / puntos
            return todas.chunked(tramo).map { g ->
                val sensores = g.maxOf { it.humedades.size }
                RegistroLectura(
                    ts = g.last().ts,
                    temperatura = g.map { it.temperatura }.average().toFloat(),
                    humedadAire = g.mapNotNull { it.humedadAire }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                    humedadSuelo = g.map { it.humedadSuelo }.average().toFloat(),
                    pwm = g.map { it.pwm }.average().toInt(),
                    ventana = g.map { it.ventana }.average().toFloat(),
                    humedades = List(sensores) { i -> g.mapNotNull { it.humedades.getOrNull(i) }.average().toFloat() }
                )
            }
        }
    }

    /**
     * Copia de cada registro de auditoría hacia la nube (la pone la capa Nube al iniciar).
     * Si no hay sesión en la nube no hace nada; el registro local se guarda igual.
     */
    @Volatile var espejo: ((usuario: String, accion: String, detalle: String, resultado: String) -> Unit)? = null

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
                creado INTEGER NOT NULL,
                recuperacion_hash BLOB,
                recuperacion_sal BLOB,
                recordar_hash BLOB,
                recordar_vence INTEGER NOT NULL DEFAULT 0)"""
        )
        crearInvitaciones(db)
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v2: semilla de recuperación por cuenta + pases de visita
            db.execSQL("ALTER TABLE usuarios ADD COLUMN recuperacion_hash BLOB")
            db.execSQL("ALTER TABLE usuarios ADD COLUMN recuperacion_sal BLOB")
            crearInvitaciones(db)
        }
        if (oldVersion < 3) {
            // v3: "Recordarme en este teléfono"
            db.execSQL("ALTER TABLE usuarios ADD COLUMN recordar_hash BLOB")
            db.execSQL("ALTER TABLE usuarios ADD COLUMN recordar_vence INTEGER NOT NULL DEFAULT 0")
        }
    }

    private fun crearInvitaciones(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS invitaciones(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                codigo_hash BLOB NOT NULL UNIQUE,
                creador TEXT NOT NULL,
                puede_activar INTEGER NOT NULL,
                creada INTEGER NOT NULL,
                vence INTEGER NOT NULL,
                revocada INTEGER NOT NULL DEFAULT 0,
                usos INTEGER NOT NULL DEFAULT 0,
                ultimo_visitante TEXT)"""
        )
    }

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

    /**
     * Lecturas desde [desde] hasta ahora, agrupadas en a lo más [puntos] tramos (promedio de cada tramo)
     * para dibujar la evolución de las plantas sin cargar miles de puntos en la gráfica.
     */
    fun lecturasDesde(desde: Long, puntos: Int = 120): List<RegistroLectura> = agrupar(lecturasCrudasDesde(desde), puntos)

    /** Todas las lecturas guardadas desde [desde], sin agrupar, de la más vieja a la más nueva. */
    fun lecturasCrudasDesde(desde: Long): List<RegistroLectura> =
        readableDatabase.rawQuery(
            "SELECT ts, temperatura, humedad_aire, humedades, pwm, ventana FROM lecturas WHERE ts >= ? ORDER BY ts",
            arrayOf(desde.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val hs = c.getString(3).orEmpty().split(";").mapNotNull { it.toFloatOrNull() }
                    add(RegistroLectura(c.getLong(0), c.getFloat(1), if (c.isNull(2)) null else c.getFloat(2),
                        if (hs.isEmpty()) 0f else hs.average().toFloat(), c.getInt(4), c.getFloat(5), hs))
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
        runCatching { espejo?.invoke(usuario.take(40), accion, detalle.take(200), resultado) }
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

    /** Eventos de acceso (para el panel del administrador). */
    fun auditoriaDeAcceso(n: Int): List<RegistroAuditoria> =
        readableDatabase.rawQuery(
            "SELECT ts, usuario, accion, detalle, resultado FROM auditoria " +
                "WHERE accion IN ('LOGIN','USUARIO','INVITADO','RECUPERAR','SESION') ORDER BY ts DESC LIMIT ?",
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
