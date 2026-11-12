package com.invernadero.ui.controles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.invernadero.Contenedor
import com.invernadero.conexion.Comandos
import com.invernadero.conexion.ParametrosControl
import com.invernadero.datos.Usuario
import com.invernadero.ui.Dato
import com.invernadero.ui.Encabezado
import com.invernadero.ui.OpcionRadio
import com.invernadero.ui.PildoraEstado
import com.invernadero.ui.Seccion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Pantalla CONTROLES: órdenes directas al ESP32 (directo o a través del teléfono central)
 * y las reglas del modo automático que decide el cliente.
 * Elementos interactivos: RadioButtons, Switch, Sliders, botones y campos de texto (EditText).
 */
@Composable
fun PantallaControles(c: Contenedor, u: Usuario) {
    val lectura by c.conexion.lectura.collectAsState()
    val estado by c.conexion.estado.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var enviando by remember { mutableStateOf(false) }

    /** Envía las órdenes en orden; se detiene en la primera que falla. Devuelve true si salieron todas. */
    fun enviar(vararg codigos: String, alTerminar: (Boolean) -> Unit = {}) {
        if (enviando || codigos.isEmpty()) return
        scope.launch {
            enviando = true
            var todosOk = true
            for (cod in codigos) if (!c.conexion.enviar(cod)) { todosOk = false; break }
            enviando = false
            alTerminar(todosOk)
            val desc = codigos.joinToString(" + ") { Comandos.descripcion(it) }
            snack.showSnackbar(if (todosOk) "Enviado: $desc" else "No se pudo enviar: $desc")
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background) { relleno ->
        if (!u.rol.puedeActivar) {
            Box(Modifier.fillMaxSize().padding(relleno).padding(24.dp), contentAlignment = Alignment.Center) {
                Seccion("Acceso restringido") {
                    Text(if (u.invitado) "Tu pase de visita es solo para mirar. Pide a quien te invitó un pase para regar y ventilar." else "Esta cuenta solo permite mirar.")
                }
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Encabezado("Controles", "Maneja el invernadero ahora o define cómo debe cuidarse solo. Todo queda registrado a tu nombre.") {
                PildoraEstado(
                    "${estado.descripcion} · " + if (estado.conectado) "conectado" else (estado.mensaje ?: "sin conexión"),
                    ok = estado.conectado
                )
            }
            if (enviando) LinearProgressIndicator(Modifier.fillMaxWidth())

            // ── Modo (RadioButtons) ──
            val automatico = lectura?.let { it.modoVentilador == "AUTO" && it.modoVentana == "AUTO" } ?: true
            Seccion("Modo de control") {
                Column(Modifier.selectableGroup()) {
                    OpcionRadio("Automático", automatico, !enviando, "El ESP32 decide con tus reglas (más abajo)") {
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
            Seccion("Ventilador", subtitulo = "Modo ${(lectura?.modoVentilador ?: "—").lowercase()}") {
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
                    OutlinedButton(onClick = { enviar(Comandos.BAJAR) }, enabled = !enviando) { Text("Bajar") }
                    OutlinedButton(onClick = { enviar(Comandos.SUBIR) }, enabled = !enviando) { Text("Subir") }
                    FilledTonalButton(onClick = { enviar(Comandos.MAXIMO) }, enabled = !enviando) { Text("Máximo") }
                }
            }

            // ── Ventana (Slider + botones) ──
            var aperturaArrastrada by remember { mutableStateOf<Float?>(null) }
            val apertura = aperturaArrastrada ?: (lectura?.anguloVentana ?: 0f)
            Seccion("Ventana", subtitulo = "Modo ${(lectura?.modoVentana ?: "—").lowercase()}") {
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

            // ── Reglas del modo automático (las decide el cliente) ──
            SeccionReglas(
                actual = lectura?.parametros,
                hayDatos = lectura != null,
                invitado = u.invitado,
                temperatura = lectura?.temperatura,
                enviando = enviando,
                alGuardar = { codigos, alTerminar -> enviar(*codigos.toTypedArray(), alTerminar = alTerminar) }
            )
        }
    }
}

/** Valores escritos por el usuario (texto, para poder validar mientras escribe). */
private data class Borrador(
    val sobre: String,
    val desde: String,
    val hasta: String,
    val humedad: String,
    val porHumedad: Boolean
) {
    companion object {
        fun de(p: ParametrosControl) = Borrador(
            p.ventiladorSobre.toString(), p.ventanaDesde.toString(), p.ventanaHasta.toString(),
            (if (p.ventilaPorHumedad) p.humedadMaxima else 85).toString(), p.ventilaPorHumedad
        )
    }
}

private class Validacion(b: Borrador) {
    val sobre = b.sobre.toIntOrNull()
    val desde = b.desde.toIntOrNull()
    val hasta = b.hasta.toIntOrNull()
    val humedad = b.humedad.toIntOrNull()

    val errorSobre = rango(sobre, ParametrosControl.RANGO_VENTILADOR, "°C")
    val errorDesde = rango(desde, ParametrosControl.RANGO_DESDE, "°C")
    val errorHasta = rango(hasta, ParametrosControl.RANGO_HASTA, "°C")
        ?: if (desde != null && hasta != null && hasta < desde + ParametrosControl.SEPARACION_MINIMA)
            "Debe ser al menos ${ParametrosControl.SEPARACION_MINIMA} °C más que el inicio ($desde °C)" else null
    val errorHumedad = if (b.porHumedad) rango(humedad, ParametrosControl.RANGO_HUMEDAD, "%") else null

    val resultado: ParametrosControl? =
        if (errorSobre == null && errorDesde == null && errorHasta == null && errorHumedad == null)
            ParametrosControl(sobre!!, desde!!, hasta!!, if (b.porHumedad) humedad!! else 100)
        else null

    private fun rango(v: Int?, r: IntRange, u: String) =
        if (v == null || v !in r) "Entre ${r.first} y ${r.last} $u" else null
}

@Composable
private fun SeccionReglas(
    actual: ParametrosControl?,
    hayDatos: Boolean,
    invitado: Boolean,
    temperatura: Float?,
    enviando: Boolean,
    alGuardar: (List<String>, (Boolean) -> Unit) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Seccion(
        "Reglas del modo automático",
        subtitulo = "Ajusta cuándo ventilar según lo que necesita tu cultivo. Se guardan en el ESP32 y siguen funcionando aunque cierres la app."
    ) {
        when {
            !hayDatos -> {
                Text("Conéctate al invernadero para ver y cambiar sus reglas.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                return@Seccion
            }
            actual == null -> {
                Text(
                    "Tu ESP32 todavía no admite reglas propias: carga el sketch actualizado (carpeta esp32) y vuelve a conectar.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.error
                )
                return@Seccion
            }
            invitado -> {
                ReglaTermica(actual!!, temperatura)
                ResumenReglas(actual)
                Text("Los pases de visita pueden ver las reglas, pero no cambiarlas.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                return@Seccion
            }
        }
        val base = actual!!
        // El borrador se reinicia si el ESP32 informa otras reglas (por ejemplo, las cambió otro teléfono)
        var borrador by remember(base) { mutableStateOf(Borrador.de(base)) }
        var confirmar by remember { mutableStateOf(false) }
        val v = Validacion(borrador)
        val nuevo = v.resultado
        val cambios = nuevo?.let { Comandos.reglas(base, it) }.orEmpty()

        ReglaTermica(nuevo ?: base, temperatura)

        CampoNumero(
            "Encender el ventilador sobre", "°C", borrador.sobre, v.errorSobre,
            "A toda potencia ${v.sobre?.let { "en ${it + 10} °C" } ?: "10 °C más arriba"}.",
            ParametrosControl.RANGO_VENTILADOR
        ) { borrador = borrador.copy(sobre = it) }
        CampoNumero(
            "La ventana empieza a abrir en", "°C", borrador.desde, v.errorDesde,
            "Más frío que esto, queda cerrada.", ParametrosControl.RANGO_DESDE
        ) { borrador = borrador.copy(desde = it) }
        CampoNumero(
            "La ventana queda abierta del todo en", "°C", borrador.hasta, v.errorHasta,
            "Entre ambos valores abre en proporción.", ParametrosControl.RANGO_HASTA
        ) { borrador = borrador.copy(hasta = it) }

        HorizontalDivider(color = cs.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ventilar por humedad del aire", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Si el aire de la caja se pone muy húmedo, ventila suave y abre a medias aunque no haga calor.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = borrador.porHumedad, onCheckedChange = { borrador = borrador.copy(porHumedad = it) })
        }
        if (borrador.porHumedad) {
            CampoNumero(
                "Humedad del aire máxima", "%", borrador.humedad, v.errorHumedad,
                "Sobre este valor se ventila.", ParametrosControl.RANGO_HUMEDAD
            ) { borrador = borrador.copy(humedad = it) }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { borrador = Borrador.de(ParametrosControl.FABRICA) },
                enabled = !enviando && borrador != Borrador.de(ParametrosControl.FABRICA)
            ) { Text("Valores de fábrica") }
            Spacer(Modifier.weight(1f))
            if (borrador != Borrador.de(base)) {
                TextButton(onClick = { borrador = Borrador.de(base) }, enabled = !enviando) { Text("Descartar") }
            }
            Button(onClick = { confirmar = true }, enabled = !enviando && cambios.isNotEmpty()) { Text("Guardar reglas") }
        }

        if (confirmar && nuevo != null) {
            AlertDialog(
                onDismissRequest = { confirmar = false },
                title = { Text("Guardar reglas") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("El ESP32 empezará a usar estas reglas en el modo automático:")
                        ResumenReglas(nuevo)
                        Text("Quedará registrado a tu nombre.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmar = false
                        alGuardar(cambios) { /* si todo salió bien el ESP32 informa las reglas nuevas y el borrador se reinicia solo */ }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
            )
        }
    }
}

@Composable
private fun ResumenReglas(p: ParametrosControl) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Dato("Ventilador", "sobre ${p.ventiladorSobre} °C")
        Dato("Ventana", "abre de ${p.ventanaDesde} a ${p.ventanaHasta} °C")
        Dato("Por humedad", if (p.ventilaPorHumedad) "sobre ${p.humedadMaxima} %" else "desactivado")
    }
}

/** Campo numérico con flechas para subir/bajar de a 1 y teclado numérico para escribir. */
@Composable
private fun CampoNumero(
    etiqueta: String,
    unidad: String,
    valor: String,
    error: String?,
    ayuda: String,
    rango: IntRange,
    alCambiar: (String) -> Unit
) {
    val n = valor.toIntOrNull()
    OutlinedTextField(
        value = valor,
        onValueChange = { t -> alCambiar(t.filter { it.isDigit() }.take(3)) },
        label = { Text(etiqueta) },
        suffix = { Text(unidad) },
        singleLine = true,
        isError = error != null,
        supportingText = { Text(error ?: ayuda) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        trailingIcon = {
            Row {
                IconButton(onClick = { alCambiar(((n ?: rango.first) - 1).coerceIn(rango).toString()) }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Bajar $etiqueta")
                }
                IconButton(onClick = { alCambiar(((n ?: rango.first) + 1).coerceIn(rango).toString()) }) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Subir $etiqueta")
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Regla térmica de un vistazo: una barra de 5 a 45 °C con las zonas (ventana cerrada / abriendo /
 * abierta), la marca donde se enciende el ventilador y la temperatura de ahora.
 */
@Composable
private fun ReglaTermica(p: ParametrosControl, actual: Float?) {
    val cs = MaterialTheme.colorScheme
    val medidor = rememberTextMeasurer()
    val estilo = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    val estiloFuerte = MaterialTheme.typography.labelMedium.copy(color = cs.onSurface)
    val frio = cs.primary
    val tibio = cs.secondary
    val calor = cs.tertiary
    val pista = cs.surfaceContainerHighest
    val marca = cs.onSurface
    val min = 5f
    val max = 45f
    val descripcion = "Ventana cerrada bajo ${p.ventanaDesde} °C, abierta del todo sobre ${p.ventanaHasta} °C; " +
        "ventilador sobre ${p.ventiladorSobre} °C" + (actual?.let { "; ahora %.1f °C".format(it) } ?: "")

    Canvas(Modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = descripcion }) {
        val alto = 10.dp.toPx()
        val y = 26.dp.toPx()
        fun x(c: Float) = (c.coerceIn(min, max) - min) / (max - min) * size.width
        val r = CornerRadius(alto / 2f)
        drawRoundRect(pista, Offset(0f, y), Size(size.width, alto), r)
        // Zonas: cerrada (frío) · abriendo (tibio) · abierta (calor)
        drawRoundRect(frio.copy(alpha = 0.55f), Offset(0f, y), Size(x(p.ventanaDesde.toFloat()), alto), r)
        drawRect(tibio.copy(alpha = 0.7f), Offset(x(p.ventanaDesde.toFloat()), y), Size(x(p.ventanaHasta.toFloat()) - x(p.ventanaDesde.toFloat()), alto))
        drawRoundRect(calor.copy(alpha = 0.7f), Offset(x(p.ventanaHasta.toFloat()), y), Size(size.width - x(p.ventanaHasta.toFloat()), alto), r)

        // Marca del ventilador (arriba)
        val xv = x(p.ventiladorSobre.toFloat())
        drawLine(marca, Offset(xv, y - 6.dp.toPx()), Offset(xv, y + alto), 2.dp.toPx(), StrokeCap.Round)
        val tv = medidor.measure("Ventilador ${p.ventiladorSobre}°", estiloFuerte)
        drawText(tv, topLeft = Offset((xv - tv.size.width / 2f).coerceIn(0f, size.width - tv.size.width), y - 8.dp.toPx() - tv.size.height))

        // Temperatura actual
        actual?.let {
            val xa = x(it)
            drawCircle(Color.White, 7.dp.toPx(), Offset(xa, y + alto / 2f))
            drawCircle(marca, 5.dp.toPx(), Offset(xa, y + alto / 2f))
        }

        // Valores de la ventana (abajo)
        val td = medidor.measure("${p.ventanaDesde}°", estilo)
        val th = medidor.measure("${p.ventanaHasta}°", estilo)
        val yt = y + alto + 4.dp.toPx()
        drawText(td, topLeft = Offset((x(p.ventanaDesde.toFloat()) - td.size.width / 2f).coerceIn(0f, size.width - td.size.width), yt))
        drawText(th, topLeft = Offset((x(p.ventanaHasta.toFloat()) - th.size.width / 2f).coerceIn(0f, size.width - th.size.width), yt))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Leyenda(frio.copy(alpha = 0.55f), "cerrada")
        Leyenda(tibio.copy(alpha = 0.7f), "abriendo")
        Leyenda(calor.copy(alpha = 0.7f), "abierta")
    }
}

@Composable
private fun Leyenda(color: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(12.dp).height(12.dp)) { drawRoundRect(color, cornerRadius = CornerRadius(3.dp.toPx())) }
        Spacer(Modifier.width(6.dp))
        Text("Ventana $texto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
