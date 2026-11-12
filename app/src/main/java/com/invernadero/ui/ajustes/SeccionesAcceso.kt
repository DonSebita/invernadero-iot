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
import com.invernadero.datos.Invitacion
import com.invernadero.datos.Usuario
import com.invernadero.ui.OpcionRadio
import com.invernadero.ui.Seccion
import com.invernadero.ui.copiarSensible
import kotlinx.coroutines.Dispatchers
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
    val min = ((vence - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
    return when {
        min >= 48 * 60 -> "quedan ${min / (24 * 60)} días"
        min >= 60 -> "quedan ${min / 60} h"
        else -> "quedan $min min"
    }
}

/** Pases de visita: el usuario genera una clave temporal para alguien de confianza. */
@Composable
fun SeccionInvitar(c: Contenedor, u: Usuario, aviso: (String) -> Unit) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var duracion by rememberSaveable { mutableIntStateOf(1) }
    var puedeActivar by rememberSaveable { mutableStateOf(u.rol.puedeActivar) }
    var generando by remember { mutableStateOf(false) }
    var recien by remember { mutableStateOf<Pair<String, Long>?>(null) }   // clave, vence
    var pases by remember { mutableStateOf<List<Invitacion>>(emptyList()) }
    var recargar by remember { mutableIntStateOf(0) }

    LaunchedEffect(recargar) { pases = withContext(Dispatchers.IO) { c.invitaciones.activas(u.nombre) } }

    fun generar() {
        generando = true
        val d = Duraciones[duracion]
        val activar = puedeActivar && u.rol.puedeActivar          // nunca más permisos que quien invita
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                c.invitaciones.crear(u.nombre, d.ms, activar).also {
                    c.db.auditar(u.nombre, "INVITADO", "Pase de visita por ${d.etiqueta} (${if (activar) "puede activar" else "solo mirar"})",
                        if (it.isSuccess) "OK" else "FALLA")
                }
            }
            generando = false
            r.onSuccess { recien = it to (System.currentTimeMillis() + d.ms); recargar++ }
                .onFailure { aviso(it.message ?: "No se pudo crear el pase") }
        }
    }

    fun compartir(clave: String, vence: Long) {
        val texto = "Te dejo pasar a mi invernadero GreenBox.\n" +
            "Abre la app, toca «Invitado» y escribe esta clave: $clave\n" +
            "Vale hasta el ${horaYDia(vence)}."
        val envio = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, texto) }
        contexto.startActivity(Intent.createChooser(envio, "Compartir pase de visita"))
    }

    Seccion("Abre la puerta a alguien") {
        Text(
            "¿No vas a estar en casa? Genera una clave temporal para que un amigo o vecino cuide tus plantas. " +
                "La clave abre GreenBox en este dispositivo, así que déjalo en casa o en el invernadero.",
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
            OpcionRadio("Solo mirar", !puedeActivar, ayuda = "Ve el invernadero en vivo, sin tocar nada") { puedeActivar = false }
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
                        Text(clave, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 3.sp))
                    }
                    Text("Vale hasta el ${horaYDia(vence)}. Solo la verás ahora: compártela de inmediato.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { compartir(clave, vence) }) { Text("Compartir") }
                        OutlinedButton(onClick = { copiarSensible(contexto, "Pase GreenBox", clave); aviso("Clave copiada") }) { Text("Copiar") }
                    }
                }
            }
        }

        if (pases.isNotEmpty()) {
            HorizontalDivider()
            Text("Tus pases abiertos", style = MaterialTheme.typography.titleSmall)
            pases.forEach { p ->
                FilaPase(p, mostrarCreador = false) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            c.invitaciones.revocar(p.id, u)
                            c.db.auditar(u.nombre, "INVITADO", "Revocar pase #${p.id}", "OK")
                        }
                        if (recien != null) recien = null
                        recargar++
                        aviso("Pase revocado. Si alguien lo estaba usando, su visita termina en unos segundos.")
                    }
                }
            }
        }
    }
}

@Composable
fun FilaPase(p: Invitacion, mostrarCreador: Boolean, alRevocar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                (if (p.puedeActivar) "Regar y ventilar" else "Solo mirar") + " · " + tiempoRestante(p.vence),
                style = MaterialTheme.typography.bodyLarge
            )
            val detalle = buildList {
                if (mostrarCreador) add("de ${p.creador}")
                add(if (p.ultimoVisitante != null) "lo usó ${p.ultimoVisitante}" else "aún sin usar")
            }.joinToString(" · ")
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = alRevocar) { Text("Revocar") }
    }
}

/** Generar una semilla nueva (p. ej. cuentas creadas antes de que existieran, o si se perdió). */
@Composable
fun SeccionSemilla(c: Contenedor, u: Usuario) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var semilla by remember { mutableStateOf<String?>(null) }
    var generando by remember { mutableStateOf(false) }

    Seccion("Semilla de recuperación") {
        Text(
            "Con ella haces brotar una contraseña nueva si olvidas la tuya. Generar otra invalida la anterior.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = {
                generando = true
                scope.launch {
                    val s = withContext(Dispatchers.Default) { c.usuarios.nuevaSemilla(u.nombre) }
                    withContext(Dispatchers.IO) { c.db.auditar(u.nombre, "RECUPERAR", "Nueva semilla de recuperación", "OK") }
                    generando = false
                    semilla = s
                }
            },
            enabled = !generando
        ) { Text(if (generando) "Generando…" else "Generar nueva semilla") }
    }

    semilla?.let { s ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Guarda tu semilla") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Anótala en papel o en un gestor de contraseñas. No volverás a verla.")
                    SelectionContainer {
                        Text(s, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { semilla = null }) { Text("La guardé") } },
            dismissButton = { TextButton(onClick = { copiarSensible(contexto, "Semilla GreenBox", s) }) { Text("Copiar") } }
        )
    }
}
