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
import androidx.compose.runtime.LaunchedEffect
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
import com.invernadero.datos.ModoConexion
import com.invernadero.datos.Rol
import com.invernadero.datos.Usuario
import com.invernadero.ui.OpcionRadio
import com.invernadero.ui.Seccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun PantallaAjustes(c: Contenedor, u: Usuario) {
    val admin = u.rol.puedeAdministrar
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val estado by c.conexion.estado.collectAsState()
    var cfg by remember { mutableStateOf(c.ajustes.cargar()) }
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
        val necesitaClave = cfg.modo != ModoConexion.SIMULADOR || cfg.compartir
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
                Text("${u.nombre} · ${u.rol.etiqueta}")
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { c.db.auditar(u.nombre, "SESION", "Cierre de sesión", "OK") }
                        c.conexion.detener()
                        c.sesion.cerrar()
                    }
                }) { Text("Cerrar sesión") }
            }

            Seccion("Conexión inalámbrica") {
                if (!admin) Text("Solo un administrador puede cambiar estos ajustes.", color = MaterialTheme.colorScheme.error)
                Text("Estado: ${estado.descripcion} — ${if (estado.conectado) "conectado" else estado.mensaje ?: "sin conexión"}",
                    style = MaterialTheme.typography.bodySmall)

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

                OutlinedTextField(
                    value = cfg.secreto, onValueChange = { cfg = cfg.copy(secreto = it.take(64)) },
                    label = { Text("Clave compartida") }, singleLine = true, enabled = admin,
                    visualTransformation = if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton(onClick = { verClave = !verClave }) { Text(if (verClave) "Ocultar" else "Ver") } },
                    supportingText = { Text("Igual en el ESP32 (SECRETO) y en todos los teléfonos. Mín. 12 caracteres.") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (cfg.modo != ModoConexion.REMOTO) {
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

            if (admin) GestionUsuarios(c, u) { aviso(it) }
        }
    }
}

@Composable
private fun GestionUsuarios(c: Contenedor, yo: Usuario, aviso: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var usuarios by remember { mutableStateOf<List<Usuario>>(emptyList()) }
    var recargar by remember { mutableStateOf(0) }
    var nombre by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var rol by remember { mutableStateOf(Rol.OPERADOR) }
    var guardando by remember { mutableStateOf(false) }

    LaunchedEffect(recargar) { usuarios = withContext(Dispatchers.IO) { c.usuarios.listar() } }

    Seccion("Usuarios (control de acceso)") {
        usuarios.forEach { usr ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(usr.nombre + if (usr.id == yo.id) " (tú)" else "")
                    Text(usr.rol.etiqueta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                if (usr.id != yo.id) {
                    TextButton(onClick = {
                        scope.launch {
                            val e = withContext(Dispatchers.IO) {
                                c.usuarios.eliminar(usr.id).also {
                                    c.db.auditar(yo.nombre, "USUARIO", "Eliminar ${usr.nombre}", if (it == null) "OK" else "FALLA")
                                }
                            }
                            aviso(e ?: "Usuario eliminado")
                            recargar++
                        }
                    }) { Text("Eliminar") }
                }
            }
        }
        HorizontalDivider()
        Text("Nuevo usuario", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(nombre, { nombre = it.take(30) }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            clave, { clave = it.take(64) }, label = { Text("Contraseña (mín. 8, letras y números)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Column(Modifier.selectableGroup()) {
            Rol.entries.forEach { r -> OpcionRadio(r.etiqueta, rol == r) { rol = r } }
        }
        Button(
            onClick = {
                guardando = true
                val n = nombre.trim()
                val arreglo = clave.toCharArray()
                val r = rol
                scope.launch {
                    val e = withContext(Dispatchers.Default) { c.usuarios.registrar(n, arreglo, r) }
                    arreglo.fill('\u0000')
                    withContext(Dispatchers.IO) { c.db.auditar(yo.nombre, "USUARIO", "Crear $n (${r.name})", if (e == null) "OK" else "FALLA") }
                    guardando = false
                    if (e == null) { nombre = ""; clave = ""; recargar++; aviso("Usuario creado") } else aviso(e)
                }
            },
            enabled = !guardando && nombre.isNotBlank() && clave.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Crear usuario") }
    }
}
