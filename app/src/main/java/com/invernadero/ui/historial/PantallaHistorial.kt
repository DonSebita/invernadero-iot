@file:OptIn(ExperimentalMaterial3Api::class)

package com.invernadero.ui.historial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.invernadero.Contenedor
import com.invernadero.conexion.ParametrosControl
import com.invernadero.conexion.haceCuanto
import com.invernadero.datos.BaseDatos
import com.invernadero.datos.RegistroLectura
import com.invernadero.nube.AlertaNube
import com.invernadero.datos.nombrePlanta
import com.invernadero.ui.Encabezado
import com.invernadero.ui.GraficaSerie
import com.invernadero.ui.PuntoSerie
import com.invernadero.ui.Seccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class Rango(val etiqueta: String, val ms: Long, val formato: String)

private val Rangos = listOf(
    Rango("1 hora", 3_600_000L, "HH:mm"),
    Rango("24 horas", 24 * 3_600_000L, "HH:mm"),
    Rango("7 días", 7 * 24 * 3_600_000L, "EEE d"),
)

/** Cómo han evolucionado tus plantas. El registro de actividad está en su propia pantalla. */
@Composable
fun PantallaHistorial(c: Contenedor, alVerRegistro: () -> Unit) {
    var rango by rememberSaveable { mutableIntStateOf(1) }
    var planta by rememberSaveable { mutableIntStateOf(-1) }        // -1 = todas (promedio)
    var locales by remember { mutableStateOf<List<RegistroLectura>>(emptyList()) }
    var enNube by remember { mutableStateOf<List<RegistroLectura>>(emptyList()) }
    var avisos by remember { mutableStateOf<List<AlertaNube>>(emptyList()) }
    var periodo by remember { mutableStateOf(0L to 0L) }               // [desde, hasta] del eje X
    val reglas = c.conexion.lectura.collectAsState().value?.parametros ?: ParametrosControl.FABRICA
    val invernadero = c.sesion.usuario.collectAsState().value?.invernadero.orEmpty()

    LaunchedEffect(rango) {
        locales = emptyList()
        while (true) {
            val ahora = System.currentTimeMillis()
            val desde = ahora - Rangos[rango].ms
            locales = withContext(Dispatchers.IO) { c.db.lecturasCrudasDesde(desde) }
            periodo = desde to ahora
            delay(30_000)                     // se guarda una lectura cada 30 s
        }
    }
    // Resúmenes de 5 min que subió el ESP32: rellenan lo que este teléfono no alcanzó a guardar
    LaunchedEffect(rango, invernadero) {
        enNube = emptyList()
        if (invernadero.isNotEmpty()) {
            c.nube.resumenes(invernadero, System.currentTimeMillis() - Rangos[rango].ms).catch { }.collect { enNube = it }
        }
    }
    LaunchedEffect(invernadero) {
        if (invernadero.isNotEmpty()) c.nube.alertas(invernadero).catch { }.collect { avisos = it }
    }
    val lecturas = remember(locales, enNube) { BaseDatos.agrupar(BaseDatos.combinar(locales, enNube)) }

    val sensores = lecturas.maxOfOrNull { it.humedades.size } ?: 0
    val p = if (planta < sensores) planta else -1
    val formato = remember(rango) { SimpleDateFormat(Rangos[rango].formato, Locale.forLanguageTag("es")) }
    val eje: (Long) -> String = { formato.format(Date(it)) }
    val cs = MaterialTheme.colorScheme

    val nombres by c.plantas.nombres.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Encabezado("Así han crecido tus plantas", "Humedad de la tierra, temperatura y aire a lo largo del tiempo.")
        }

        // ── Filtros ──
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Filtro("Periodo") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Rangos.forEachIndexed { i, r ->
                            SegmentedButton(
                                selected = rango == i,
                                onClick = { rango = i },
                                shape = SegmentedButtonDefaults.itemShape(i, Rangos.size),
                                label = { Text(r.etiqueta) }
                            )
                        }
                    }
                }
                if (sensores > 1) Filtro("Planta") {
                    val ultima = lecturas.lastOrNull()
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            ChipPlanta("Todas", ultima?.humedadSuelo, p == -1) { planta = -1 }
                        }
                        items(sensores) { i ->
                            ChipPlanta(nombrePlanta(nombres, i), ultima?.humedades?.getOrNull(i), p == i) { planta = i }
                        }
                    }
                }
            }
        }

        // ── Avisos que subió el ESP32 a la nube (calor, frío, tierra seca, sensor sin responder…) ──
        val recientes = avisos.filter { it.ts >= System.currentTimeMillis() - 7 * 24 * 3_600_000L }.take(5)
        if (recientes.isNotEmpty()) item {
            Seccion("Avisos del invernadero", subtitulo = "Últimos 7 días, desde la nube") {
                recientes.forEach { a ->
                    Column {
                        Text(a.detalle, style = MaterialTheme.typography.bodyLarge)
                        Text(haceCuanto(a.ts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ── Gráficas ──
        item {
            Seccion(
                "Humedad de la tierra",
                subtitulo = when {
                    p >= 0 -> nombrePlanta(nombres, p)
                    sensores > 1 -> "Promedio de $sensores sensores"
                    else -> null
                }
            ) {
                GraficaSerie(
                    puntos = lecturas.map { PuntoSerie(it.ts, if (p == -1) it.humedadSuelo else it.humedades.getOrNull(p) ?: it.humedadSuelo) },
                    color = cs.secondary,
                    unidad = "%",
                    nombre = "Humedad de la tierra",
                    etiquetaEje = eje,
                    limites = 0f..100f,
                    spanMinimo = 10f,
                    desde = periodo.first, hasta = periodo.second
                )
            }
        }
        item {
            Seccion("Temperatura", subtitulo = "La franja marca donde la ventana abre sola (${reglas.ventanaDesde}–${reglas.ventanaHasta} °C)") {
                GraficaSerie(
                    puntos = lecturas.map { PuntoSerie(it.ts, it.temperatura) },
                    color = cs.tertiary,
                    unidad = "°C",
                    nombre = "Temperatura",
                    etiquetaEje = eje,
                    spanMinimo = 4f,
                    banda = reglas.ventanaDesde.toFloat()..reglas.ventanaHasta.toFloat(),
                    etiquetaBanda = "ventana automática",
                    desde = periodo.first, hasta = periodo.second
                )
            }
        }
        val aire = lecturas.mapNotNull { l -> l.humedadAire?.let { PuntoSerie(l.ts, it) } }
        if (aire.size >= 2) item {
            Seccion("Humedad del aire") {
                GraficaSerie(
                    puntos = aire,
                    color = cs.primary,
                    unidad = "%",
                    nombre = "Humedad del aire",
                    etiquetaEje = eje,
                    limites = 0f..100f,
                    spanMinimo = 10f,
                    desde = periodo.first, hasta = periodo.second,
                    banda = if (reglas.ventilaPorHumedad) reglas.humedadMaxima.toFloat()..100f else null,
                    etiquetaBanda = "se ventila"
                )
            }
        }

        // ── Enlace al registro ──
        item {
            Surface(
                onClick = alVerRegistro,
                shape = MaterialTheme.shapes.large,
                color = cs.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ver registro de actividad", style = MaterialTheme.typography.titleMedium, color = cs.onSecondaryContainer)
                        Text(
                            "Entradas, órdenes enviadas, visitas y estabilidad de la conexión.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun Filtro(titulo: String, contenido: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        contenido()
    }
}

/** Chip de planta con su humedad actual, para elegir sabiendo cuál está más seca. */
@Composable
private fun ChipPlanta(nombre: String, humedad: Float?, elegido: Boolean, alElegir: () -> Unit) {
    FilterChip(
        selected = elegido,
        onClick = alElegir,
        label = {
            Text(if (humedad != null) "$nombre · ${humedad.roundToInt()}%" else nombre)
        },
        leadingIcon = if (elegido) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null
    )
}
