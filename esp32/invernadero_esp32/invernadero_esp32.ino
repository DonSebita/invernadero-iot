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
 * Seguridad:
 *   - La clave SECRETO nunca viaja por la red; solo viajan firmas.
 *   - El nonce cambia después de cada comando: un comando capturado no se puede repetir.
 *   - 5 firmas inválidas seguidas -> se bloquean los comandos remotos 60 s.
 *   - Lista blanca de comandos y validación de rangos.
 *
 * Librerías: "DHT sensor library" (Adafruit), "Adafruit Unified Sensor", "ESP32Servo"
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

// ───────── CONFIGURA ESTO ─────────
const char* WIFI_SSID     = "TU_WIFI";
const char* WIFI_PASSWORD = "TU_CLAVE";
const char* SECRETO       = "cambia-esta-clave-larga-2025";   // igual que "Clave compartida" en la app (mín. 12)
const char* NOMBRE_BT     = "Invernadero";

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
  if (!isnan(h) && !isnan(t)) { humedadAmbiente = h; temperatura = t; }
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
  if (!SerialBT.hasClient()) return;
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
  while (SerialBT.available()) {
    char ch = SerialBT.read();
    if (ch == '\n') { procesarLineaBT(bufferBT); bufferBT = ""; }
    else if (ch != '\r' && bufferBT.length() < 128) bufferBT += ch;
  }
}

// ───────── Arranque ─────────
void setup() {
  Serial.begin(115200);
  cargarReglas();
  nuevoNonce();
  SerialBT.begin(NOMBRE_BT);

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

  server.on("/datos", responderDatos);
  server.on("/comando", responderComando);
  server.onNotFound([]() { server.send(404, "text/plain", "no existe"); });
  server.begin();

  mensaje("Sistema iniciado. Bluetooth: " + String(NOMBRE_BT));
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
