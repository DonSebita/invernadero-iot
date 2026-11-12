package com.invernadero.ui.activador

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.invernadero.Contenedor
import com.invernadero.conexion.Comandos
import com.invernadero.datos.Usuario
import com.invernadero.ui.Dato
import com.invernadero.ui.OpcionRadio
import com.invernadero.ui.Seccion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Pantalla ACTIVADOR: envía órdenes al ESP32 (directo o a través del teléfono central).
 * Elementos interactivos: RadioButtons, Switch, Sliders, botones y un campo de texto (EditText).
 */
@Composable
fun PantallaActivador(c: Contenedor, u: Usuario) {
    val lectura by c.conexion.lectura.collectAsState()
    val estado by c.conexion.estado.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var enviando by remember { mutableStateOf(false) }

    fun enviar(vararg codigos: String) {
        if (enviando) return
        scope.launch {
            enviando = true
            var todosOk = true
            for (cod in codigos) if (!c.conexion.enviar(cod)) todosOk = false
            enviando = false
            val desc = codigos.joinToString(" + ") { Comandos.descripcion(it) }
            snack.showSnackbar(if (todosOk) "✔ $desc" else "✖ No se pudo: $desc")
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background) { relleno ->
        if (!u.rol.puedeActivar) {
            Box(Modifier.fillMaxSize().padding(relleno).padding(24.dp), contentAlignment = Alignment.Center) {
                Seccion("Acceso restringido") {
                    Text("Tu rol (${u.rol.etiqueta}) solo permite monitorear. Pide a un administrador permisos de Operador.")
                }
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Activador", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${estado.descripcion} · " + if (estado.conectado) "conectado" else (estado.mensaje ?: "sin conexión"),
                style = MaterialTheme.typography.bodyMedium,
                color = if (estado.conectado) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
            if (enviando) LinearProgressIndicator(Modifier.fillMaxWidth())

            // ── Modo (RadioButtons) ──
            val automatico = lectura?.let { it.modoVentilador == "AUTO" && it.modoVentana == "AUTO" } ?: true
            Seccion("Modo de control") {
                Column(Modifier.selectableGroup()) {
                    OpcionRadio("Automático", automatico, !enviando, "El ESP32 decide por la temperatura (tu lógica del sketch)") {
                        if (!automatico) enviar(Comandos.AUTOMATICO)
                    }
                    OpcionRadio("Manual", !automatico, !enviando, "Fija el estado actual; luego usa los controles") {
                        val l = lectura
                        if (automatico && l != null) {
                            enviar(
                                Comandos.velocidad((l.velocidadVentilador * 255).roundToInt()),
                                Comandos.ventana(l.anguloVentana.roundToInt())
                            )
                        }
                    }
                }
            }

            // ── Ventilador (Switch + Slider + botones) ──
            var pwmArrastrado by remember { mutableStateOf<Float?>(null) }
            val pwmReal = (lectura?.velocidadVentilador ?: 0f) * 255f
            val pwm = pwmArrastrado ?: pwmReal
            Seccion("Ventilador") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (lectura?.ventiladorEncendido == true) "Encendido" else "Apagado", Modifier.weight(1f))
                    Switch(
                        checked = lectura?.ventiladorEncendido == true,
                        onCheckedChange = { enviar(if (it) Comandos.MAXIMO else Comandos.APAGAR) },
                        enabled = !enviando && lectura != null
                    )
                }
                Dato("Velocidad (PWM)", "${pwm.roundToInt()} / 255  ·  ${(pwm / 2.55f).roundToInt()}%")
                Slider(
                    value = pwm,
                    onValueChange = { pwmArrastrado = it },
                    onValueChangeFinished = {
                        pwmArrastrado?.let { enviar(Comandos.velocidad(it.roundToInt())) }
                        pwmArrastrado = null
                    },
                    valueRange = 0f..255f,
                    enabled = !enviando && lectura != null
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { enviar(Comandos.BAJAR) }, enabled = !enviando) { Text("− 25") }
                    OutlinedButton(onClick = { enviar(Comandos.SUBIR) }, enabled = !enviando) { Text("+ 25") }
                    FilledTonalButton(onClick = { enviar(Comandos.MAXIMO) }, enabled = !enviando) { Text("Máximo") }
                }
            }

            // ── Ventana (Slider + botones) ──
            var aperturaArrastrada by remember { mutableStateOf<Float?>(null) }
            val apertura = aperturaArrastrada ?: (lectura?.anguloVentana ?: 0f)
            Seccion("Ventana") {
                Dato("Apertura", "${apertura.roundToInt()}° de 90°")
                Slider(
                    value = apertura,
                    onValueChange = { aperturaArrastrada = it },
                    onValueChangeFinished = {
                        aperturaArrastrada?.let { enviar(Comandos.ventana(it.roundToInt())) }
                        aperturaArrastrada = null
                    },
                    valueRange = 0f..90f,
                    steps = 8,
                    enabled = !enviando && lectura != null
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { enviar(Comandos.CERRAR) }, enabled = !enviando) { Text("Cerrar") }
                    Button(onClick = { enviar(Comandos.ABRIR) }, enabled = !enviando) { Text("Abrir") }
                }
            }

            // ── Comando manual (EditText) ──
            var texto by remember { mutableStateOf("") }
            var confirmar by remember { mutableStateOf(false) }
            val cmd = texto.trim().uppercase()
            val valido = Comandos.esValido(cmd)
            Seccion("Comando manual") {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.take(4) },
                    label = { Text("Comando") },
                    singleLine = true,
                    isError = texto.isNotBlank() && !valido,
                    supportingText = {
                        Text(if (texto.isBlank() || valido) "A, C, M, 0, +, -, X, V0–V255, W0–W90" else "Comando no permitido")
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Send),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { confirmar = true }, enabled = valido && !enviando, modifier = Modifier.fillMaxWidth()) {
                    Text("Enviar comando")
                }
            }
            if (confirmar) {
                AlertDialog(
                    onDismissRequest = { confirmar = false },
                    title = { Text("Confirmar orden") },
                    text = { Text("¿Enviar \"$cmd\" (${Comandos.descripcion(cmd)}) al invernadero?\nQuedará registrado a tu nombre.") },
                    confirmButton = { TextButton(onClick = { confirmar = false; enviar(cmd); texto = "" }) { Text("Enviar") } },
                    dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
                )
            }
        }
    }
}
