package com.invernadero.ui

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
import com.invernadero.nube.Reloj
import com.invernadero.ui.acceso.PantallaAcceso
import com.invernadero.ui.historial.PantallaRegistro
import com.invernadero.ui.controles.PantallaControles
import com.invernadero.ui.ajustes.PantallaAjustes
import com.invernadero.ui.historial.PantallaHistorial
import com.invernadero.ui.monitoreo.PantallaMonitoreo
import kotlinx.coroutines.delay

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
            // Un invitado sale apenas su pase vence o lo revocan (se revisa en la nube)
            val paseCerrado = u.invitado && !c.nube.paseVigente(u)
            if (c.sesion.expirada() || paseCerrado) {
                val motivo = when {
                    u.invitado && Reloj.ahora() > u.vence -> "Fin de la visita: el pase venció"
                    paseCerrado -> "Fin de la visita: el pase fue revocado"
                    else -> "Cierre automático por inactividad"
                }
                c.cerrarSesion(u, if (u.invitado) "INVITADO" else "SESION", motivo)
                break
            }
        }
    }

    // Íconos de la barra de estado oscuros de día y claros al atardecer (el fondo llega hasta arriba)
    val vista = LocalView.current
    if (!vista.isInEditMode) {
        SideEffect {
            val ventana = (vista.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(ventana, vista).apply {
                isAppearanceLightStatusBars = !modoTarde
                isAppearanceLightNavigationBars = !modoTarde
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
    // Después de entrar siempre se abre Monitoreo
    val destinos = listOf(
        Destino("monitoreo", "Monitoreo", Icons.Filled.Home),
        Destino("controles", "Controles", Icons.Filled.Build),
        Destino("historial", "Historial", Icons.AutoMirrored.Filled.List),
        Destino("ajustes", if (u.invitado) "Visita" else "Ajustes", Icons.Filled.Settings),
    )
    val actual by nav.currentBackStackEntryAsState()
    fun elegido(d: Destino) = actual?.destination?.route == d.ruta ||
        (d.ruta == "historial" && actual?.destination?.route == "registro")
    fun ir(d: Destino) = nav.navigate(d.ruta) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    val pantallas: @Composable (Modifier) -> Unit = { modificador ->
        NavHost(nav, startDestination = "monitoreo", modifier = modificador) {
            composable("monitoreo") { PantallaMonitoreo(c, modoTarde, alCambiarModo) }
            composable("controles") { PantallaControles(c, u) }
            composable("historial") { PantallaHistorial(c) { nav.navigate("registro") } }
            composable("registro") { PantallaRegistro(c) { nav.popBackStack() } }
            composable("ajustes") { PantallaAjustes(c, u) }
        }
    }

    // Material: en horizontal (o en pantallas anchas) el menú va en un riel lateral, no abajo,
    // para no robarle alto al contenido
    val config = LocalConfiguration.current
    val usarRiel = config.orientation == Configuration.ORIENTATION_LANDSCAPE || config.screenWidthDp >= 600

    if (usarRiel) {
        val ladoRiel = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
        Row(Modifier.fillMaxSize()) {
            NavigationRail(windowInsets = ladoRiel) {
                Spacer(Modifier.weight(1f))
                destinos.forEach { d ->
                    NavigationRailItem(
                        selected = elegido(d),
                        onClick = { ir(d) },
                        icon = { Icon(d.icono, contentDescription = d.titulo) },
                        label = { Text(d.titulo) },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            pantallas(
                Modifier.weight(1f).fillMaxHeight()
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
            )
        }
    } else {
        // Sin márgenes automáticos arriba: cada pantalla decide (Monitoreo pinta su cielo bajo la barra de estado y la cámara)
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar {
                    destinos.forEach { d ->
                        NavigationBarItem(
                            selected = elegido(d),
                            onClick = { ir(d) },
                            icon = { Icon(d.icono, contentDescription = d.titulo) },
                            label = { Text(d.titulo) }
                        )
                    }
                }
            }
        ) { relleno ->
            pantallas(Modifier.padding(relleno).consumeWindowInsets(relleno))
        }
    }
}
