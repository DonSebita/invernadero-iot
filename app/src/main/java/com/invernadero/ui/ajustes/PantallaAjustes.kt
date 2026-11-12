package com.invernadero.ui.ajustes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.invernadero.Contenedor
import com.invernadero.conexion.buscarCentral
import com.invernadero.conexion.esIpValida
import com.invernadero.conexion.ipLocal
import com.invernadero.conexion.textoCorto
import com.invernadero.datos.ModoConexion
import com.invernadero.datos.Usuario
import com.invernadero.ui.OpcionRadio
import com.invernadero.ui.Seccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PantallaAjustes(c: Contenedor, u: Usuario) {
    val admin = !u.invitado          // cualquier cuenta configura la conexión; los invitados no
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val estado by c.conexion.estado.collectAsState()
    // En un teléfono donde nunca se configuró la conexión, la app ya está mirando desde la nube
    var cfg by remember {
        val guardada = c.ajustes.cargar()
        mutableStateOf(if (!c.ajustes.configurado && u.invernadero.isNotEmpty()) guardada.copy(modo = ModoConexion.NUBE) else guardada)
    }
    var verClave by remember { mutableStateOf(false) }
    var buscando by remember { mutableStateOf(false) }

    fun aviso(t: String) { scope.launch { snack.showSnackbar(t) } }

    fun aplicar() {
        c.ajustes.guardar(cfg)
        c.conexion.reiniciar(cfg)
        scope.launch(Dispatchers.IO) {
            // Nunca se registra la clave compartida, solo qué se cambió
            c.db.auditar(u.nombre, "AJUSTES", "modo=${cfg.modo.name}, central=${cfg.compartir}", "OK")
        }
        aviso("Ajustes guardados. Conectando…")
    }

    val pedirPermisoBt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) aplicar() else aviso("Sin el permiso de Bluetooth no se puede conectar al ESP32")
    }

    fun validarYGuardar() {
        val necesitaClave = (cfg.modo != ModoConexion.SIMULADOR && cfg.modo != ModoConexion.NUBE) || cfg.compartir
        val error = when {
            necesitaClave && cfg.secreto.length < 12 -> "La clave compartida debe tener al menos 12 caracteres"
            cfg.modo == ModoConexion.WIFI && !esIpValida(cfg.ipEsp32) -> "IP del ESP32 no válida"
            cfg.modo == ModoConexion.REMOTO && !esIpValida(cfg.ipCentral) -> "IP del teléfono central no válida (usa Buscar)"
            cfg.modo == ModoConexion.BLUETOOTH && cfg.nombreBt.isBlank() -> "Escribe el nombre Bluetooth del ESP32"
            else -> null
        }
        if (error != null) { aviso(error); return }
        val faltaPermisoBt = cfg.modo == ModoConexion.BLUETOOTH && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (faltaPermisoBt) pedirPermisoBt.launch(Manifest.permission.BLUETOOTH_CONNECT) else aplicar()
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background) { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall)

            Seccion("Sesión") {
                if (u.invitado) {
                    Text("${u.nombre} · de visita por invitación de ${u.anfitrion}")
                    Text(
                        "Tu pase vence el ${horaYDia(u.vence)} (${tiempoRestante(u.vence)}). " +
                            if (u.rol.puedeActivar) "Puedes regar y ventilar." else "Puedes mirar, sin activar nada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(u.nombre)
                    Text(
                        listOfNotNull(u.correo, if (c.sesion.recordada) "recordada en este teléfono" else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Con esta cuenta entras en cualquier teléfono y ves el mismo invernadero.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        // Cerrar sesión también olvida el "Recordarme" y, si era una visita, borra su identidad temporal
                        c.cerrarSesion(u, if (u.invitado) "INVITADO" else "SESION", if (u.invitado) "Fin de la visita" else "Cierre de sesión")
                    }
                }) { Text(if (u.invitado) "Terminar visita" else "Cerrar sesión") }
            }

            if (!u.invitado) Seccion("Conexión inalámbrica") {
                Text("Estado: ${estado.textoCorto()}", style = MaterialTheme.typography.bodySmall)

                Column(Modifier.selectableGroup()) {
                    ModoConexion.entries.forEach { m ->
                        OpcionRadio(m.etiqueta, cfg.modo == m, admin, m.ayuda) { cfg = cfg.copy(modo = m) }
                    }
                }
                HorizontalDivider()

                when (cfg.modo) {
                    ModoConexion.SIMULADOR -> {
                        Text("Sensores simulados: ${cfg.sensoresSimulados} (una planta por sensor)")
                        Slider(
                            value = cfg.sensoresSimulados.toFloat(),
                            onValueChange = { cfg = cfg.copy(sensoresSimulados = it.roundToInt()) },
                            valueRange = 1f..8f, steps = 6, enabled = admin
                        )
                    }
                    ModoConexion.WIFI -> OutlinedTextField(
                        value = cfg.ipEsp32, onValueChange = { cfg = cfg.copy(ipEsp32 = it.take(15)) },
                        label = { Text("IP del ESP32") }, singleLine = true, enabled = admin,
                        supportingText = { Text("La misma IP fija del sketch y de network_security_config.xml") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    ModoConexion.BLUETOOTH -> OutlinedTextField(
                        value = cfg.nombreBt, onValueChange = { cfg = cfg.copy(nombreBt = it.take(30)) },
                        label = { Text("Nombre Bluetooth del ESP32") }, singleLine = true, enabled = admin,
                        supportingText = { Text("Primero emparéjalo en Ajustes del teléfono > Bluetooth") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ModoConexion.NUBE -> Text(
                        "No necesita IP ni clave: verás lo último que subió el ESP32 (cada 2 min) desde cualquier lugar con " +
                            "Internet. Desde la nube solo se mira; para mover el ventilador o la ventana usa WiFi o Bluetooth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ModoConexion.REMOTO -> {
                        OutlinedTextField(
                            value = cfg.ipCentral, onValueChange = { cfg = cfg.copy(ipCentral = it.take(15)) },
                            label = { Text("IP del teléfono central") }, singleLine = true, enabled = admin,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                buscando = true
                                scope.launch {
                                    val ip = buscarCentral(contexto)
                                    buscando = false
                                    if (ip != null) { cfg = cfg.copy(ipCentral = ip); aviso("Central encontrado: $ip") }
                                    else aviso("No se encontró un teléfono central en esta red WiFi")
                                }
                            },
                            enabled = admin && !buscando
                        ) { Text(if (buscando) "Buscando…" else "Buscar en la red") }
                    }
                }

                if (cfg.modo != ModoConexion.NUBE) OutlinedTextField(
                    value = cfg.secreto, onValueChange = { cfg = cfg.copy(secreto = it.take(64)) },
                    label = { Text("Clave compartida") }, singleLine = true, enabled = admin,
                    visualTransformation = if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton(onClick = { verClave = !verClave }) { Text(if (verClave) "Ocultar" else "Ver") } },
                    supportingText = { Text("Igual en el ESP32 (SECRETO) y en todos los teléfonos. Mín. 12 caracteres.") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (cfg.modo != ModoConexion.REMOTO && cfg.modo != ModoConexion.NUBE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Compartir con otros teléfonos")
                            Text("Este teléfono será el CENTRAL. Tu IP: ${ipLocal() ?: "sin WiFi"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = cfg.compartir, onCheckedChange = { cfg = cfg.copy(compartir = it) }, enabled = admin)
                    }
                }

                Button(onClick = { validarYGuardar() }, enabled = admin, modifier = Modifier.fillMaxWidth()) {
                    Text("Guardar y conectar")
                }
            }

            if (!u.invitado) {
                SeccionNube(c, u) { aviso(it) }
                SeccionInvitar(c, u) { aviso(it) }
            }
        }
    }
}
