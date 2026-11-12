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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.invernadero.datos.InvitacionesRepo
import com.invernadero.datos.ResultadoInvitado
import com.invernadero.datos.ResultadoLogin
import com.invernadero.datos.ResultadoRecuperacion
import com.invernadero.datos.Usuario
import com.invernadero.datos.UsuariosRepo
import com.invernadero.datos.normalizarNombre
import com.invernadero.seguridad.Contrasenas
import com.invernadero.ui.CampoClave
import com.invernadero.ui.copiarSensible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────
//  ACCESO a GreenBox: entrar, crear cuenta, pase de visita y
//  recuperar la contraseña con la semilla de recuperación.
// ─────────────────────────────────────────────────────────────

private enum class Vista(val pestana: String) {
    ENTRAR("Entrar"), CREAR("Crear cuenta"), INVITADO("Invitado"), RECUPERAR("")
}

private val Pestanas = listOf(Vista.ENTRAR, Vista.CREAR, Vista.INVITADO)

/** Una semilla recién generada: se muestra una sola vez antes de seguir. */
private data class SemillaPendiente(
    val semilla: String,
    val nombre: String,
    val idVisible: String?,
    val usuario: Usuario?,      // si no es null, al continuar se inicia su sesión
    val recuperada: Boolean,
    val recordar: Boolean = false
)

private val SalidaSuave = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Composable
fun PantallaAcceso(c: Contenedor) {
    var hayUsuarios by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        // "Recordarme": si hay una sesión recordada válida se entra directo
        val recordado = withContext(Dispatchers.IO) { c.recordar.restaurar() }
        if (recordado != null) {
            withContext(Dispatchers.IO) { c.db.auditar(recordado.nombre, "LOGIN", "Inicio recordado en este teléfono", "OK") }
            c.sesion.iniciar(recordado, recordar = true)
            return@LaunchedEffect
        }
        hayUsuarios = withContext(Dispatchers.IO) { c.usuarios.hayUsuarios() }
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
                        when (hayUsuarios) {
                            null -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                            else -> Acceso(c, primeraVez = hayUsuarios == false) { hayUsuarios = true }
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
private fun Acceso(c: Contenedor, primeraVez: Boolean, alCrearCuenta: () -> Unit) {
    var vista by rememberSaveable { mutableStateOf(Vista.ENTRAR) }
    var nombre by rememberSaveable { mutableStateOf("") }
    var avisoEntrar by remember { mutableStateOf<String?>(null) }
    var semilla by remember { mutableStateOf<SemillaPendiente?>(null) }
    val scope = rememberCoroutineScope()

    val pendiente = semilla
    if (pendiente != null) {
        VistaSemilla(pendiente) {
            semilla = null
            val u = pendiente.usuario
            if (u != null) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (pendiente.recordar) c.recordar.guardar(u)
                        c.db.auditar(u.nombre, "LOGIN", "Primer ingreso tras crear la cuenta", "OK")
                    }
                    c.sesion.iniciar(u, pendiente.recordar)
                }
            } else {
                nombre = pendiente.nombre
                avisoEntrar = "Contraseña renovada. Ya puedes entrar con la nueva."
                vista = Vista.ENTRAR
            }
        }
        return
    }

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
                Vista.ENTRAR -> FormEntrar(c, nombre, { nombre = it }, avisoEntrar, primeraVez,
                    alIrACrear = { vista = Vista.CREAR }) { vista = Vista.RECUPERAR }
                Vista.CREAR -> FormCrear(c, primeraVez) { alCrearCuenta(); semilla = it }
                Vista.INVITADO -> FormInvitado(c)
                Vista.RECUPERAR -> FormRecuperar(c, nombre, { nombre = it }, alVolver = { vista = Vista.ENTRAR }) { semilla = it }
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

// ───────────────────────── Entrar ─────────────────────────

@Composable
private fun FormEntrar(
    c: Contenedor,
    usuario: String,
    alCambiarUsuario: (String) -> Unit,
    aviso: String?,
    primeraVez: Boolean,
    alIrACrear: () -> Unit,
    alOlvidar: () -> Unit
) {
    var clave by remember { mutableStateOf("") }          // la contraseña no se guarda en el estado persistente
    var recordar by rememberSaveable { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun ingresar() {
        if (cargando) return
        if (usuario.isBlank() || clave.isEmpty()) { error = "Escribe tu nombre y tu contraseña"; return }
        cargando = true
        error = null
        val nombre = normalizarNombre(usuario)
        val recordarAhora = recordar
        val arreglo = clave.toCharArray()
        scope.launch {
            val r = withContext(Dispatchers.Default) {
                c.usuarios.login(nombre, arreglo).also { if (it is ResultadoLogin.Correcto && recordarAhora) c.recordar.guardar(it.usuario) }
            }
            arreglo.fill('\u0000')
            withContext(Dispatchers.IO) {
                c.db.auditar(nombre, "LOGIN", if (recordarAhora) "Inicio de sesión (recordado en este teléfono)" else "Inicio de sesión", when (r) {
                    is ResultadoLogin.Correcto -> "OK"
                    is ResultadoLogin.Bloqueado -> "BLOQUEADO"
                    ResultadoLogin.Incorrecto -> "FALLIDO"
                })
            }
            cargando = false
            when (r) {
                is ResultadoLogin.Correcto -> { clave = ""; c.sesion.iniciar(r.usuario, recordarAhora) }
                is ResultadoLogin.Bloqueado -> error = "Demasiados intentos. La cuenta descansa ${minutos(r.segundos)} min antes de volver a abrirse."
                ResultadoLogin.Incorrecto -> error = "Nombre o contraseña incorrectos"
            }
        }
    }

    if (primeraVez) {
        Titulo("Un invernadero recién plantado", "Aún no hay cuentas aquí. Crea la tuya para empezar a ver y cuidar tus plantas.")
        BotonPrincipal("Crear la primera cuenta", cargando = false) { alIrACrear() }
        return
    }
    Titulo("Qué bueno verte de nuevo", "Tus plantas siguieron creciendo mientras no estabas.")
    Mensaje(aviso, error = false)
    CampoNombre(usuario, "Nombre", { alCambiarUsuario(it.take(30)) })
    CampoClave(clave, "Contraseña", { clave = it.take(64) }) { ingresar() }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextButton(onClick = alOlvidar) { Text("¿Olvidaste tu contraseña?") }
    }
    Recordarme(recordar) { recordar = it }
    Mensaje(error)
    BotonPrincipal("Entrar al invernadero", cargando) { ingresar() }
    NotaPie("Tu contraseña se guarda cifrada en este teléfono. Tras ${UsuariosRepo.MAX_INTENTOS} intentos fallidos la cuenta descansa 5 minutos, y sin «Recordarme» la sesión se cierra sola tras 10 minutos quieta.")
}

// ───────────────────────── Crear cuenta ─────────────────────────

@Composable
private fun FormCrear(c: Contenedor, primeraVez: Boolean, alCrear: (SemillaPendiente) -> Unit) {
    var usuario by rememberSaveable { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var recordar by rememberSaveable { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun crear() {
        if (cargando) return
        val nombre = normalizarNombre(usuario)
        error = Contrasenas.validarPolitica(nombre, clave) ?: if (clave != confirmar) "Las contraseñas no coinciden" else null
        if (error != null) return
        cargando = true
        val arreglo = clave.toCharArray()
        val recordarAhora = recordar
        scope.launch {
            val resultado = withContext(Dispatchers.Default) {
                c.usuarios.registrarDesdeAcceso(nombre, arreglo).map { rol ->
                    val u = c.usuarios.buscar(nombre)
                    rol to SemillaPendiente(
                        semilla = c.usuarios.nuevaSemilla(nombre),
                        nombre = nombre,
                        idVisible = u?.idVisible,
                        usuario = u,
                        recuperada = false,
                        recordar = recordarAhora
                    )
                }
            }
            arreglo.fill('\u0000')
            cargando = false
            val creado = resultado.getOrNull()
            if (creado == null) {
                error = resultado.exceptionOrNull()?.message ?: "No se pudo crear la cuenta"
                return@launch
            }
            val (rol, pendiente) = creado
            withContext(Dispatchers.IO) {
                c.db.auditar(nombre, "USUARIO", "Cuenta ${pendiente.idVisible} creada desde el acceso (${rol.etiqueta})", "OK")
            }
            clave = ""; confirmar = ""
            alCrear(pendiente)
        }
    }

    if (primeraVez) {
        Titulo("Planta la primera semilla", "Este invernadero aún no tiene quien lo cuide. Crea tu cuenta para ver tus plantas y regarlas o ventilarlas.")
    } else {
        Titulo("Siembra tu cuenta", "Un brote nuevo en este invernadero: podrás ver cómo crecen tus plantas y regarlas o ventilarlas desde aquí.")
    }
    CampoNombre(usuario, "Nombre", { usuario = it.take(30) }, ayuda = "Con este nombre entrarás. De 3 a 30 caracteres; puede llevar espacios")
    CampoClave(clave, "Contraseña", { clave = it.take(64) }, ayuda = "Mínimo 8 caracteres, con letras y números")
    CampoClave(confirmar, "Repite la contraseña", { confirmar = it.take(64) }) { crear() }
    Recordarme(recordar) { recordar = it }
    Mensaje(error)
    BotonPrincipal("Crear mi cuenta", cargando) { crear() }
    NotaPie("Al crearla recibirás tu ID de cuenta y una semilla de recuperación para cuando olvides tu contraseña.")
}

// ───────────────────────── Pase de visita ─────────────────────────

/** Muestra la clave como XXXX-XXXX mientras se escribe. */
private fun formatearClaveVisita(texto: String): String {
    val n = InvitacionesRepo.normalizar(texto).take(8)
    return if (n.length > 4) n.take(4) + "-" + n.drop(4) else n
}

@Composable
private fun FormInvitado(c: Contenedor) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var codigo by remember { mutableStateOf(TextFieldValue("")) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun entrar() {
        if (cargando) return
        val visitante = nombre.trim().replace(Regex("\\s+"), " ")
        error = InvitacionesRepo.validarVisitante(visitante)
            ?: if (InvitacionesRepo.normalizar(codigo.text).length != 8) "La clave temporal tiene 8 caracteres, como 7KQ4-M2PX" else null
        if (error != null) return
        cargando = true
        val clave = codigo.text
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                c.invitaciones.entrar(visitante, clave).also { r ->
                    c.db.auditar("$visitante (invitado)", "INVITADO", when (r) {
                        is ResultadoInvitado.Correcto -> "Entrada con pase de ${r.usuario.anfitrion}"
                        else -> "Intento de entrada con pase"
                    }, when (r) {
                        is ResultadoInvitado.Correcto -> "OK"
                        is ResultadoInvitado.Bloqueado -> "BLOQUEADO"
                        ResultadoInvitado.Incorrecto -> "FALLIDO"
                    })
                }
            }
            cargando = false
            when (r) {
                is ResultadoInvitado.Correcto -> { codigo = TextFieldValue(""); c.sesion.iniciar(r.usuario) }
                is ResultadoInvitado.Bloqueado -> error = "Demasiadas claves incorrectas. La puerta de visitas se abre de nuevo en ${minutos(r.segundos)} min."
                ResultadoInvitado.Incorrecto -> error = "Esa clave no abre la puerta: puede que esté mal escrita, haya vencido o la hayan revocado."
            }
        }
    }

    Titulo("Pase de visita", "Alguien te abrió la puerta de su invernadero. Escribe la clave temporal que te compartió.")
    CampoNombre(nombre, "Tu nombre", { nombre = it.take(24) }, ayuda = "Así sabrá tu anfitrión quién pasó a regar")
    OutlinedTextField(
        value = codigo,
        onValueChange = { nuevo -> val f = formatearClaveVisita(nuevo.text); codigo = TextFieldValue(f, TextRange(f.length)) },
        label = { Text("Clave temporal") },
        placeholder = { Text("XXXX-XXXX") },
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
    NotaPie("El pase dura lo que tu anfitrión decidió, de 1 hora a 3 días. Al vencer, la visita termina sola. No podrás cambiar ajustes ni cuentas.")
}

// ───────────────────────── Olvidé mi contraseña ─────────────────────────

@Composable
private fun FormRecuperar(
    c: Contenedor,
    usuario: String,
    alCambiarUsuario: (String) -> Unit,
    alVolver: () -> Unit,
    alRecuperar: (SemillaPendiente) -> Unit
) {
    var semilla by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun restablecer() {
        if (cargando) return
        val nombre = usuario.trim()
        error = when {
            nombre.isEmpty() -> "Escribe tu nombre o tu ID de cuenta"
            UsuariosRepo.normalizarSemilla(semilla).length < 12 -> "Escribe la semilla completa, como QUILLAY-7K4P-QR2D-9XW3"
            clave != confirmar -> "Las contraseñas nuevas no coinciden"
            else -> null
        }
        if (error != null) return
        cargando = true
        val arreglo = clave.toCharArray()
        val texto = semilla
        scope.launch {
            val r = withContext(Dispatchers.Default) { c.usuarios.restablecerConSemilla(nombre, texto, arreglo) }
            arreglo.fill('\u0000')
            withContext(Dispatchers.IO) {
                c.db.auditar(nombre, "RECUPERAR", "Restablecer contraseña con semilla", when (r) {
                    is ResultadoRecuperacion.Correcto -> "OK"
                    is ResultadoRecuperacion.Bloqueado -> "BLOQUEADO"
                    else -> "FALLIDO"
                })
            }
            cargando = false
            when (r) {
                is ResultadoRecuperacion.Correcto -> {
                    semilla = ""; clave = ""; confirmar = ""
                    val id = withContext(Dispatchers.IO) { c.usuarios.buscar(r.nombre)?.idVisible }
                    alRecuperar(SemillaPendiente(r.nuevaSemilla, r.nombre, id, usuario = null, recuperada = true))
                }
                is ResultadoRecuperacion.Bloqueado -> error = "Demasiados intentos. La cuenta descansa ${minutos(r.segundos)} min antes de volver a abrirse."
                is ResultadoRecuperacion.Invalida -> error = r.mensaje
                ResultadoRecuperacion.Incorrecto -> error = "El nombre o ID de cuenta y la semilla no coinciden"
            }
        }
    }

    TextButton(onClick = alVolver, modifier = Modifier.padding(top = 8.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Volver a entrar")
    }
    Titulo("Recupera tu acceso", "Con la semilla de recuperación que guardaste al crear tu cuenta, haces brotar una contraseña nueva.")
    CampoNombre(usuario, "Nombre o ID de cuenta", { alCambiarUsuario(it.take(30)) }, ayuda = "Por ejemplo: María José o GB-0001")
    OutlinedTextField(
        value = semilla,
        onValueChange = { semilla = it.uppercase().take(40) },
        label = { Text("Semilla de recuperación") },
        placeholder = { Text("QUILLAY-XXXX-XXXX-XXXX") },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Next,
            autoCorrectEnabled = false
        )
    )
    CampoClave(clave, "Contraseña nueva", { clave = it.take(64) }, ayuda = "Mínimo 8 caracteres, con letras y números")
    CampoClave(confirmar, "Repite la contraseña nueva", { confirmar = it.take(64) }) { restablecer() }
    Mensaje(error)
    BotonPrincipal("Restablecer contraseña", cargando) { restablecer() }
    NotaPie("Sin la semilla no es posible recuperar la contraseña: guárdala siempre junto a tu ID de cuenta.")
}

// ───────────────────────── Mostrar la semilla (una sola vez) ─────────────────────────

@Composable
private fun VistaSemilla(p: SemillaPendiente, alContinuar: () -> Unit) {
    val contexto = LocalContext.current
    var guardada by remember { mutableStateOf(false) }
    var copiada by remember { mutableStateOf(false) }

    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (p.recuperada) {
            Titulo("Contraseña renovada", "Tu semilla anterior ya germinó y dejó de servir. Esta es la nueva: guárdala igual de bien.")
        } else {
            Titulo("Guarda tu semilla", "Si algún día olvidas tu contraseña, esta semilla hará brotar una nueva. Anótala en papel o en un gestor de contraseñas: solo la verás esta vez.")
        }
        p.idVisible?.let { id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tu ID de cuenta", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(id, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.primary)
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SelectionContainer {
                Text(
                    p.semilla,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)
                )
            }
        }
        OutlinedButton(onClick = {
            copiarSensible(contexto, "Semilla GreenBox", listOfNotNull(p.idVisible, p.semilla).joinToString("  "))
            copiada = true
        }) {
            Text(if (copiada) "Copiada" else if (p.idVisible != null) "Copiar ID y semilla" else "Copiar semilla")
        }
        Text("Para recuperar la cuenta necesitarás tu nombre o tu ID de cuenta, y esta semilla.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = guardada, role = Role.Checkbox, onValueChange = { guardada = it }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = guardada, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Text("La guardé en un lugar seguro", style = MaterialTheme.typography.bodyLarge)
        }
        BotonPrincipal(if (p.usuario != null) "Entrar al invernadero" else "Volver a entrar", cargando = false, habilitado = guardada) { alContinuar() }
    }
}
