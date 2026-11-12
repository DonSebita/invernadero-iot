package com.invernadero.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tarjeta Material con título. */
@Composable
fun Seccion(titulo: String, modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            contenido()
        }
    }
}

/** RadioButton con texto, accesible (toda la fila es tocable). */
@Composable
fun OpcionRadio(texto: String, seleccionado: Boolean, habilitado: Boolean = true, ayuda: String? = null, alElegir: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = seleccionado, enabled = habilitado, role = Role.RadioButton, onClick = alElegir)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = seleccionado, onClick = null, enabled = habilitado)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(texto, style = MaterialTheme.typography.bodyLarge)
            if (ayuda != null) Text(ayuda, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Fila "etiqueta ........ valor" con el valor en verde. */
@Composable
fun Dato(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(etiqueta, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(valor, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

/** Gráfica de línea simple para el historial. */
@Composable
fun GraficaLinea(valores: List<Float>, color: Color, unidad: String) {
    val suave = MaterialTheme.colorScheme.onSurfaceVariant
    if (valores.size < 2) {
        Text("Aún no hay datos suficientes (se guarda una lectura cada 30 s).", style = MaterialTheme.typography.bodySmall, color = suave)
        return
    }
    val min = valores.min()
    val max = valores.max()
    val rango = (max - min).coerceAtLeast(1f)
    Row(Modifier.fillMaxWidth()) {
        Text("mín %.1f%s".format(min, unidad), style = MaterialTheme.typography.labelSmall, color = suave)
        Spacer(Modifier.weight(1f))
        Text("máx %.1f%s".format(max, unidad), style = MaterialTheme.typography.labelSmall, color = suave)
    }
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val paso = size.width / (valores.size - 1)
        fun punto(i: Int) = Offset(i * paso, size.height * (1f - (valores[i] - min) / rango))
        val linea = Path().apply {
            moveTo(punto(0).x, punto(0).y)
            for (i in 1 until valores.size) lineTo(punto(i).x, punto(i).y)
        }
        val relleno = Path().apply {
            addPath(linea)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(relleno, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
        drawPath(linea, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 4.dp.toPx(), punto(valores.size - 1))
    }
}

fun formatoFecha(ts: Long): String = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(ts))
