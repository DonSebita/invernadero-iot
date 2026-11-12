package com.invernadero

import android.app.Application
import android.content.Context
import com.invernadero.conexion.ControladorConexion
import com.invernadero.datos.AjustesRepo
import com.invernadero.datos.BaseDatos
import com.invernadero.datos.InvitacionesRepo
import com.invernadero.datos.Sesion
import com.invernadero.datos.SesionRecordada
import com.invernadero.datos.UsuariosRepo
import com.invernadero.seguridad.CajaFuerte

/**
 * Se crea una sola vez al abrir la app (no se reinicia al girar la pantalla),
 * así la conexión con el ESP32 y la sesión se mantienen.
 */
class InvernaderoApp : Application() {
    val contenedor by lazy { Contenedor(this) }
}

/** Todas las piezas de la app en un solo lugar (inyección de dependencias manual). */
class Contenedor(context: Context) {
    val db = BaseDatos(context)
    val caja = CajaFuerte(context)
    val ajustes = AjustesRepo(caja)
    val usuarios = UsuariosRepo(db)
    val invitaciones = InvitacionesRepo(db)
    val sesion = Sesion()
    val recordar = SesionRecordada(caja, usuarios)
    val conexion = ControladorConexion(context, db, ajustes, sesion)
}
