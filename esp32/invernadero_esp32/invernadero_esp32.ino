/*
 * Invernadero - ESP32 (basado en tu invernadero.ino)
 *
 * Misma lógica que tu sketch de Arduino:
 *   - Ciclo automático cada 30 s (ventilador por temperatura + "respiración" cada 5 min, ventana por temperatura)
 *   - Ventana: 120° = cerrada, 0° = abierta, movimiento suave
 *   - Comandos: A/C ventana, + - M 0 ventilador, X automático   (+ V<0-255> y W<0-90> para los sliders de la app)
 *   - Reglas del modo automático, ajustables desde la app (pantalla Controles) y guardadas en la memoria
 *     del ESP32 (sobreviven a un reinicio):
 *       T<15-40>  el ventilador se enciende sobre T °C (a toda potencia en T+10 °C)
 *       L<5-40>   bajo L °C la ventana queda cerrada
 *       H<10-45>  sobre H °C la ventana queda abierta del todo (entre L y H abre en proporción; H >= L+2)
 *       U<40-100> si la humedad del aire supera U % se ventila aunque no haga calor (U100 = desactivado)
 *
 * Comunicación con la app:
 *   WiFi:      GET /datos                     -> JSON + cabecera X-Firma = HMAC-SHA256(SECRETO, json)
 *              GET /comando?c=<cmd>&f=<firma> -> firma = HMAC-SHA256(SECRETO, cmd + nonce)
 *   Bluetooth: el ESP32 envía  "D|<json>|<firma>"  cada 2 s
 *              la app envía    "C|<cmd>|<firma>"
 *   Serial USB: comandos de 1 letra SIN firma (puerto de mantenimiento local, requiere acceso físico)
 *
 * NUBE (modelo híbrido con Firebase Realtime Database):
 *   - El tiempo real sigue siendo local (arriba). A la nube se sube menos y más lento:
 *       estado  cada 2 min  (PUT  invernaderos/<id>/estado)      → lo último, para mirar desde lejos
 *       resumen cada 5 min  (POST invernaderos/<id>/resumenes)   → promedio, mínimo y máximo
 *       avisos  al ocurrir  (POST invernaderos/<id>/alertas)     → calor, frío, tierra seca, sensor caído, reinicio
 *   - Corre en una tarea aparte (núcleo 0): el control del invernadero nunca espera a Internet.
 *   - HTTPS verificado con los certificados raíz de Google; el ESP32 entra con SU PROPIA cuenta de Firebase
 *     (la crea la app: Ajustes > Nube > Vincular ESP32) y las reglas solo le dejan escribir esas 3 cosas.
 *   - Sin Internet los resúmenes esperan en una cola (hasta 2 h) y se suben al volver.
 *   - Por la nube NO llegan órdenes: los comandos siguen siendo solo locales y firmados.
 *
 * Seguridad:
 *   - La clave SECRETO nunca viaja por la red; solo viajan firmas.
 *   - El nonce cambia después de cada comando: un comando capturado no se puede repetir.
 *   - 5 firmas inválidas seguidas -> se bloquean los comandos remotos 60 s.
 *   - Lista blanca de comandos y validación de rangos.
 *
 * Librerías: "DHT sensor library" (Adafruit), "Adafruit Unified Sensor", "ESP32Servo"
 *            (WiFiClientSecure y HTTPClient vienen con el core ESP32 de Espressif, versión 3.x)
 * Placa: ESP32 Dev Module (BluetoothSerial NO existe en ESP32-S3 / C3)
 */

#include <WiFi.h>
#include <WebServer.h>
#include <BluetoothSerial.h>
#include <ESP32Servo.h>
#include "DHT.h"
#include "mbedtls/md.h"
#include "esp_random.h"
#include <Preferences.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <time.h>

// ───────── CONFIGURA ESTO ─────────
const char* WIFI_SSID     = "TU_WIFI";
const char* WIFI_PASSWORD = "TU_CLAVE";
const char* SECRETO       = "cambia-esta-clave-larga-2025";   // igual que "Clave compartida" en la app (mín. 12)
const char* NOMBRE_BT     = "Invernadero";
// Bluetooth clásico + WiFi + HTTPS usan bastante memoria. Si en el Monitor Serie ves que la nube falla
// por memoria ("heap libre" bajo), pon false aquí: el teléfono seguirá conectando por WiFi.
const bool  USAR_BLUETOOTH = true;

// ───────── NUBE (Firebase) ─────────
// Estas 5 líneas las genera la app: Ajustes > Nube > Vincular ESP32 > Copiar. Pégalas aquí.
const bool  NUBE_ACTIVA      = true;
const char* FIREBASE_API_KEY = "PEGA_AQUI";
const char* FIREBASE_URL     = "https://PEGA_AQUI.firebaseio.com";
const char* INVERNADERO_ID   = "PEGA_AQUI";
const char* ESP32_CORREO     = "PEGA_AQUI";
const char* ESP32_CLAVE      = "PEGA_AQUI";
const unsigned long CADA_ESTADO_MS  = 2UL * 60 * 1000;   // estado: cada 2 min
const unsigned long CADA_RESUMEN_MS = 5UL * 60 * 1000;   // resumen: cada 5 min

// IP fija: debe coincidir con la app (Ajustes) y con res/xml/network_security_config.xml
const bool USAR_IP_FIJA = true;
IPAddress IP_FIJA(192, 168, 1, 200);
IPAddress PUERTA(192, 168, 1, 1);
IPAddress MASCARA(255, 255, 255, 0);
IPAddress DNS1(8, 8, 8, 8);

// --- Definición de Pines (equivalentes ESP32 de tu sketch) ---
#define SOIL_A_PIN 34   // era A0  (solo ADC1: pines 32-39 funcionan con WiFi)
#define SOIL_D_PIN 35   // era 2
#define SERVO_PIN  27   // era A1
#define DHT_PIN    4
#define FAN_PIN    26   // era 5 (el GPIO 5 da pulsos al arrancar)

// --- Sensor de suelo: ESP32 lee 0-4095 (Arduino 0-1023), umbrales x4 ---
const int SUELO_SOBRE_REGADA = 1600;
const int SUELO_OPTIMA       = 2400;
const int SUELO_CONSUMIENDO  = 3600;
const int SUELO_FALTA_AGUA   = 4092;
const int CRUDO_MOJADO = 1200;   // calibra: valor dentro de agua
const int CRUDO_SECO   = 4095;   // calibra: valor al aire

#define DHTTYPE DHT22
DHT dht(DHT_PIN, DHTTYPE);

// --- Servo ---
const int ANGULO_CERRADA = 120;
const int ANGULO_ABIERTA = 0;
Servo motorVentana;
int anguloActual   = ANGULO_CERRADA;
int anguloObjetivo = ANGULO_CERRADA;
unsigned long ultimoPasoServo = 0;

// --- Ventilador ---
int contadorCiclos = 0;
const int CICLOS_PARA_RESPIRAR = 10;   // 10 x 30 s = 5 min
bool ventilandoPorTiempo = false;
bool fanEstabaApagado = true;
int  pwmFanActual = 0;

// --- Reglas del modo automático (las ajusta la app; se guardan en NVS) ---
Preferences preferencias;
int pVentiladorSobre = 25;   // °C
int pVentanaDesde    = 20;   // °C
int pVentanaHasta    = 27;   // °C
int pHumedadMaxima   = 85;   // %  (100 = no ventilar por humedad)

void cargarReglas() {
  preferencias.begin("reglas", true);
  pVentiladorSobre = preferencias.getInt("ventSobre", 25);
  pVentanaDesde    = preferencias.getInt("ventDesde", 20);
  pVentanaHasta    = preferencias.getInt("ventHasta", 27);
  pHumedadMaxima   = preferencias.getInt("humMax", 85);
  preferencias.end();
  if (pVentanaHasta < pVentanaDesde + 2) { pVentanaDesde = 20; pVentanaHasta = 27; }
}

void guardarRegla(const char* clave, int valor) {
  preferencias.begin("reglas", false);
  preferencias.putInt(clave, valor);
  preferencias.end();
}

// --- Manual ---
bool modoManualServo = false;
bool modoManualFan = false;
int  velocidadManualFan = 0;

// --- Tiempos ---
unsigned long tiempoAnterior = 0;
const long intervalo = 30000;
unsigned long ultimaLectura = 0;
const long intervaloLectura = 2000;

// --- Lecturas ---
float temperatura = NAN;
float humedadAmbiente = NAN;
int   humedadSueloAnalog = 0;

// --- Seguridad ---
String nonce;
int fallosFirma = 0;
unsigned long bloqueadoHasta = 0;

// --- Nube ---
// Certificados raíz de Google Trust Services (válidos hasta 2036): el ESP32 solo habla con servidores
// de Google verificados, nunca con un impostor en la red.
const char* CA_GOOGLE =
  "-----BEGIN CERTIFICATE-----\n"
  "MIIFVzCCAz+gAwIBAgINAgPlk28xsBNJiGuiFzANBgkqhkiG9w0BAQwFADBHMQsw\n"
  "CQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzEU\n"
  "MBIGA1UEAxMLR1RTIFJvb3QgUjEwHhcNMTYwNjIyMDAwMDAwWhcNMzYwNjIyMDAw\n"
  "MDAwWjBHMQswCQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZp\n"
  "Y2VzIExMQzEUMBIGA1UEAxMLR1RTIFJvb3QgUjEwggIiMA0GCSqGSIb3DQEBAQUA\n"
  "A4ICDwAwggIKAoICAQC2EQKLHuOhd5s73L+UPreVp0A8of2C+X0yBoJx9vaMf/vo\n"
  "27xqLpeXo4xL+Sv2sfnOhB2x+cWX3u+58qPpvBKJXqeqUqv4IyfLpLGcY9vXmX7w\n"
  "Cl7raKb0xlpHDU0QM+NOsROjyBhsS+z8CZDfnWQpJSMHobTSPS5g4M/SCYe7zUjw\n"
  "TcLCeoiKu7rPWRnWr4+wB7CeMfGCwcDfLqZtbBkOtdh+JhpFAz2weaSUKK0Pfybl\n"
  "qAj+lug8aJRT7oM6iCsVlgmy4HqMLnXWnOunVmSPlk9orj2XwoSPwLxAwAtcvfaH\n"
  "szVsrBhQf4TgTM2S0yDpM7xSma8ytSmzJSq0SPly4cpk9+aCEI3oncKKiPo4Zor8\n"
  "Y/kB+Xj9e1x3+naH+uzfsQ55lVe0vSbv1gHR6xYKu44LtcXFilWr06zqkUspzBmk\n"
  "MiVOKvFlRNACzqrOSbTqn3yDsEB750Orp2yjj32JgfpMpf/VjsPOS+C12LOORc92\n"
  "wO1AK/1TD7Cn1TsNsYqiA94xrcx36m97PtbfkSIS5r762DL8EGMUUXLeXdYWk70p\n"
  "aDPvOmbsB4om3xPXV2V4J95eSRQAogB/mqghtqmxlbCluQ0WEdrHbEg8QOB+DVrN\n"
  "VjzRlwW5y0vtOUucxD/SVRNuJLDWcfr0wbrM7Rv1/oFB2ACYPTrIrnqYNxgFlQID\n"
  "AQABo0IwQDAOBgNVHQ8BAf8EBAMCAYYwDwYDVR0TAQH/BAUwAwEB/zAdBgNVHQ4E\n"
  "FgQU5K8rJnEaK0gnhS9SZizv8IkTcT4wDQYJKoZIhvcNAQEMBQADggIBAJ+qQibb\n"
  "C5u+/x6Wki4+omVKapi6Ist9wTrYggoGxval3sBOh2Z5ofmmWJyq+bXmYOfg6LEe\n"
  "QkEzCzc9zolwFcq1JKjPa7XSQCGYzyI0zzvFIoTgxQ6KfF2I5DUkzps+GlQebtuy\n"
  "h6f88/qBVRRiClmpIgUxPoLW7ttXNLwzldMXG+gnoot7TiYaelpkttGsN/H9oPM4\n"
  "7HLwEXWdyzRSjeZ2axfG34arJ45JK3VmgRAhpuo+9K4l/3wV3s6MJT/KYnAK9y8J\n"
  "ZgfIPxz88NtFMN9iiMG1D53Dn0reWVlHxYciNuaCp+0KueIHoI17eko8cdLiA6Ef\n"
  "MgfdG+RCzgwARWGAtQsgWSl4vflVy2PFPEz0tv/bal8xa5meLMFrUKTX5hgUvYU/\n"
  "Z6tGn6D/Qqc6f1zLXbBwHSs09dR2CQzreExZBfMzQsNhFRAbd03OIozUhfJFfbdT\n"
  "6u9AWpQKXCBfTkBdYiJ23//OYb2MI3jSNwLgjt7RETeJ9r/tSQdirpLsQBqvFAnZ\n"
  "0E6yove+7u7Y/9waLd64NnHi/Hm3lCXRSHNboTXns5lndcEZOitHTtNCjv0xyBZm\n"
  "2tIMPNuzjsmhDYAPexZ3FL//2wmUspO8IFgV6dtxQ/PeEMMA3KgqlbbC1j+Qa3bb\n"
  "bP6MvPJwNQzcmRk13NfIRmPVNnGuV/u3gm3c\n"
  "-----END CERTIFICATE-----\n"
  "-----BEGIN CERTIFICATE-----\n"
  "MIICCTCCAY6gAwIBAgINAgPlwGjvYxqccpBQUjAKBggqhkjOPQQDAzBHMQswCQYD\n"
  "VQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzEUMBIG\n"
  "A1UEAxMLR1RTIFJvb3QgUjQwHhcNMTYwNjIyMDAwMDAwWhcNMzYwNjIyMDAwMDAw\n"
  "WjBHMQswCQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2Vz\n"
  "IExMQzEUMBIGA1UEAxMLR1RTIFJvb3QgUjQwdjAQBgcqhkjOPQIBBgUrgQQAIgNi\n"
  "AATzdHOnaItgrkO4NcWBMHtLSZ37wWHO5t5GvWvVYRg1rkDdc/eJkTBa6zzuhXyi\n"
  "QHY7qca4R9gq55KRanPpsXI5nymfopjTX15YhmUPoYRlBtHci8nHc8iMai/lxKvR\n"
  "HYqjQjBAMA4GA1UdDwEB/wQEAwIBhjAPBgNVHRMBAf8EBTADAQH/MB0GA1UdDgQW\n"
  "BBSATNbrdP9JNqPV2Py1PsVq8JQdjDAKBggqhkjOPQQDAwNpADBmAjEA6ED/g94D\n"
  "9J+uHXqnLrmvT/aDHQ4thQEd0dlq7A/Cr8deVl5c1RxYIigL9zC2L7F8AjEA8GE8\n"
  "p/SgguMh1YQdc4acLa/KNJvxn7kjNuK8YAOdgLOaVsjh4rsUecrNIdSUtUlD\n"
  "-----END CERTIFICATE-----\n";

struct Acumulado {            // lo que se junta entre un resumen y el siguiente
  uint32_t n = 0;
  float tSum = 0, tMin = 1000, tMax = -1000; uint32_t tN = 0;
  float haSum = 0; uint32_t haN = 0;
  float hsSum = 0, pwmSum = 0, ventanaSum = 0;
  uint32_t ventiladorMs = 0;
};
Acumulado acumulado;
SemaphoreHandle_t candado = NULL;   // protege "acumulado" (lo usan el loop y la tarea de la nube)
int fallosDht = 0;

BluetoothSerial SerialBT;
WebServer server(80);
String bufferBT;

// ───────── Utilidades ─────────
void mensaje(const String& s) {
  Serial.println(s);
}

void setFan(int pwm) {
  pwmFanActual = constrain(pwm, 0, 255);
  analogWrite(FAN_PIN, pwmFanActual);
}

void moverVentana(int destino) {
  anguloObjetivo = constrain(destino, ANGULO_ABIERTA, ANGULO_CERRADA);
}

void actualizarServo() {     // 1° cada 15 ms sin bloquear
  if (anguloActual != anguloObjetivo && millis() - ultimoPasoServo >= 15) {
    ultimoPasoServo = millis();
    anguloActual += (anguloObjetivo > anguloActual) ? 1 : -1;
    motorVentana.write(anguloActual);
  }
}

String estadoSuelo(int v) {
  if (v < SUELO_SOBRE_REGADA) return "Sobre regada";
  if (v <= SUELO_OPTIMA)      return "Regada (Óptima)";
  if (v <= SUELO_CONSUMIENDO) return "Consumiendo agua";
  if (v <= SUELO_FALTA_AGUA)  return "Falta de agua";
  return "Totalmente seca";
}

float sueloPorcentaje(int v) {
  float pct = (CRUDO_SECO - v) * 100.0 / (CRUDO_SECO - CRUDO_MOJADO);
  return constrain(pct, 0, 100);
}

// ───────── Criptografía ─────────
String hmacHex(const String& msg) {
  byte out[32];
  mbedtls_md_context_t ctx;
  mbedtls_md_init(&ctx);
  mbedtls_md_setup(&ctx, mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), 1);
  mbedtls_md_hmac_starts(&ctx, (const unsigned char*)SECRETO, strlen(SECRETO));
  mbedtls_md_hmac_update(&ctx, (const unsigned char*)msg.c_str(), msg.length());
  mbedtls_md_hmac_finish(&ctx, out);
  mbedtls_md_free(&ctx);
  char hex[65];
  for (int i = 0; i < 32; i++) sprintf(hex + 2 * i, "%02x", out[i]);
  hex[64] = 0;
  return String(hex);
}

bool igualesSeguro(const String& a, const String& b) {   // tiempo constante
  if (a.length() != b.length()) return false;
  uint8_t r = 0;
  for (unsigned int i = 0; i < a.length(); i++) r |= a[i] ^ b[i];
  return r == 0;
}

void nuevoNonce() {
  char b[17];
  snprintf(b, sizeof(b), "%08lx%08lx", (unsigned long)esp_random(), (unsigned long)esp_random());
  nonce = String(b);
}

/** Verifica firma del comando. Devuelve "" si está OK o el motivo del rechazo. */
String verificarComando(const String& cmd, const String& firma) {
  if (millis() < bloqueadoHasta) return "bloqueado";
  if (!igualesSeguro(firma, hmacHex(cmd + nonce))) {
    fallosFirma++;
    if (fallosFirma >= 5) { bloqueadoHasta = millis() + 60000; fallosFirma = 0; mensaje("SEGURIDAD: 5 firmas invalidas, comandos remotos bloqueados 60 s"); }
    return "firma";
  }
  fallosFirma = 0;
  return "";
}

// ───────── Comandos ─────────
void procesarComando(char comando) {     // tu lógica original
  if (comando == 'A' || comando == 'a') {
    modoManualServo = true;
    mensaje("MODO MANUAL: Abriendo ventana a 0°...");
    moverVentana(ANGULO_ABIERTA);
  }
  else if (comando == 'C' || comando == 'c') {
    modoManualServo = true;
    mensaje("MODO MANUAL: Cerrando ventana a 120°...");
    moverVentana(ANGULO_CERRADA);
  }
  else if (comando == 'M' || comando == 'm') {
    modoManualFan = true;
    velocidadManualFan = 255;
    setFan(255);
    fanEstabaApagado = false;
    mensaje("MODO MANUAL FAN: MÁXIMA POTENCIA (PWM: 255)");
  }
  else if (comando == '+') {
    modoManualFan = true;
    velocidadManualFan += 25;
    if (velocidadManualFan > 255) velocidadManualFan = 255;
    if (velocidadManualFan == 25) { setFan(255); delay(100); }
    setFan(velocidadManualFan);
    fanEstabaApagado = false;
    mensaje("MODO MANUAL FAN: Subiendo velocidad -> PWM: " + String(velocidadManualFan));
  }
  else if (comando == '-') {
    modoManualFan = true;
    velocidadManualFan -= 25;
    if (velocidadManualFan <= 0) { velocidadManualFan = 0; fanEstabaApagado = true; }
    setFan(velocidadManualFan);
    mensaje("MODO MANUAL FAN: Bajando velocidad -> PWM: " + String(velocidadManualFan));
  }
  else if (comando == '0') {
    modoManualFan = true;
    velocidadManualFan = 0;
    setFan(0);
    fanEstabaApagado = true;
    mensaje("MODO MANUAL FAN: Ventilador apagado (PWM: 0)");
  }
  else if (comando == 'X' || comando == 'x') {
    modoManualServo = false;
    modoManualFan = false;
    mensaje("MODO AUTOMÁTICO: Reactivado. Sensores retoman el control.");
  }
}

bool soloDigitos(const String& s) {
  if (s.length() == 0 || s.length() > 3) return false;
  for (unsigned int i = 0; i < s.length(); i++) if (!isDigit(s[i])) return false;
  return true;
}

/** Lista blanca: A C M + - 0 X, V0..V255, W0..W90 y reglas T/L/H/U con sus rangos. Devuelve false si no es válido. */
bool procesarTexto(String c) {
  c.trim();
  c.toUpperCase();
  if (c.length() == 1 && String("ACM+-0X").indexOf(c[0]) >= 0) { procesarComando(c[0]); return true; }
  if (c.length() >= 2 && soloDigitos(c.substring(1))) {
    int n = c.substring(1).toInt();
    if (c[0] == 'V' && n <= 255) {
      modoManualFan = true;
      if (fanEstabaApagado && n > 0) { setFan(255); delay(100); }   // golpe de arranque
      velocidadManualFan = n;
      setFan(n);
      fanEstabaApagado = (n == 0);
      mensaje("MODO MANUAL FAN: PWM -> " + String(n));
      return true;
    }
    if (c[0] == 'T' && n >= 15 && n <= 40) {
      pVentiladorSobre = n; guardarRegla("ventSobre", n);
      mensaje("REGLA: ventilador sobre " + String(n) + " °C");
      return true;
    }
    if (c[0] == 'L' && n >= 5 && n <= 40 && n <= pVentanaHasta - 2) {
      pVentanaDesde = n; guardarRegla("ventDesde", n);
      mensaje("REGLA: ventana empieza a abrir en " + String(n) + " °C");
      return true;
    }
    if (c[0] == 'H' && n >= 10 && n <= 45 && n >= pVentanaDesde + 2) {
      pVentanaHasta = n; guardarRegla("ventHasta", n);
      mensaje("REGLA: ventana abierta del todo en " + String(n) + " °C");
      return true;
    }
    if (c[0] == 'U' && n >= 40 && n <= 100) {
      pHumedadMaxima = n; guardarRegla("humMax", n);
      mensaje("REGLA: humedad del aire máxima " + String(n) + " %");
      return true;
    }
    if (c[0] == 'W' && n <= 90) {
      modoManualServo = true;
      moverVentana(map(n, 0, 90, ANGULO_CERRADA, ANGULO_ABIERTA));   // app: 0 = cerrada
      mensaje("MODO MANUAL: Ventana a " + String(n) + "° de apertura");
      return true;
    }
  }
  return false;
}

// ───────── Sensores ─────────
void leerSensores() {
  humedadSueloAnalog = analogRead(SOIL_A_PIN);
  float h = dht.readHumidity();
  float t = dht.readTemperature();
  if (!isnan(h) && !isnan(t)) { humedadAmbiente = h; temperatura = t; fallosDht = 0; }
  else if (fallosDht < 1000) fallosDht++;
}

/** Suma la lectura actual al resumen en curso (cada 2 s). */
void acumular() {
  if (candado == NULL || xSemaphoreTake(candado, pdMS_TO_TICKS(20)) != pdTRUE) return;
  Acumulado& a = acumulado;
  a.n++;
  if (!isnan(temperatura)) {
    a.tSum += temperatura; a.tN++;
    if (temperatura < a.tMin) a.tMin = temperatura;
    if (temperatura > a.tMax) a.tMax = temperatura;
  }
  if (!isnan(humedadAmbiente)) { a.haSum += humedadAmbiente; a.haN++; }
  a.hsSum += sueloPorcentaje(humedadSueloAnalog);
  a.pwmSum += pwmFanActual;
  a.ventanaSum += map(anguloActual, ANGULO_CERRADA, ANGULO_ABIERTA, 0, 90);
  if (pwmFanActual > 0) a.ventiladorMs += intervaloLectura;
  xSemaphoreGive(candado);
}

// ───────── Ciclo automático (tu lógica, cada 30 s) ─────────
void cicloAutomatico() {
  mensaje("----------------------------------------");
  if (isnan(temperatura)) {
    mensaje("¡Error al leer el sensor DHT22 en el pin 4!");
  } else {
    bool muyHumedo = pHumedadMaxima < 100 && !isnan(humedadAmbiente) && humedadAmbiente > pHumedadMaxima;
    if (!modoManualFan) {
      if (temperatura > pVentiladorSobre) {
        int velocidadVentilador = constrain(map((int)temperatura, pVentiladorSobre, pVentiladorSobre + 10, 200, 255), 200, 255);
        if (fanEstabaApagado) { setFan(255); delay(100); fanEstabaApagado = false; }
        setFan(velocidadVentilador);
        ventilandoPorTiempo = false;
        contadorCiclos = 0;
      } else if (muyHumedo) {            // regla de humedad: ventila suave aunque no haga calor
        if (fanEstabaApagado) { setFan(255); delay(100); fanEstabaApagado = false; }
        setFan(200);
        ventilandoPorTiempo = false;
        contadorCiclos = 0;
      } else {
        contadorCiclos++;
        if (!ventilandoPorTiempo && contadorCiclos >= CICLOS_PARA_RESPIRAR) {
          setFan(255); ventilandoPorTiempo = true; fanEstabaApagado = false; contadorCiclos = 0;
        } else if (ventilandoPorTiempo && contadorCiclos >= 1) {
          setFan(0); ventilandoPorTiempo = false; fanEstabaApagado = true; contadorCiclos = 0;
        } else if (!ventilandoPorTiempo) {
          setFan(0); fanEstabaApagado = true;
        }
      }
    }
    if (!modoManualServo) {
      int destino;
      if (temperatura > pVentanaHasta)      destino = ANGULO_ABIERTA;
      else if (temperatura < pVentanaDesde) destino = ANGULO_CERRADA;
      else                                  destino = map((int)temperatura, pVentanaDesde, pVentanaHasta, ANGULO_CERRADA, ANGULO_ABIERTA);
      if (muyHumedo) destino = min(destino, (ANGULO_CERRADA + ANGULO_ABIERTA) / 2);   // al menos a medio abrir
      if (anguloObjetivo != destino) { mensaje("Auto: Ajustando ventana a " + String(destino) + "°"); moverVentana(destino); }
    }
    mensaje("Temp: " + String(temperatura) + " °C | Hum: " + String(humedadAmbiente) +
            " % | Servo: " + (modoManualServo ? "MANUAL" : "AUTO") + " | Fan: " + (modoManualFan ? "MANUAL" : "AUTO"));
  }
  mensaje("Humedad de Planta: " + String(humedadSueloAnalog) + " -> " + estadoSuelo(humedadSueloAnalog));
}

// ───────── JSON para la app ─────────
String jsonDatos() {
  int aperturaApp = map(anguloActual, ANGULO_CERRADA, ANGULO_ABIERTA, 0, 90);
  String j = "{";
  j += "\"humedades\":[" + String(sueloPorcentaje(humedadSueloAnalog), 1) + "]";
  j += ",\"estadosSuelo\":[\"" + estadoSuelo(humedadSueloAnalog) + "\"]";
  j += ",\"temperatura\":" + String(isnan(temperatura) ? 0.0f : temperatura, 1);
  if (!isnan(humedadAmbiente)) j += ",\"humedadAmbiente\":" + String(humedadAmbiente, 1);   // sin sensor: no se envía
  j += ",\"ventilador\":" + String(pwmFanActual > 0 ? "true" : "false");
  j += ",\"velocidadVentilador\":" + String(pwmFanActual);
  j += ",\"anguloVentana\":" + String(aperturaApp);
  j += ",\"modoVentilador\":\"" + String(modoManualFan ? "MANUAL" : "AUTO") + "\"";
  j += ",\"modoVentana\":\"" + String(modoManualServo ? "MANUAL" : "AUTO") + "\"";
  j += ",\"parametros\":{\"ventiladorSobre\":" + String(pVentiladorSobre) + ",\"ventanaDesde\":" + String(pVentanaDesde) +
       ",\"ventanaHasta\":" + String(pVentanaHasta) + ",\"humedadMaxima\":" + String(pHumedadMaxima) + "}";
  j += ",\"nonce\":\"" + nonce + "\"";
  j += "}";
  return j;
}

// ───────── WiFi ─────────
void responderDatos() {
  String cuerpo = jsonDatos();
  server.sendHeader("X-Firma", hmacHex(cuerpo));
  server.sendHeader("Cache-Control", "no-store");
  server.send(200, "application/json; charset=utf-8", cuerpo);
}

void responderComando() {
  String c = server.arg("c");
  String f = server.arg("f");
  String motivo = verificarComando(c, f);
  if (motivo != "") {
    mensaje("SEGURIDAD: comando WiFi rechazado (" + motivo + ") desde " + server.client().remoteIP().toString());
    server.send(motivo == "bloqueado" ? 429 : 403, "text/plain", "rechazado");
    return;
  }
  if (!procesarTexto(c)) { server.send(400, "text/plain", "comando no permitido"); return; }
  nuevoNonce();          // el comando ya usado no sirve otra vez
  responderDatos();
}

// ───────── Bluetooth ─────────
void enviarDatosBT() {
  if (!USAR_BLUETOOTH || !SerialBT.hasClient()) return;
  String cuerpo = jsonDatos();
  SerialBT.print("D|");
  SerialBT.print(cuerpo);
  SerialBT.print("|");
  SerialBT.println(hmacHex(cuerpo));
}

void procesarLineaBT(const String& linea) {   // "C|<cmd>|<firma>"
  int a = linea.indexOf('|');
  int b = linea.lastIndexOf('|');
  if (!linea.startsWith("C|") || b <= a) return;
  String cmd = linea.substring(a + 1, b);
  String firma = linea.substring(b + 1);
  firma.trim();
  String motivo = verificarComando(cmd, firma);
  if (motivo != "") { mensaje("SEGURIDAD: comando BT rechazado (" + motivo + ")"); return; }
  if (procesarTexto(cmd)) { nuevoNonce(); enviarDatosBT(); }
}

void leerBT() {
  if (!USAR_BLUETOOTH) return;
  while (SerialBT.available()) {
    char ch = SerialBT.read();
    if (ch == '\n') { procesarLineaBT(bufferBT); bufferBT = ""; }
    else if (ch != '\r' && bufferBT.length() < 128) bufferBT += ch;
  }
}

// ───────── Nube (Firebase Realtime Database por HTTPS) ─────────
String tokenNube;                  // ID token de Firebase (dura 1 h; se renueva a los 50 min)
unsigned long tokenDesde = 0;
const int MAX_PENDIENTES = 24;     // 24 resúmenes de 5 min = 2 h sin Internet
String pendientes[MAX_PENDIENTES];
int nPendientes = 0;
bool avisoActivo[4] = {false, false, false, false};
unsigned long avisoUltimo[4] = {0, 0, 0, 0};

bool nubeConfigurada() {
  return NUBE_ACTIVA && strncmp(INVERNADERO_ID, "PEGA", 4) != 0 && strncmp(FIREBASE_API_KEY, "PEGA", 4) != 0;
}

bool horaLista() { return time(nullptr) > 1700000000; }   // ya llegó la hora por NTP

/** Hora actual en ms (si ya hay hora) o la hora del servidor de Firebase. */
String marcaDeTiempo() {
  if (!horaLista()) return "{\".sv\":\"timestamp\"}";
  char b[24];
  snprintf(b, sizeof(b), "%llu", (unsigned long long)time(nullptr) * 1000ULL);
  return String(b);
}

/** Valor de texto de un campo en un JSON simple ("clave":"valor"). */
String campoJson(const String& json, const char* clave) {
  int i = json.indexOf(String("\"") + clave + "\"");
  if (i < 0) return "";
  i = json.indexOf(':', i);
  if (i < 0) return "";
  i = json.indexOf('"', i);
  if (i < 0) return "";
  int f = json.indexOf('"', i + 1);
  return f < 0 ? "" : json.substring(i + 1, f);
}

/** Petición HTTPS verificada. Devuelve el código HTTP (o negativo si no hubo conexión). */
int pedirNube(const char* metodo, const String& url, const String& cuerpo, String* respuesta) {
  WiFiClientSecure cliente;
  cliente.setCACert(CA_GOOGLE);
  HTTPClient http;
  http.setConnectTimeout(10000);
  http.setTimeout(10000);
  if (!http.begin(cliente, url)) return -1;
  http.addHeader("Content-Type", "application/json");
  int codigo = http.sendRequest(metodo, cuerpo);
  if (respuesta != nullptr && codigo > 0) *respuesta = http.getString();
  http.end();
  if (codigo < 0) mensaje("NUBE: sin conexión (" + HTTPClient::errorToString(codigo) + ", heap libre " + String(ESP.getFreeHeap()) + ")");
  return codigo;
}

/** Entra con la cuenta propia del ESP32 (la creó la app) y guarda el ID token. */
bool sesionNube() {
  if (tokenNube.length() > 0 && millis() - tokenDesde < 50UL * 60 * 1000) return true;
  String url = String("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=") + FIREBASE_API_KEY;
  String cuerpo = String("{\"email\":\"") + ESP32_CORREO + "\",\"password\":\"" + ESP32_CLAVE + "\",\"returnSecureToken\":true}";
  String r;
  int codigo = pedirNube("POST", url, cuerpo, &r);
  tokenNube = codigo == 200 ? campoJson(r, "idToken") : "";
  if (tokenNube.length() == 0) {
    mensaje("NUBE: no se pudo entrar (HTTP " + String(codigo) + "). Revisa las credenciales pegadas desde la app.");
    return false;
  }
  tokenDesde = millis();
  mensaje("NUBE: sesión iniciada");
  return true;
}

/** Escribe en invernaderos/<id>/<ruta>.json. PUT reemplaza, POST agrega uno nuevo. */
bool escribirNube(const char* metodo, const char* ruta, const String& json) {
  String url = String(FIREBASE_URL) + "/invernaderos/" + INVERNADERO_ID + "/" + ruta + ".json?print=silent&auth=" + tokenNube;
  int codigo = pedirNube(metodo, url, json, nullptr);
  if (codigo == 401 || codigo == 403) {
    tokenNube = "";      // token vencido o sin permiso: se vuelve a entrar en el próximo intento
    mensaje("NUBE: la base rechazó la escritura en " + String(ruta) + " (¿ESP32 vinculado? ¿reglas publicadas?)");
  }
  return codigo == 200 || codigo == 204;
}

/** Estado actual en el mismo formato que lee la app (modo "Desde la nube"). */
String jsonEstadoNube() {
  int aperturaApp = map(anguloActual, ANGULO_CERRADA, ANGULO_ABIERTA, 0, 90);
  String j = "{\"ts\":{\".sv\":\"timestamp\"}";
  if (!isnan(temperatura)) j += ",\"temperatura\":" + String(temperatura, 1);
  if (!isnan(humedadAmbiente)) j += ",\"humedadAire\":" + String(humedadAmbiente, 1);
  j += ",\"humedades\":[" + String(sueloPorcentaje(humedadSueloAnalog), 1) + "]";
  j += ",\"estadosSuelo\":[\"" + estadoSuelo(humedadSueloAnalog) + "\"]";
  j += ",\"pwm\":" + String(pwmFanActual);
  j += ",\"ventana\":" + String(aperturaApp);
  j += ",\"modoVentilador\":\"" + String(modoManualFan ? "MANUAL" : "AUTO") + "\"";
  j += ",\"modoVentana\":\"" + String(modoManualServo ? "MANUAL" : "AUTO") + "\"";
  j += ",\"parametros\":{\"ventiladorSobre\":" + String(pVentiladorSobre) + ",\"ventanaDesde\":" + String(pVentanaDesde) +
       ",\"ventanaHasta\":" + String(pVentanaHasta) + ",\"humedadMaxima\":" + String(pHumedadMaxima) + "}";
  j += "}";
  return j;
}

/** Cierra el resumen de los últimos 5 min (promedio, mínimo y máximo) y lo deja en la cola. */
void cerrarResumen() {
  if (candado == NULL || xSemaphoreTake(candado, pdMS_TO_TICKS(500)) != pdTRUE) return;
  Acumulado a = acumulado;
  acumulado = Acumulado();
  xSemaphoreGive(candado);
  if (a.n == 0) return;
  float hs = a.hsSum / a.n;
  String j = "{\"ts\":" + marcaDeTiempo() + ",\"n\":" + String(a.n);
  if (a.tN > 0) j += ",\"tProm\":" + String(a.tSum / a.tN, 1) + ",\"tMin\":" + String(a.tMin, 1) + ",\"tMax\":" + String(a.tMax, 1);
  if (a.haN > 0) j += ",\"haProm\":" + String(a.haSum / a.haN, 1);
  j += ",\"hsProm\":" + String(hs, 1) + ",\"humedades\":[" + String(hs, 1) + "]";
  j += ",\"pwmProm\":" + String(a.pwmSum / a.n, 0) + ",\"ventanaProm\":" + String(a.ventanaSum / a.n, 0);
  j += ",\"ventiladorSeg\":" + String(a.ventiladorMs / 1000) + "}";
  if (nPendientes == MAX_PENDIENTES) {          // cola llena: se descarta el más viejo
    for (int i = 1; i < MAX_PENDIENTES; i++) pendientes[i - 1] = pendientes[i];
    nPendientes--;
  }
  pendientes[nPendientes++] = j;
}

/** Sube los resúmenes en cola, del más viejo al más nuevo. Se detiene en el primero que falla. */
void enviarPendientes() {
  while (nPendientes > 0) {
    if (!escribirNube("POST", "resumenes", pendientes[0])) return;
    for (int i = 1; i < nPendientes; i++) pendientes[i - 1] = pendientes[i];
    pendientes[--nPendientes] = "";
  }
}

bool subirAviso(const char* tipo, const String& detalle) {
  String j = String("{\"ts\":{\".sv\":\"timestamp\"},\"tipo\":\"") + tipo + "\",\"detalle\":\"" + detalle.substring(0, 110) + "\"}";
  bool ok = escribirNube("POST", "alertas", j);
  if (ok) mensaje("NUBE: aviso " + String(tipo) + " -> " + detalle);
  return ok;
}

/** Un aviso se sube una vez al entrar en la condición; se rearma al salir (y como máximo cada 15 min). */
void vigilar(int i, bool entra, bool sale, const char* tipo, const String& detalle) {
  if (!avisoActivo[i] && entra && (avisoUltimo[i] == 0 || millis() - avisoUltimo[i] > 15UL * 60 * 1000)) {
    if (subirAviso(tipo, detalle)) { avisoActivo[i] = true; avisoUltimo[i] = millis(); }
  } else if (avisoActivo[i] && sale) {
    avisoActivo[i] = false;
  }
}

void revisarAvisos() {
  float t = temperatura;
  float hs = sueloPorcentaje(humedadSueloAnalog);
  if (!isnan(t)) {
    vigilar(0, t > pVentanaHasta + 5, t < pVentanaHasta + 3, "CALOR",
            "Hace calor: " + String(t, 1) + " °C (la ventana abre del todo en " + String(pVentanaHasta) + " °C)");
    vigilar(1, t < pVentanaDesde - 5, t > pVentanaDesde - 3, "FRIO", "Hace frío: " + String(t, 1) + " °C");
  }
  vigilar(2, hs < 15, hs > 25, "SECO", "La tierra está seca: " + String(hs, 0) + " % de humedad");
  vigilar(3, fallosDht >= 5, fallosDht == 0, "SENSOR", "El sensor de temperatura y humedad no responde");
}

/** Tarea en el núcleo 0: todo lo de la nube. El loop (control, WiFi local, Bluetooth) nunca la espera. */
void tareaNube(void* parametro) {
  unsigned long proximoResumen = millis() + CADA_RESUMEN_MS;
  unsigned long proximoEstado = 0;
  unsigned long proximaRevision = 0;
  bool avisoInicio = false;
  for (;;) {
    vTaskDelay(pdMS_TO_TICKS(1000));
    // El resumen se cierra cada 5 min aunque no haya Internet: espera en la cola
    if ((long)(millis() - proximoResumen) >= 0) { proximoResumen += CADA_RESUMEN_MS; cerrarResumen(); }
    // HTTPS necesita WiFi y la hora (para validar los certificados)
    if (WiFi.status() != WL_CONNECTED || !horaLista()) continue;
    if (!sesionNube()) { vTaskDelay(pdMS_TO_TICKS(60000)); continue; }
    if (!avisoInicio) avisoInicio = subirAviso("INICIO", "El ESP32 se encendió (reinicio o corte de luz)");
    if ((long)(millis() - proximoEstado) >= 0) {
      proximoEstado = millis() + (escribirNube("PUT", "estado", jsonEstadoNube()) ? CADA_ESTADO_MS : 30000UL);
    }
    enviarPendientes();
    if ((long)(millis() - proximaRevision) >= 0) { proximaRevision = millis() + 10000; revisarAvisos(); }
  }
}

// ───────── Arranque ─────────
void setup() {
  Serial.begin(115200);
  cargarReglas();
  nuevoNonce();
  if (USAR_BLUETOOTH) SerialBT.begin(NOMBRE_BT);

  analogReadResolution(12);
  analogSetPinAttenuation(SOIL_A_PIN, ADC_11db);
  pinMode(SOIL_D_PIN, INPUT);
  pinMode(FAN_PIN, OUTPUT);
  setFan(0);

  dht.begin();
  motorVentana.setPeriodHertz(50);
  motorVentana.attach(SERVO_PIN, 500, 2400);
  motorVentana.write(anguloActual);

  WiFi.mode(WIFI_STA);
  if (USAR_IP_FIJA) WiFi.config(IP_FIJA, PUERTA, MASCARA, DNS1);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Conectando a WiFi");
  unsigned long inicio = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - inicio < 15000) { delay(500); Serial.print("."); }
  Serial.println();
  if (WiFi.status() == WL_CONNECTED) Serial.println("IP del ESP32: " + WiFi.localIP().toString());
  else Serial.println("Sin WiFi: sigue funcionando por Bluetooth y Serial.");

  // Nube: hora por NTP (necesaria para validar HTTPS) y tarea aparte en el núcleo 0
  candado = xSemaphoreCreateMutex();
  configTime(0, 0, "pool.ntp.org", "time.google.com");
  if (nubeConfigurada()) {
    xTaskCreatePinnedToCore(tareaNube, "nube", 16384, NULL, 1, NULL, 0);
    mensaje("Nube: activada (estado cada 2 min, resumen cada 5 min)");
  } else {
    mensaje("Nube: sin configurar (pega las credenciales de la app en la sección NUBE)");
  }

  server.on("/datos", responderDatos);
  server.on("/comando", responderComando);
  server.onNotFound([]() { server.send(404, "text/plain", "no existe"); });
  server.begin();

  mensaje("Sistema iniciado. Bluetooth: " + String(USAR_BLUETOOTH ? NOMBRE_BT : "desactivado"));
  mensaje("Serial USB: '+' '-' 'M' '0' (fan), 'A' 'C' (ventana), 'X' (auto)");
  leerSensores();
}

void loop() {
  if (Serial.available() > 0) {          // mantenimiento local por USB
    char ch = Serial.read();
    if (ch != '\n' && ch != '\r') procesarComando(ch);
  }
  leerBT();
  server.handleClient();
  actualizarServo();

  if (millis() - ultimaLectura >= intervaloLectura) {
    ultimaLectura = millis();
    leerSensores();
    acumular();
    enviarDatosBT();
  }

  if (millis() - tiempoAnterior >= intervalo) {
    tiempoAnterior = millis();
    cicloAutomatico();
  }

  static unsigned long ultimoIntentoWifi = 0;
  if (WiFi.status() != WL_CONNECTED && millis() - ultimoIntentoWifi > 10000) {
    ultimoIntentoWifi = millis();
    WiFi.reconnect();
  }
}
