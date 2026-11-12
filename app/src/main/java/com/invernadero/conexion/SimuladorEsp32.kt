package com.invernadero.conexion

import com.invernadero.datos.Rol
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** Imita la lógica de tu sketch para probar la app sin hardware. */
class SimuladorEsp32(private val sensores: Int) : FuenteEsp32 {
    override val nombre = "Simulador ($sensores sensores)"

    private val mutex = Mutex()
    private val hum = MutableList(sensores) { 30f + Random.nextFloat() * 45f }
    private var temp = 26f
    private var pwm = 0
    private var apertura = 0f
    private var fanManual = false
    private var pwmManual = 0
    private var ventanaManual = false
    private var aperturaManual = 0f

    override fun lecturas(): Flow<EventoFuente> = flow {
        while (true) {
            emit(EventoFuente.Dato(mutex.withLock { paso() }))
            delay(2000)
        }
    }

    private fun paso(): LecturaEsp32 {
        pwm = if (fanManual) pwmManual
              else if (temp > 25f) (200 + ((temp - 25f) / 10f * 55f).toInt()).coerceIn(200, 255) else 0
        apertura = if (ventanaManual) aperturaManual
                   else when { temp > 27f -> 90f; temp < 20f -> 0f; else -> (temp - 20f) / 7f * 90f }
        val objetivo = 31f - pwm / 255f * 7f - apertura / 90f * 2f
        temp += (objetivo - temp) * 0.08f + (Random.nextFloat() - 0.5f) * 0.3f
        for (i in hum.indices) {
            hum[i] = (hum[i] - 0.2f - pwm / 255f * 0.15f + (Random.nextFloat() - 0.5f) * 0.6f).coerceIn(3f, 95f)
            if (hum[i] < 12f) hum[i] = 80f    // "riego"
        }
        return LecturaEsp32(
            humedades = hum.toList(),
            temperatura = temp,
            ventiladorEncendido = pwm > 0,
            anguloVentana = apertura,
            velocidadVentilador = pwm / 255f,
            humedadAmbiente = 55f + (Random.nextFloat() - 0.5f) * 4f,
            estadosSuelo = hum.map { estadoSuelo(it) },
            modoVentilador = if (fanManual) "MANUAL" else "AUTO",
            modoVentana = if (ventanaManual) "MANUAL" else "AUTO"
        )
    }

    override suspend fun enviar(codigo: String, usuario: String, rol: Rol): Boolean = mutex.withLock {
        val n = codigo.drop(1).toIntOrNull() ?: 0
        if (!fanManual) pwmManual = pwm
        when (codigo[0]) {
            'A' -> { ventanaManual = true; aperturaManual = 90f }
            'C' -> { ventanaManual = true; aperturaManual = 0f }
            'W' -> { ventanaManual = true; aperturaManual = n.toFloat() }
            'M' -> { fanManual = true; pwmManual = 255 }
            '0' -> { fanManual = true; pwmManual = 0 }
            '+' -> { fanManual = true; pwmManual = (pwmManual + 25).coerceAtMost(255) }
            '-' -> { fanManual = true; pwmManual = (pwmManual - 25).coerceAtLeast(0) }
            'V' -> { fanManual = true; pwmManual = n }
            'X' -> { fanManual = false; ventanaManual = false }
            else -> return@withLock false
        }
        true
    }
}

fun estadoSuelo(pct: Float) = when {
    pct > 85f -> "Sobre regada"
    pct >= 55f -> "Regada (Óptima)"
    pct >= 20f -> "Consumiendo agua"
    pct >= 5f -> "Falta de agua"
    else -> "Totalmente seca"
}
