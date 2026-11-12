@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.invernadero.ui.ajustes

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invernadero.Contenedor
import com.invernadero.conexion.LecturaEsp32
import com.invernadero.conexion.haceCuanto
import com.invernadero.datos.Usuario
import com.invernadero.nube.ConfigEsp32
import com.invernadero.nube.PaseNube
import com.invernadero.nube.Reloj
import com.invernadero.ui.OpcionRadio
import com.invernadero.ui.Seccion
import com.invernadero.ui.copiarSensible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Duracion(val etiqueta: String, val ms: Long)

private val Duraciones = listOf(
    Duracion("1 hora", 3_600_000L),
    Duracion("8 horas", 8 * 3_600_000L),
    Duracion("1 día", 24 * 3_600_000L),
    Duracion("3 días", 72 * 3_600_000L),
)

fun horaYDia(ts: Long): String = SimpleDateFormat("EEE d 'a las' HH:mm", Locale.forLanguageTag("es")).format(Date(ts))

/** "vence en 3 h" / "vence en 25 min" */
fun tiempoRestante(vence: Long): String {
    val min = ((vence - Reloj.ahora()) / 60_000L).coerceAtLeast(0)
    return when {
        min >= 48 * 60 -> "quedan ${min / (24 * 60)} días"
        min >= 60 -> "quedan ${min / 60} h"
        else -> "quedan $min min"
    }
}

/**
 * Pases de visita en la nube: el usuario genera una clave temporal para alguien de confianza,
 * que entra desde SU PROPIO teléfono (pestaña Invitado) con Internet.
 */
@Composable
fun SeccionInvitar(c: Contenedor, u: Usuario, aviso: (String) -> Unit) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var duracion by rememberSaveable { mutableIntStateOf(1) }
    var puedeActivar by rememberSaveable { mutableStateOf(u.rol.puedeActivar) }
    var generando by remember { mutableStateOf(false) }
    var recien by remember { mutableStateOf<Pair<String, Long>?>(null) }   // clave, vence
    var pases by remember { mutableStateOf<List<PaseNube>>(emptyList()) }
    var errorLista by remember { mutableStateOf<String?>(null) }
    var recargar by remember { mutableIntStateOf(0) }

    LaunchedEffect(recargar) {
        val r = runCatching { c.nube.pasesAbiertos(u) }
        pases = r.getOrDefault(pases)
        errorLista = r.exceptionOrNull()?.message
    }

    fun generar() {
        generando = true
        val d = Duraciones[duracion]
        val activar = puedeActivar && u.rol.puedeActivar          // nunca más permisos que quien invita
        scope.launch {
            val r = runCatching { c.nube.crearPase(u, d.ms, activar) }
            withContext(Dispatchers.IO) {
                c.db.auditar(u.nombre, "INVITADO", "Pase de visita por ${d.etiqueta} (${if (activar) "puede activar" else "solo mirar"})",
                    if (r.isSuccess) "OK" else "FALLA")
            }
            generando = false
            r.onSuccess { recien = it; recargar++ }
                .onFailure { aviso(it.message ?: "No se pudo crear el pase") }
        }
    }

    fun compartir(clave: String, vence: Long) {
        val texto = "Te dejo pasar a mi invernadero GreenBox.\n" +
            "Abre la app en tu teléfono, toca «Invitado» y escribe esta clave: $clave\n" +
            "Vale hasta el ${horaYDia(vence)}."
        val envio = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, texto) }
        contexto.startActivity(Intent.createChooser(envio, "Compartir pase de visita"))
    }

    Seccion("Abre la puerta a alguien") {
        Text(
            "¿No vas a estar en casa? Genera una clave temporal para que un amigo o vecino cuide tus plantas. " +
                "Entra desde su propio teléfono con Internet: verá lo que sube el ESP32 a la nube, y si está en la red " +
                "del invernadero con la conexión configurada, también podrá usar los Controles.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("¿Por cuánto tiempo?", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Duraciones.forEachIndexed { i, d ->
                FilterChip(selected = duracion == i, onClick = { duracion = i }, label = { Text(d.etiqueta) })
            }
        }
        Text("¿Qué podrá hacer?", style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            OpcionRadio("Regar y ventilar", puedeActivar, u.rol.puedeActivar, "Usa los Controles igual que tú") { puedeActivar = true }
            OpcionRadio("Solo mirar", !puedeActivar, ayuda = "Ve el invernadero, sin tocar nada") { puedeActivar = false }
        }
        Button(onClick = { generar() }, enabled = !generando, modifier = Modifier.fillMaxWidth()) {
            Text(if (generando) "Generando…" else "Generar clave temporal")
        }

        recien?.let { (clave, vence) ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionContainer {
                        Text(clave, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp))
                    }
                    Text("Vale hasta el ${horaYDia(vence)}. Solo la verás ahora: compártela de inmediato.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { compartir(clave, vence) }) { Text("Compartir") }
                        OutlinedButton(onClick = { copiarSensible(contexto, "Pase GreenBox", clave); aviso("Clave copiada") }) { Text("Copiar") }
                    }
                }
            }
        }

        errorLista?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (pases.isNotEmpty()) {
            HorizontalDivider()
            Text("Tus pases abiertos", style = MaterialTheme.typography.titleSmall)
            pases.forEach { p ->
                FilaPase(p) {
                    scope.launch {
                        val r = runCatching { c.nube.revocarPase(u, p.hash) }
                        withContext(Dispatchers.IO) {
                            c.db.auditar(u.nombre, "INVITADO", "Revocar pase ${p.hash.take(8)}", if (r.isSuccess) "OK" else "FALLA")
                        }
                        if (r.isSuccess) {
                            recien = null
                            recargar++
                            aviso("Pase revocado. Si alguien lo estaba usando, su visita termina en unos segundos.")
                        } else {
                            aviso(r.exceptionOrNull()?.message ?: "No se pudo revocar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilaPase(p: PaseNube, alRevocar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                (if (p.puedeActivar) "Regar y ventilar" else "Solo mirar") + " · " + tiempoRestante(p.vence),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                when {
                    p.ultimoVisitante == null -> "aún sin usar"
                    p.usos > 1 -> "lo usó ${p.ultimoVisitante} (${p.usos} entradas)"
                    else -> "lo usó ${p.ultimoVisitante}"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = alRevocar) { Text("Revocar") }
    }
}

/**
 * Nube (Firebase): estado de la subida del ESP32 y "Vincular ESP32", que crea su cuenta y entrega
 * lo que hay que pegar en el sketch (se muestra una sola vez).
 */
@Composable
fun SeccionNube(c: Contenedor, u: Usuario, aviso: (String) -> Unit) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var vinculado by remember { mutableStateOf<Boolean?>(null) }
    var ultimo by remember { mutableStateOf<LecturaEsp32?>(null) }
    var vinculando by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf(false) }
    var config by remember { mutableStateOf<ConfigEsp32?>(null) }

    LaunchedEffect(u.invernadero) {
        vinculado = runCatching { c.nube.dispositivo(u) != null }.getOrNull()
        c.nube.estado(u.invernadero).catch { }.collect { ultimo = it }
    }

    fun vincular() {
        vinculando = true
        scope.launch {
            val r = runCatching { c.nube.vincularEsp32(u) }
            withContext(Dispatchers.IO) { c.db.auditar(u.nombre, "NUBE", "Vincular ESP32 (credenciales nuevas)", if (r.isSuccess) "OK" else "FALLA") }
            vinculando = false
            r.onSuccess { config = it; vinculado = true }.onFailure { aviso(it.message ?: "No se pudo vincular el ESP32") }
        }
    }

    Seccion("Nube") {
        Text(
            "El ESP32 sube a Firebase un estado cada 2 minutos y un resumen cada 5. Así ves tu invernadero desde " +
                "cualquier teléfono donde entres con tu cuenta, y el Historial se completa aunque este teléfono no haya estado cerca.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            when {
                ultimo != null -> "Último dato del ESP32 en la nube: ${haceCuanto(ultimo?.recibidoEn ?: 0L)}"
                vinculado == true -> "ESP32 vinculado, pero aún no sube datos. Revisa que tenga Internet y las credenciales en el sketch."
                vinculado == false -> "Aún no hay un ESP32 vinculado a tu cuenta."
                else -> "Revisando la nube…"
            },
            style = MaterialTheme.typography.bodyLarge
        )
        OutlinedButton(onClick = { if (vinculado == true) confirmar = true else vincular() }, enabled = !vinculando) {
            Text(if (vinculando) "Vinculando…" else if (vinculado == true) "Generar credenciales nuevas" else "Vincular ESP32")
        }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Credenciales nuevas?") },
            text = { Text("El ESP32 actual dejará de poder subir datos hasta que cargues el sketch con las credenciales nuevas.") },
            confirmButton = { TextButton(onClick = { confirmar = false; vincular() }) { Text("Generar") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
        )
    }

    config?.let { cfg ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Pega esto en el sketch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Reemplaza estas 5 líneas en invernadero_esp32.ino (sección NUBE) y vuelve a cargarlo. La clave no se guarda en el teléfono: solo la verás ahora.")
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                        SelectionContainer {
                            Text(
                                cfg.comoCodigo(),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { config = null }) { Text("Listo") } },
            dismissButton = {
                TextButton(onClick = { copiarSensible(contexto, "Credenciales ESP32", cfg.comoCodigo()); aviso("Copiado") }) { Text("Copiar") }
            }
        )
    }
}
