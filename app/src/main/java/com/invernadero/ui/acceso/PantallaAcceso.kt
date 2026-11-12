package com.invernadero.ui.acceso

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.invernadero.Contenedor
import com.invernadero.datos.ResultadoLogin
import com.invernadero.datos.Rol
import com.invernadero.seguridad.Contrasenas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Si no hay usuarios todavía, pide crear el primer administrador; si no, muestra el login. */
@Composable
fun PantallaAcceso(c: Contenedor) {
    var hayUsuarios by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { hayUsuarios = withContext(Dispatchers.IO) { c.usuarios.hayUsuarios() } }
    when (hayUsuarios) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        false -> PantallaRegistroInicial(c) { hayUsuarios = true }
        true -> PantallaLogin(c)
    }
}

@Composable
private fun Marco(titulo: String, subtitulo: String, contenido: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Lock, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.secondary) }
            Text(titulo, style = MaterialTheme.typography.headlineMedium)
            Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            contenido()
        }
    }
}

@Composable
private fun CampoClave(valor: String, etiqueta: String, alCambiar: (String) -> Unit, accionFinal: (() -> Unit)? = null) {
    var ver by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (ver) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (accionFinal != null) ImeAction.Done else ImeAction.Next,
            autoCorrectEnabled = false
        ),
        keyboardActions = KeyboardActions(onDone = { accionFinal?.invoke() }),
        trailingIcon = { TextButton(onClick = { ver = !ver }) { Text(if (ver) "Ocultar" else "Ver") } }
    )
}

@Composable
fun PantallaLogin(c: Contenedor) {
    var usuario by rememberSaveable { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }          // la contraseña no se guarda en el estado persistente
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun ingresar() {
        if (cargando) return
        if (usuario.isBlank() || clave.isEmpty()) { error = "Completa usuario y contraseña"; return }
        cargando = true
        error = null
        val nombre = usuario.trim()
        val arreglo = clave.toCharArray()
        scope.launch {
            val r = withContext(Dispatchers.Default) { c.usuarios.login(nombre, arreglo) }
            arreglo.fill('\u0000')
            withContext(Dispatchers.IO) {
                c.db.auditar(nombre, "LOGIN", "Inicio de sesión", when (r) {
                    is ResultadoLogin.Correcto -> "OK"
                    is ResultadoLogin.Bloqueado -> "BLOQUEADO"
                    ResultadoLogin.Incorrecto -> "FALLIDO"
                })
            }
            cargando = false
            when (r) {
                is ResultadoLogin.Correcto -> { clave = ""; c.sesion.iniciar(r.usuario) }
                is ResultadoLogin.Bloqueado -> error = "Cuenta bloqueada por intentos fallidos. Intenta en ${(r.segundos + 59) / 60} min."
                ResultadoLogin.Incorrecto -> error = "Usuario o contraseña incorrectos"
            }
        }
    }

    Marco("Invernadero IoT", "Monitoreo y control del invernadero") {
        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it.take(30) },
            label = { Text("Usuario") },
            leadingIcon = { Icon(Icons.Filled.Person, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false)
        )
        CampoClave(clave, "Contraseña", { clave = it.take(64) }) { ingresar() }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = { ingresar() }, enabled = !cargando, modifier = Modifier.fillMaxWidth()) {
            if (cargando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Ingresar")
        }
        Text(
            "Contraseñas protegidas con PBKDF2-SHA256 · bloqueo de 5 min tras ${com.invernadero.datos.UsuariosRepo.MAX_INTENTOS} intentos · cierre de sesión por inactividad",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Primer uso: no hay contraseñas por defecto, el dueño crea su propio administrador. */
@Composable
private fun PantallaRegistroInicial(c: Contenedor, alTerminar: () -> Unit) {
    var usuario by rememberSaveable { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun crear() {
        val nombre = usuario.trim()
        error = Contrasenas.validarPolitica(nombre, clave) ?: if (clave != confirmar) "Las contraseñas no coinciden" else null
        if (error != null) return
        cargando = true
        val arreglo = clave.toCharArray()
        scope.launch {
            val e = withContext(Dispatchers.Default) { c.usuarios.registrar(nombre, arreglo, Rol.ADMIN) }
            arreglo.fill('\u0000')
            cargando = false
            if (e != null) { error = e; return@launch }
            withContext(Dispatchers.IO) { c.db.auditar(nombre, "USUARIO", "Creación del administrador inicial", "OK") }
            alTerminar()
        }
    }

    Marco("Configuración inicial", "Crea la cuenta de administrador. No existen contraseñas por defecto.") {
        OutlinedTextField(
            value = usuario, onValueChange = { usuario = it.take(30) }, label = { Text("Usuario administrador") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false)
        )
        CampoClave(clave, "Contraseña (mín. 8, letras y números)", { clave = it.take(64) })
        CampoClave(confirmar, "Repetir contraseña", { confirmar = it.take(64) }) { crear() }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { crear() }, enabled = !cargando, modifier = Modifier.fillMaxWidth()) {
            if (cargando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Crear administrador")
        }
    }
}
