package com.invernadero.datos

import com.invernadero.seguridad.CajaFuerte

/**
 * "Recordarme en este teléfono": guarda id + token cifrados con el Android Keystore.
 * El token no sirve en otro teléfono (la clave del Keystore no se puede extraer) y se invalida
 * al cerrar sesión, al cambiar la contraseña, al eliminar la cuenta o tras 30 días sin uso.
 * Llamar fuera del hilo principal.
 */
class SesionRecordada(private val caja: CajaFuerte, private val usuarios: UsuariosRepo) {

    fun guardar(u: Usuario) {
        if (u.invitado) return
        caja.guardar("recordar_id", u.id.toString())
        caja.guardar("recordar_token", usuarios.recordar(u.id))
    }

    /** Cuenta recordada válida, o null (y se limpia lo guardado). */
    fun restaurar(): Usuario? {
        val id = caja.leer("recordar_id")?.toLongOrNull() ?: return null
        val token = caja.leer("recordar_token") ?: return null
        return usuarios.sesionRecordada(id, token) ?: run { limpiar(); null }
    }

    fun olvidar(u: Usuario?) {
        if (u != null && !u.invitado) usuarios.olvidar(u.id)
        limpiar()
    }

    private fun limpiar() {
        caja.borrar("recordar_id")
        caja.borrar("recordar_token")
    }
}
