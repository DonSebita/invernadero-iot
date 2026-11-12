package com.invernadero.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.invernadero.Contenedor
import com.invernadero.datos.Usuario
import com.invernadero.ui.acceso.PantallaAcceso
import com.invernadero.ui.activador.PantallaActivador
import com.invernadero.ui.ajustes.PantallaAjustes
import com.invernadero.ui.historial.PantallaHistorial
import com.invernadero.ui.monitoreo.PantallaMonitoreo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun AppInvernadero(c: Contenedor) {
    var modoTarde by remember { mutableStateOf(c.ajustes.modoTarde) }
    val usuario by c.sesion.usuario.collectAsState()

    // Al iniciar sesión se conecta; la sesión se cierra sola tras 10 min sin uso
    LaunchedEffect(usuario) {
        val u = usuario ?: return@LaunchedEffect
        c.conexion.reiniciar()
        while (true) {
            delay(15_000)
            if (c.sesion.expirada()) {
                withContext(Dispatchers.IO) { c.db.auditar(u.nombre, "SESION", "Cierre automático por inactividad", "OK") }
                c.conexion.detener()
                c.sesion.cerrar()
                break
            }
        }
    }

    TemaInvernadero(modoTarde) {
        Box(
            Modifier
                .fillMaxSize()
                // cualquier toque cuenta como actividad (no consume el toque)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            c.sesion.tocar()
                        }
                    }
                }
        ) {
            // Surface fija el color de texto correcto para el tema (día u oscuro)
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val u = usuario
                if (u == null) {
                    PantallaAcceso(c)
                } else {
                    PantallaConMenu(c, u, modoTarde) { nuevo ->
                        modoTarde = nuevo
                        c.ajustes.modoTarde = nuevo
                    }
                }
            }
        }
    }
}

private data class Destino(val ruta: String, val titulo: String, val icono: ImageVector)

@Composable
private fun PantallaConMenu(c: Contenedor, u: Usuario, modoTarde: Boolean, alCambiarModo: (Boolean) -> Unit) {
    val nav = rememberNavController()
    val destinos = listOf(
        Destino("monitoreo", "Monitoreo", Icons.Filled.Home),
        Destino("activador", "Activador", Icons.Filled.Build),
        Destino("historial", "Historial", Icons.AutoMirrored.Filled.List),
        Destino("ajustes", "Ajustes", Icons.Filled.Settings),
    )
    val actual by nav.currentBackStackEntryAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinos.forEach { d ->
                    NavigationBarItem(
                        selected = actual?.destination?.route == d.ruta,
                        onClick = {
                            nav.navigate(d.ruta) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(d.icono, contentDescription = d.titulo) },
                        label = { Text(d.titulo) }
                    )
                }
            }
        }
    ) { relleno ->
        NavHost(nav, startDestination = "monitoreo", modifier = Modifier.padding(relleno)) {
            composable("monitoreo") { PantallaMonitoreo(c, modoTarde, alCambiarModo) }
            composable("activador") { PantallaActivador(c, u) }
            composable("historial") { PantallaHistorial(c) }
            composable("ajustes") { PantallaAjustes(c, u) }
        }
    }
}
