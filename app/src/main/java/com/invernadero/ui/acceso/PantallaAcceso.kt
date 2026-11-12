@file:OptIn(ExperimentalMaterial3Api::class)

package com.invernadero.ui.acceso

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invernadero.Contenedor
import com.invernadero.datos.normalizarNombre
import com.invernadero.nube.ErrorNube
import com.invernadero.nube.Freno
import com.invernadero.nube.GoogleCancelado
import com.invernadero.nube.Nube
import com.invernadero.nube.tokenDeGoogle
import com.invernadero.seguridad.Contrasenas
import com.invernadero.ui.CampoClave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────
//  ACCESO a GreenBox (Firebase Authentication): entrar con correo
//  o con Google, crear cuenta, pase de visita y recuperar la
//  contraseña por correo. La misma cuenta sirve en todos tus teléfonos.
// ─────────────────────────────────────────────────────────────

private enum class Vista(val pestana: String) {
    ENTRAR("Entrar"), CREAR("Crear cuenta"), INVITADO("Invitado"), RECUPERAR("")
}

private val Pestanas = listOf(Vista.ENTRAR, Vista.CREAR, Vista.INVITADO)

private val SalidaSuave = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Composable
fun PantallaAcceso(c: Contenedor) {
    var listo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // "Recordarme": si la cuenta quedó recordada se entra directo (también sin Internet)
        val recordado = withContext(Dispatchers.IO) { c.nube.restaurar() }
        if (recordado != null) {
            withContext(Dispatchers.IO) { c.db.auditar(recordado.nombre, "LOGIN", "Inicio recordado en este teléfono", "OK") }
            c.sesion.iniciar(recordado, recordar = true)
            return@LaunchedEffect
        }
        listo = true
    }

    // En el acceso el verde de las hojas es el color principal (botones, pestañas, foco)
    val base = MaterialTheme.colorScheme
    val verde = base.copy(
        primary = base.secondary,
        onPrimary = base.onSecondary,
        primaryContainer = base.secondaryContainer,
        onPrimaryContainer = base.onSecondaryContainer
    )

    MaterialTheme(colorScheme = verde, typography = MaterialTheme.typography) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Paisaje(Modifier.fillMaxWidth().height(300.dp))
            Encabezado(Modifier.statusBarsPadding().padding(horizontal = 28.dp, vertical = 20.dp))

            Surface(
                modifier = Modifier
                    .padding(top = 250.dp)
                    .fillMaxWidth()
                    .heightIn(min = 520.dp),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = 460.dp)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp)
                            .padding(top = 8.dp, bottom = 32.dp)
                    ) {
                        if (!listo) {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            Acceso(c)
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Paisaje y marca ─────────────────────────

/** Cielo (día o atardecer, según el tema), sol y tres colinas que se asientan al abrir la app. */
@Composable
private fun Paisaje(modifier: Modifier) {
    val tarde = MaterialTheme.colorScheme.background.luminance() < 0.3f
    val cielo = if (tarde) listOf(Color(0xFF2E1F2A), Color(0xFF7A4F55), Color(0xFFD9826B))
                else listOf(Color(0xFF8DB9EC), Color(0xFFBFD8F3), Color(0xFFEAF2FB))
    val sol = if (tarde) Color(0xFFF8C9A0) else Color(0xFFFFE6A6)
    val lejana = if (tarde) Color(0xFF8A5560) else Color(0xFFB5D8B0)
    val media = if (tarde) Color(0xFF4A3940) else Color(0xFF7DB87F)
    val cercana = if (tarde) Color(0xFF1F3A2A) else Color(0xFF3F8A45)
    val brote = if (tarde) Color(0xFF7CFFB0) else Color(0xFF2E7D32)
    val vidrio = Color.White.copy(alpha = if (tarde) 0.18f else 0.55f)

    // El único movimiento del acceso: el paisaje se asienta y el sol sube, con salida exponencial
    val asentar = remember { Animatable(1f) }
    LaunchedEffect(Unit) { asentar.animateTo(0f, tween(1400, easing = SalidaSuave)) }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val a = asentar.value
        drawRect(Brush.verticalGradient(cielo))
        drawCircle(sol.copy(alpha = 0.35f), radius = w * 0.16f, center = Offset(w * 0.8f, h * (0.36f + 0.12f * a)))
        drawCircle(sol, radius = w * 0.085f, center = Offset(w * 0.8f, h * (0.36f + 0.12f * a)))

        translate(top = h * 0.10f * a) { colina(lejana, h * 0.58f, h * 0.05f, 1.3f, 0.6f) }
        translate(top = h * 0.18f * a) {
            colina(media, h * 0.68f, h * 0.045f, 1.8f, 2.1f)
            invernadero(Offset(w * 0.2f, h * 0.665f), 30.dp.toPx(), vidrio, cercana)
        }
        translate(top = h * 0.28f * a) {
            colina(cercana, h * 0.78f, h * 0.035f, 2.4f, 4.0f)
            listOf(0.08f, 0.47f, 0.63f, 0.9f).forEachIndexed { i, x ->
                val px = w * x
                val py = altura(px, w, h * 0.78f, h * 0.035f, 2.4f, 4.0f)
                broteDibujado(Offset(px, py), (10 + i % 2 * 4).dp.toPx(), brote)
            }
        }
    }
}

private fun altura(x: Float, w: Float, base: Float, amp: Float, frec: Float, fase: Float): Float =
    base - amp * sin(x / w * 2f * PI.toFloat() * frec / 2f + fase) - amp * 0.4f * sin(x / w * 7f + fase * 1.7f)

private fun DrawScope.colina(color: Color, base: Float, amp: Float, frec: Float, fase: Float) {
    val w = size.width
    val p = Path().apply {
        moveTo(0f, size.height * 1.5f)
        var x = 0f
        while (x <= w) { lineTo(x, altura(x, w, base, amp, frec, fase)); x += w / 48f }
        lineTo(w, altura(w, w, base, amp, frec, fase))
        lineTo(w, size.height * 1.5f)
        close()
    }
    drawPath(p, color)
}

/** Un pequeño invernadero de vidrio: la casa de GreenBox en la colina. */
private fun DrawScope.invernadero(pie: Offset, ancho: Float, vidrio: Color, linea: Color) {
    val alto = ancho * 0.62f
    val izq = pie.x - ancho / 2
    val techo = Path().apply {
        moveTo(izq, pie.y)
        lineTo(izq, pie.y - alto * 0.55f)
        cubicTo(izq, pie.y - alto * 1.05f, izq + ancho, pie.y - alto * 1.05f, izq + ancho, pie.y - alto * 0.55f)
        lineTo(izq + ancho, pie.y)
        close()
    }
    drawPath(techo, vidrio)
    val trazo = Stroke(1.4f * density, cap = StrokeCap.Round)
    drawPath(techo, linea.copy(alpha = 0.7f), style = trazo)
    for (i in 1..3) {
        val x = izq + ancho * i / 4f
        drawLine(linea.copy(alpha = 0.45f), Offset(x, pie.y), Offset(x, pie.y - alto * 0.8f), 1f * density)
    }
}

/** Un brote: tallo y dos hojas. */
private fun DrawScope.broteDibujado(pie: Offset, alto: Float, color: Color) {
    val punta = Offset(pie.x, pie.y - alto)
    drawLine(color, pie, punta, 1.6f * density, StrokeCap.Round)
    fun hoja(dir: Float) = Path().apply {
        moveTo(punta.x, punta.y + alto * 0.25f)
        quadraticTo(punta.x + dir * alto * 0.7f, punta.y - alto * 0.1f, punta.x + dir * alto * 0.75f, punta.y - alto * 0.45f)
        quadraticTo(punta.x + dir * alto * 0.15f, punta.y - alto * 0.35f, punta.x, punta.y + alto * 0.25f)
    }
    drawPath(hoja(1f), color)
    drawPath(hoja(-0.8f), color)
}

@Composable
private fun Encabezado(modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HojaMarca(Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "GreenBox",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Tu invernadero, a un respiro de distancia",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        )
    }
}

/** Marca: una hoja con su nervio. */
@Composable
fun HojaMarca(modifier: Modifier = Modifier) {
    val hoja = MaterialTheme.colorScheme.primary
    val nervio = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.12f, h * 0.9f)
            cubicTo(w * 0.02f, h * 0.35f, w * 0.45f, h * 0.02f, w * 0.94f, h * 0.06f)
            cubicTo(w * 0.98f, h * 0.55f, w * 0.62f, h * 0.98f, w * 0.12f, h * 0.9f)
            close()
        }
        drawPath(p, hoja)
        drawLine(nervio, Offset(w * 0.12f, h * 0.9f), Offset(w * 0.72f, h * 0.3f), 1.6f * density, StrokeCap.Round)
    }
}

// ───────────────────────── Navegación entre formularios ─────────────────────────

@Composable
private fun Acceso(c: Contenedor) {
    var vista by rememberSaveable { mutableStateOf(Vista.ENTRAR) }
    var correo by rememberSaveable { mutableStateOf("") }
    var avisoEntrar by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = vista == Vista.RECUPERAR) { vista = Vista.ENTRAR }

    if (vista != Vista.RECUPERAR) {
        PrimaryTabRow(
            selectedTabIndex = Pestanas.indexOf(vista),
            containerColor = Color.Transparent,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Pestanas.forEach { v ->
                Tab(
                    selected = vista == v,
                    onClick = { vista = v; avisoEntrar = null },
                    text = { Text(v.pestana, maxLines = 1) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
    }

    AnimatedContent(
        targetState = vista,
        transitionSpec = { fadeIn(tween(240, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
        label = "vista"
    ) { v ->
        Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (v) {
                Vista.ENTRAR -> FormEntrar(c, correo, { correo = it }, avisoEntrar) { vista = Vista.RECUPERAR }
                Vista.CREAR -> FormCrear(c)
                Vista.INVITADO -> FormInvitado(c)
                Vista.RECUPERAR -> FormRecuperar(c, correo, { correo = it }, alVolver = { vista = Vista.ENTRAR }) { aviso ->
                    avisoEntrar = aviso
                    vista = Vista.ENTRAR
                }
            }
        }
    }
}

// ───────────────────────── Piezas comunes ─────────────────────────

@Composable
private fun Titulo(titulo: String, bajada: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        Text(titulo, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(bajada, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CampoNombre(valor: String, etiqueta: String, alCambiar: (String) -> Unit, ayuda: String? = null) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        leadingIcon = { Icon(Icons.Filled.Person, null) },
        supportingText = ayuda?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false)
    )
}

@Composable
private fun Mensaje(texto: String?, error: Boolean = true) {
    if (texto == null) return
    Text(
        texto,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@Composable
private fun BotonPrincipal(texto: String, cargando: Boolean, habilitado: Boolean = true, alPulsar: () -> Unit) {
    Button(
        onClick = alPulsar,
        enabled = habilitado && !cargando,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        if (cargando) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        else Text(texto, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun NotaPie(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun minutos(segundos: Long) = (segundos + 59) / 60

@Composable
private fun Recordarme(valor: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = valor, role = Role.Checkbox, onValueChange = alCambiar),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = valor, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Recordarme en este teléfono", style = MaterialTheme.typography.bodyLarge)
            Text("No tendrás que entrar cada vez. Úsalo solo en tu propio teléfono.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CampoCorreo(valor: String, alCambiar: (String) -> Unit, accionFinal: (() -> Unit)? = null) {
    OutlinedTextField(
        value = valor,
        onValueChange = { alCambiar(it.trim().take(80)) },
        label = { Text("Correo") },
        leadingIcon = { Icon(Icons.Filled.Email, null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = if (accionFinal != null) ImeAction.Done else ImeAction.Next,
            autoCorrectEnabled = false
        ),
        keyboardActions = KeyboardActions(onDone = { accionFinal?.invoke() })
    )
}

/** Línea con una "o" al centro, entre el formulario y el botón de Google. */
@Composable
private fun SeparadorO() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.weight(1f))
        Text("o", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp))
        HorizontalDivider(Modifier.weight(1f))
    }
}

private fun resultadoAuditoria(r: Result<*>): String = when {
    r.isSuccess -> "OK"
    (r.exceptionOrNull() as? ErrorNube)?.bloqueado == true -> "BLOQUEADO"
    else -> "FALLIDO"
}

/** "Continuar con Google": la primera vez crea la cuenta y su invernadero en la nube. */
@Composable
private fun BotonGoogle(c: Contenedor, recordar: Boolean, alError: (String?) -> Unit) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var cargando by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = {
            if (cargando) return@OutlinedButton
            cargando = true
            alError(null)
            scope.launch {
                val r = runCatching { c.nube.entrarConGoogle(tokenDeGoogle(contexto), recordar) }
                cargando = false
                if (r.exceptionOrNull() is GoogleCancelado) return@launch
                withContext(Dispatchers.IO) {
                    c.db.auditar(r.getOrNull()?.nombre ?: "Google", "LOGIN",
                        "Inicio de sesión con Google" + if (recordar) " (recordado en este teléfono)" else "", resultadoAuditoria(r))
                }
                r.onSuccess { c.sesion.iniciar(it, recordar) }
                    .onFailure { alError(it.message ?: "No se pudo entrar con Google") }
            }
        },
        enabled = !cargando,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        if (cargando) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else Text("Continuar con Google", style = MaterialTheme.typography.titleSmall)
    }
}

// ───────────────────────── Entrar ─────────────────────────

@Composable
private fun FormEntrar(
    c: Contenedor,
    correo: String,
    alCambiarCorreo: (String) -> Unit,
    aviso: String?,
    alOlvidar: () -> Unit
) {
    var clave by remember { mutableStateOf("") }          // la contraseña no se guarda en el estado persistente
    var recordar by rememberSaveable { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun ingresar() {
        if (cargando) return
        val limpio = correo.trim()
        if (!Nube.correoValido(limpio) || clave.isEmpty()) { error = "Escribe tu correo y tu contraseña"; return }
        cargando = true
        error = null
        val recordarAhora = recordar
        val claveAhora = clave
        scope.launch {
            val r = runCatching { c.nube.entrarConCorreo(limpio, claveAhora, recordarAhora) }
            withContext(Dispatchers.IO) {
                c.db.auditar(r.getOrNull()?.nombre ?: limpio, "LOGIN",
                    "Inicio de sesión con correo" + if (recordarAhora) " (recordado en este teléfono)" else "", resultadoAuditoria(r))
            }
            cargando = false
            r.onSuccess { clave = ""; c.sesion.iniciar(it, recordarAhora) }
                .onFailure { error = it.message ?: "No se pudo entrar" }
        }
    }

    Titulo("Qué bueno verte de nuevo", "Tus plantas siguieron creciendo mientras no estabas.")
    Mensaje(aviso, error = false)
    CampoCorreo(correo, alCambiarCorreo)
    CampoClave(clave, "Contraseña", { clave = it.take(64) }) { ingresar() }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextButton(onClick = alOlvidar) { Text("¿Olvidaste tu contraseña?") }
    }
    Recordarme(recordar) { recordar = it }
    Mensaje(error)
    BotonPrincipal("Entrar al invernadero", cargando) { ingresar() }
    SeparadorO()
    BotonGoogle(c, recordar) { error = it }
    NotaPie(
        "Tu cuenta vive en la nube (Firebase): entra con ella en cualquier teléfono y verás el mismo invernadero. " +
            "Tras ${Freno.MAX_INTENTOS} intentos fallidos la puerta descansa 5 minutos, y sin «Recordarme» la sesión se cierra sola tras 10 minutos quieta."
    )
}

// ───────────────────────── Crear cuenta ─────────────────────────

@Composable
private fun FormCrear(c: Contenedor) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var correo by rememberSaveable { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var recordar by rememberSaveable { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun crear() {
        if (cargando) return
        val n = normalizarNombre(nombre)
        val limpio = correo.trim()
        error = when {
            !Nube.correoValido(limpio) -> "Escribe un correo válido: ahí te llegará el enlace si olvidas la contraseña"
            else -> Contrasenas.validarPolitica(n, clave) ?: if (clave != confirmar) "Las contraseñas no coinciden" else null
        }
        if (error != null) return
        cargando = true
        val claveAhora = clave
        val recordarAhora = recordar
        scope.launch {
            val r = runCatching { c.nube.crearCuenta(n, limpio, claveAhora, recordarAhora) }
            withContext(Dispatchers.IO) {
                c.db.auditar(n, "USUARIO", "Cuenta creada con correo (y su invernadero en la nube)", if (r.isSuccess) "OK" else "FALLIDO")
            }
            cargando = false
            r.onSuccess { clave = ""; confirmar = ""; c.sesion.iniciar(it, recordarAhora) }
                .onFailure { error = it.message ?: "No se pudo crear la cuenta" }
        }
    }

    Titulo("Siembra tu cuenta", "Un brote nuevo: podrás ver cómo crecen tus plantas y regarlas o ventilarlas desde cualquier teléfono.")
    CampoNombre(nombre, "Tu nombre", { nombre = it.take(30) }, ayuda = "Así te verán en el registro y en los pases que compartas. De 3 a 30 caracteres")
    CampoCorreo(correo, { correo = it })
    CampoClave(clave, "Contraseña", { clave = it.take(64) }, ayuda = "Mínimo 8 caracteres, con letras y números")
    CampoClave(confirmar, "Repite la contraseña", { confirmar = it.take(64) }) { crear() }
    Recordarme(recordar) { recordar = it }
    Mensaje(error)
    BotonPrincipal("Crear mi cuenta", cargando) { crear() }
    SeparadorO()
    BotonGoogle(c, recordar) { error = it }
    NotaPie("Te enviaremos un correo para confirmar tu dirección. Si olvidas la contraseña, te llegará ahí un enlace para crear otra.")
}

// ───────────────────────── Pase de visita ─────────────────────────

@Composable
private fun FormInvitado(c: Contenedor) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var codigo by remember { mutableStateOf(TextFieldValue("")) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun entrar() {
        if (cargando) return
        val visitante = normalizarNombre(nombre)
        error = Nube.validarVisitante(visitante)
            ?: if (Nube.normalizarPase(codigo.text).length != Nube.LARGO_PASE) "La clave temporal tiene 12 caracteres, como 7KQ4-M2PX-9WRT" else null
        if (error != null) return
        cargando = true
        val clave = codigo.text
        scope.launch {
            val r = runCatching { c.nube.entrarDeVisita(visitante, clave) }
            withContext(Dispatchers.IO) {
                c.db.auditar("$visitante (invitado)", "INVITADO",
                    r.getOrNull()?.let { "Entrada con pase de ${it.anfitrion}" } ?: "Intento de entrada con pase", resultadoAuditoria(r))
            }
            cargando = false
            r.onSuccess { codigo = TextFieldValue(""); c.sesion.iniciar(it) }
                .onFailure { error = it.message ?: "No se pudo abrir la visita" }
        }
    }

    Titulo("Pase de visita", "Alguien te abrió la puerta de su invernadero. Escribe la clave temporal que te compartió.")
    CampoNombre(nombre, "Tu nombre", { nombre = it.take(24) }, ayuda = "Así sabrá tu anfitrión quién pasó a regar")
    OutlinedTextField(
        value = codigo,
        onValueChange = { nuevo -> val f = Nube.formatearPase(nuevo.text); codigo = TextFieldValue(f, TextRange(f.length)) },
        label = { Text("Clave temporal") },
        placeholder = { Text("XXXX-XXXX-XXXX") },
        leadingIcon = { Icon(Icons.Filled.Lock, null) },
        textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false
        ),
        keyboardActions = KeyboardActions(onDone = { entrar() })
    )
    Mensaje(error)
    BotonPrincipal("Entrar de visita", cargando) { entrar() }
    NotaPie("Necesitas Internet para entrar. El pase dura lo que tu anfitrión decidió, de 1 hora a 3 días; al vencer, la visita termina sola. No podrás cambiar ajustes ni invitar a otros.")
}

// ───────────────────────── Olvidé mi contraseña ─────────────────────────

@Composable
private fun FormRecuperar(
    c: Contenedor,
    correo: String,
    alCambiarCorreo: (String) -> Unit,
    alVolver: () -> Unit,
    alEnviar: (String) -> Unit
) {
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun enviar() {
        if (cargando) return
        val limpio = correo.trim()
        if (!Nube.correoValido(limpio)) { error = "Escribe el correo de tu cuenta"; return }
        cargando = true
        error = null
        scope.launch {
            val r = runCatching { c.nube.recuperar(limpio) }
            withContext(Dispatchers.IO) { c.db.auditar(limpio, "RECUPERAR", "Enlace para nueva contraseña", if (r.isSuccess) "OK" else "FALLIDO") }
            cargando = false
            r.onSuccess { alEnviar("Si ese correo tiene una cuenta, te llegó un enlace para crear una contraseña nueva. Revisa también la carpeta de spam.") }
                .onFailure { error = it.message ?: "No se pudo enviar el enlace" }
        }
    }

    TextButton(onClick = alVolver, modifier = Modifier.padding(top = 8.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Volver a entrar")
    }
    Titulo("Recupera tu acceso", "Te enviaremos un enlace a tu correo para que hagas brotar una contraseña nueva.")
    CampoCorreo(correo, alCambiarCorreo) { enviar() }
    Mensaje(error)
    BotonPrincipal("Enviar enlace", cargando) { enviar() }
    NotaPie("Si entraste con Google, tu contraseña es la de tu cuenta de Google: recupérala desde Google.")
}
