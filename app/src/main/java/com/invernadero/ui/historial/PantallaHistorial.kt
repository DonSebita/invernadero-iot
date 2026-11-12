package com.invernadero.ui.historial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.invernadero.Contenedor
import com.invernadero.conexion.EstadoConexion
import com.invernadero.datos.RegistroAuditoria
import com.invernadero.datos.RegistroLectura
import com.invernadero.ui.Dato
import com.invernadero.ui.GraficaLinea
import com.invernadero.ui.Seccion
import com.invernadero.ui.formatoFecha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Historial guardado en SQLite + métricas de estabilidad + registro de auditoría. */
@Composable
fun PantallaHistorial(c: Contenedor) {
    val estado by c.conexion.estado.collectAsState()
    var lecturas by remember { mutableStateOf<List<RegistroLectura>>(emptyList()) }
    var auditoria by remember { mutableStateOf<List<RegistroAuditoria>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            val (l, a) = withContext(Dispatchers.IO) { c.db.ultimasLecturas(120).reversed() to c.db.ultimaAuditoria(60) }
            lecturas = l
            auditoria = a
            delay(10_000)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Historial", style = MaterialTheme.typography.headlineSmall) }
        item { TarjetaEstabilidad(estado) }
        item {
            Seccion("Temperatura (°C) — últimas ${lecturas.size} lecturas") {
                GraficaLinea(lecturas.map { it.temperatura }, Color(0xFFFF7043), " °C")
            }
        }
        item {
            Seccion("Humedad de suelo promedio (%)") {
                GraficaLinea(lecturas.map { it.humedadSuelo }, MaterialTheme.colorScheme.secondary, "%")
            }
        }
        item {
            Seccion("Ventilador (PWM)") {
                GraficaLinea(lecturas.map { it.pwm.toFloat() }, MaterialTheme.colorScheme.primary, "")
            }
        }
        item { Text("Registro de auditoría", style = MaterialTheme.typography.titleMedium) }
        items(auditoria) { a ->
            ListItem(
                overlineContent = { Text(formatoFecha(a.ts)) },
                headlineContent = { Text("${a.accion} · ${a.resultado}") },
                supportingContent = { Text("${a.usuario} — ${a.detalle}") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            HorizontalDivider()
        }
    }
}

/** Evidencia para "estabilidad y eficiencia de la interconexión". */
@Composable
private fun TarjetaEstabilidad(e: EstadoConexion) {
    val minutos = (System.currentTimeMillis() - e.desde) / 60_000
    val totalCmd = e.comandosOk + e.comandosFallidos
    val esperadas = if (e.intervaloPromedioMs > 0) ((System.currentTimeMillis() - e.desde) / 2000).coerceAtLeast(1) else 0
    Seccion("Estabilidad de la conexión") {
        Dato("Medio", e.descripcion)
        Dato("Estado", if (e.conectado) "Conectado" else (e.mensaje ?: "Sin conexión"))
        Dato("Tiempo midiendo", "$minutos min")
        Dato("Lecturas recibidas", if (esperadas > 0) "${e.recibidos} (~${(e.recibidos * 100 / esperadas).coerceAtMost(100)}% de las esperadas)" else "${e.recibidos}")
        Dato("Intervalo promedio", "${e.intervaloPromedioMs} ms (esperado 2000 ms)")
        Dato("Latencia promedio / máx.", "${e.latenciaPromedioMs} / ${e.latenciaMaxMs} ms")
        Dato("Cortes (> 6 s sin datos)", "${e.cortes}")
        Dato("Fallas / reintentos", "${e.fallas}")
        Dato("Comandos OK / fallidos", "${e.comandosOk} / ${e.comandosFallidos}" + if (totalCmd > 0) "  (${e.comandosOk * 100 / totalCmd}% éxito)" else "")
        Dato("Comandos denegados (rol o inválidos)", "${e.comandosDenegados}")
        if (e.compartiendo) Dato("Teléfonos remotos conectados", "${e.telefonosConectados}")
    }
}
