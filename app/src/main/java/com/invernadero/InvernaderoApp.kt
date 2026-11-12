package com.invernadero

import android.app.Application
import android.content.Context
import com.google.firebase.database.FirebaseDatabase
import com.invernadero.conexion.ControladorConexion
import com.invernadero.datos.AjustesRepo
import com.invernadero.datos.BaseDatos
import com.invernadero.datos.PlantasRepo
import com.invernadero.datos.Sesion
import com.invernadero.datos.Usuario
import com.invernadero.nube.Nube
import com.invernadero.seguridad.CajaFuerte
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Se crea una sola vez al abrir la app (no se reinicia al girar la pantalla),
 * así la conexión con el ESP32 y la sesión se mantienen.
 */
class InvernaderoApp : Application() {
    val contenedor by lazy { Contenedor(this) }

    override fun onCreate() {
        super.onCreate()
        // La Realtime Database guarda una copia en el teléfono: la app sigue mostrando lo último
        // que bajó de la nube y encola los cambios aunque no haya Internet. Debe activarse antes de usarla.
        runCatching { FirebaseDatabase.getInstance().setPersistenceEnabled(true) }
    }
}

/** Todas las piezas de la app en un solo lugar (inyección de dependencias manual). */
class Contenedor(context: Context) {
    val db = BaseDatos(context)
    val caja = CajaFuerte(context)
    val ajustes = AjustesRepo(caja)
    val plantas = PlantasRepo(caja)
    val nube = Nube(context, db, caja)
    val sesion = Sesion()
    val conexion = ControladorConexion(context, db, ajustes, sesion, nube)

    /** Cierra la sesión en todas partes: auditoría (local y nube), conexión, Firebase y memoria. */
    suspend fun cerrarSesion(u: Usuario, accion: String, motivo: String) {
        withContext(Dispatchers.IO) { db.auditar(u.nombre, accion, motivo, "OK") }   // antes de salir, para que llegue a la nube
        conexion.detener()
        withContext(Dispatchers.IO) { nube.salir() }
        sesion.cerrar()
    }
}
