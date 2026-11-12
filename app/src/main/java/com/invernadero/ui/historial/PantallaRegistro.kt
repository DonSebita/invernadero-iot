@file:OptIn(ExperimentalMaterial3Api::class)

package com.invernadero.ui.historial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.invernadero.Contenedor
import com.invernadero.conexion.EstadoConexion
import com.invernadero.datos.RegistroAuditoria
import com.invernadero.ui.Dato
import com.invernadero.ui.Seccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class Filtro(val etiqueta: String, val acciones: Set<String>?)

private val Filtros = listOf(
    Filtro("Todo", null),
    Filtro("Entradas", setOf("LOGIN", "SESION", "RECUPERAR", "USUARIO")),
    Filtro("Órdenes", setOf("COMANDO")),
    Filtro("Visitas", setOf("INVITADO")),
    Filtro("Ajustes", setOf("AJUSTES", "NUBE")),
)

private fun etiquetaAccion(a: String) = when (a) {
    "LOGIN" -> "Entrada"
    "SESION" -> "Sesión"
    "RECUPERAR" -> "Recuperación"
    "USUARIO" -> "Cuenta"
    "COMANDO" -> "Orden"
    "INVITADO" -> "Visita"
    "AJUSTES" -> "Ajustes"
    "NUBE" -> "Nube"
    else -> a
}

private val Es = Locale.forLanguageTag("es")

/** "Hoy", "Ayer" o "lun 10 nov". */
private fun nombreDia(ts: Long): String {
    val hoy = Calendar.getInstance()
    val dia = Calendar.getInstance().apply { timeInMillis = ts }
    fun mismoDia(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (mismoDia(hoy, dia)) return "Hoy"
    hoy.add(Calendar.DAY_OF_YEAR, -1)
    if (mismoDia(hoy, dia)) return "Ayer"
    return SimpleDateFormat("EEEE d 'de' MMMM", Es).format(Date(ts)).replaceFirstChar { it.uppercase() }
}

/** Registro de auditoría (quién entró, qué órdenes se enviaron, visitas) + estabilidad de la conexión. */
@Composable
fun PantallaRegistro(c: Contenedor, alVolver: () -> Unit) {
    BackHandler(onBack = alVolver)
    val estado by c.conexion.estado.collectAsState()
    var filtro by rememberSaveable { mutableIntStateOf(0) }
    var soloFallidos by rememberSaveable { mutableStateOf(false) }
    var registros by remember { mutableStateOf<List<RegistroAuditoria>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            registros = withContext(Dispatchers.IO) { c.db.ultimaAuditoria(200) }
            delay(10_000)
        }
    }
    val base = if (soloFallidos) registros.filter { it.resultado != "OK" } else registros
    val visibles = Filtros[filtro].acciones?.let { a -> base.filter { it.accion in a } } ?: base
    val porDia = visibles.groupBy { nombreDia(it.ts) }
    val hora = remember { SimpleDateFormat("HH:mm:ss", Es) }
    val fallidos = registros.count { it.resultado != "OK" }
    val cs = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registro de actividad", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al historial") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { relleno ->
        LazyColumn(
            Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item { Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { TarjetaEstabilidad(estado) } }

            // ── Filtros: categoría (con cuántos hay) + solo fallidos ──
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = soloFallidos,
                            onClick = { soloFallidos = !soloFallidos },
                            label = { Text("Solo fallidos · $fallidos") },
                            leadingIcon = {
                                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = cs.errorContainer,
                                selectedLabelColor = cs.onErrorContainer,
                                selectedLeadingIconColor = cs.onErrorContainer
                            )
                        )
                    }
                    item { VerticalDivider(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 24.dp)) }
                    items(Filtros.size) { i ->
                        val f = Filtros[i]
                        val cuantos = f.acciones?.let { a -> base.count { it.accion in a } } ?: base.size
                        FilterChip(
                            selected = filtro == i,
                            onClick = { filtro = i },
                            label = { Text("${f.etiqueta} · $cuantos") },
                            leadingIcon = if (filtro == i) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null
                        )
                    }
                }
            }

            if (visibles.isEmpty()) item {
                Text(
                    if (soloFallidos) "Sin fallas en este filtro. Todo en orden." else "Nada por aquí todavía.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                )
            }

            porDia.forEach { (dia, lista) ->
                item(key = "dia-$dia") {
                    Text(
                        dia,
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onBackground,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() }
                    )
                }
                items(lista) { a -> FilaRegistro(a, hora) }
            }
        }
    }
}

/** Una fila del registro: hora a la izquierda (columna alineada), qué pasó y quién a la derecha. */
@Composable
private fun FilaRegistro(a: RegistroAuditoria, hora: SimpleDateFormat) {
    val cs = MaterialTheme.colorScheme
    val falla = a.resultado != "OK"
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Text(
                hora.format(Date(a.ts)),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 64.dp).padding(top = 2.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${etiquetaAccion(a.accion)} · ${a.usuario}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (falla) cs.error else cs.onSurface
                )
                if (a.detalle.isNotBlank() || falla) {
                    Text(
                        a.detalle + if (falla) (if (a.detalle.isNotBlank()) " · " else "") + a.resultado else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (falla) cs.error else cs.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 92.dp, end = 16.dp), color = cs.outlineVariant.copy(alpha = 0.6f))
    }
}

/** Evidencia para "estabilidad y eficiencia de la interconexión": resumen arriba, detalle al tocar. */
@Composable
private fun TarjetaEstabilidad(e: EstadoConexion) {
    var detalle by rememberSaveable { mutableStateOf(false) }
    val ahora = System.currentTimeMillis()
    val minutos = (ahora - e.desde) / 60_000
    val totalCmd = e.comandosOk + e.comandosFallidos
    val esperadas = if (e.intervaloPromedioMs > 0) ((ahora - e.desde) / 2000).coerceAtLeast(1) else 0
    val pctLecturas = if (esperadas > 0) (e.recibidos * 100 / esperadas).coerceAtMost(100) else null

    Seccion(
        "Estabilidad de la conexión",
        subtitulo = "${e.descripcion} · " + if (e.conectado) "conectado" else (e.mensaje ?: "sin conexión"),
        accion = { TextButton(onClick = { detalle = !detalle }) { Text(if (detalle) "Ocultar" else "Detalle") } }
    ) {
        Row(Modifier.fillMaxWidth()) {
            Indicador("Lecturas", pctLecturas?.let { "$it%" } ?: "${e.recibidos}", Modifier.weight(1f))
            Indicador("Latencia", "${e.latenciaPromedioMs} ms", Modifier.weight(1f))
            Indicador("Cortes", "${e.cortes}", Modifier.weight(1f), alerta = e.cortes > 0)
        }
        if (detalle) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Dato("Tiempo midiendo", "$minutos min")
            Dato("Lecturas recibidas", if (esperadas > 0) "${e.recibidos} de ~$esperadas" else "${e.recibidos}")
            Dato("Intervalo promedio", "${e.intervaloPromedioMs} ms (esperado 2000 ms)")
            Dato("Latencia promedio / máx.", "${e.latenciaPromedioMs} / ${e.latenciaMaxMs} ms")
            Dato("Cortes (> 6 s sin datos)", "${e.cortes}")
            Dato("Fallas / reintentos", "${e.fallas}")
            Dato("Comandos OK / fallidos", "${e.comandosOk} / ${e.comandosFallidos}" + if (totalCmd > 0) " (${e.comandosOk * 100 / totalCmd}% éxito)" else "")
            Dato("Comandos denegados (permiso o inválidos)", "${e.comandosDenegados}")
            if (e.compartiendo) Dato("Teléfonos remotos conectados", "${e.telefonosConectados}")
        }
    }
}

@Composable
private fun Indicador(etiqueta: String, valor: String, modifier: Modifier, alerta: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(valor, style = MaterialTheme.typography.titleLarge, color = if (alerta) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
