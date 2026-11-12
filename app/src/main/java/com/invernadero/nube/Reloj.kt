package com.invernadero.nube

/**
 * Hora del servidor de Firebase. Todo lo que se compara con horas guardadas en la nube (vencimiento de
 * los pases, "hace X min" del ESP32) usa esta hora y no la del teléfono, porque un teléfono o PC con la
 * hora mal puesta crearía pases ya vencidos o mostraría datos "del futuro".
 * El desfase lo entrega Firebase (.info/serverTimeOffset) apenas hay conexión; mientras tanto es 0.
 */
object Reloj {
    @Volatile var desfaseMs: Long = 0L

    fun ahora(): Long = System.currentTimeMillis() + desfaseMs
}
