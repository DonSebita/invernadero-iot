# Invernadero IoT — App Android (TI3042 Unidad 2)

> Modelo **híbrido**: el tiempo real va por la red local (WiFi/Bluetooth con el ESP32, cada 2 s) y a la nube
> (Firebase) se suben menos datos y más lento (estado cada 2 min, resumen cada 5 min). Las cuentas viven en
> Firebase Authentication, así que entras con la misma cuenta en cualquier teléfono.
> **Antes de compilar** hay que crear el proyecto de Firebase y copiar `app/google-services.json` (sección 1.1).
> El sketch del ESP32 está en `esp32/` y las reglas de seguridad de la base en `firebase/database.rules.json`.

## 1. Cómo armarlo en Android Studio

1. **Proyecto**: abre tu proyecto `invernadero` en Android Studio.
   - Ya tienes el proyecto `invernadero` (paquete `com.invernadero`): úsalo, no crees otro.
   - Si algún día usas otro paquete, cambia la primera línea `package ...` de cada archivo y la carpeta.
2. **Gradle**: abre `app/build.gradle.kts` y haz los 2 cambios de `app/build.gradle.kts.CAMBIOS.txt`. Pulsa **Sync Now**.
3. **Manifiesto**: reemplaza `app/src/main/AndroidManifest.xml` por el de esta carpeta.
   - Si tu tema no se llama `Theme.Invernadero`, deja el nombre que trae tu proyecto (está en `res/values/themes.xml`).
4. **XML de seguridad**: crea la carpeta `app/src/main/res/xml/` (si no existe) y copia
   `network_security_config.xml` y `data_extraction_rules.xml` (reemplaza el que trae el proyecto).
5. **Código**: en `app/src/main/java/com/invernadero/` borra el `MainActivity.kt` y la carpeta `ui/theme`
   que crea Android Studio y copia todo esto, respetando las carpetas:

```
com/invernadero/
├── MainActivity.kt
├── InvernaderoApp.kt          ← Application: crea todas las piezas una sola vez
├── seguridad/
│   ├── Contrasenas.kt         ← PBKDF2 + política de contraseñas
│   ├── CajaFuerte.kt          ← ajustes cifrados con Android Keystore (AES-256-GCM)
│   └── Cripto.kt              ← HMAC-SHA256 (ESP32) y AES-GCM (entre teléfonos)
├── datos/
│   ├── BaseDatos.kt           ← SQLite: lecturas cada 30 s (7 días) y auditoría local
│   ├── Usuario.kt             ← quién usa la app (cuenta o invitado) y su rol
│   ├── Sesion.kt              ← sesión en memoria con expiración
│   └── AjustesRepo.kt         ← modo de conexión, IPs, clave compartida
├── nube/
│   ├── Nube.kt                ← Firebase: cuentas, invernadero, pases, estado, resúmenes, avisos, auditoría
│   └── AccesoGoogle.kt        ← "Continuar con Google" (Credential Manager)
├── conexion/
│   ├── Modelos.kt             ← LecturaEsp32, ParametrosControl (reglas), lista blanca de comandos
│   ├── SimuladorEsp32.kt      ← pruebas sin hardware
│   ├── Esp32Wifi.kt           ← HTTP firmado con el ESP32
│   ├── Esp32Bluetooth.kt      ← Bluetooth SPP firmado con el ESP32
│   ├── Interconexion.kt       ← teléfono central ↔ teléfonos remotos (TCP cifrado + NSD)
│   ├── FuenteNube.kt          ← modo "Desde la nube" (último estado que subió el ESP32)
│   └── ControladorConexion.kt ← une todo: métricas, historial, auditoría, permisos
└── ui/
    ├── AppInvernadero.kt      ← login → menú inferior (Monitoreo, Controles, Historial, Ajustes)
    ├── Tema.kt  Comunes.kt
    ├── acceso/PantallaAcceso.kt
    ├── monitoreo/PantallaMonitoreo.kt  Escena3D.kt  Paletas.kt
    ├── controles/PantallaControles.kt
    ├── historial/PantallaHistorial.kt
    └── ajustes/PantallaAjustes.kt
```

6. **Firebase**: sigue la sección 1.1 (sin `app/google-services.json` el proyecto no compila).
7. **Ejecuta** ▶. Crea tu cuenta con correo o con Google (no hay contraseñas por defecto).
   Empieza en modo **Simulador** para probar todo sin hardware.

### 1.1 Configurar Firebase (una sola vez)

1. Entra a <https://console.firebase.google.com> → **Agregar proyecto** (Analytics es opcional).
2. **Agregar app → Android**: paquete `com.invernadero`. Agrega la **huella SHA-1** de tu llave de depuración
   (Android Studio → panel *Gradle* → `app > Tasks > android > signingReport`, o en una terminal:
   `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android`).
   Sin la SHA-1 no funciona *Continuar con Google*.
3. **Authentication → Comenzar → Método de acceso**: habilita **Correo electrónico/contraseña**, **Google** y **Anónimo**
   (anónimo es lo que usan los pases de visita).
4. **Realtime Database → Crear base de datos** (elige ubicación, parte en *modo bloqueado*). En la pestaña **Reglas**
   pega todo el contenido de `firebase/database.rules.json` → **Publicar**.
5. Recién ahora (con Google activado y la base creada) descarga **google-services.json** desde
   *Configuración del proyecto → Tus apps* y cópialo en la carpeta `app/`. → **Sync Now**.
6. En la app: **Ajustes → Nube → Vincular ESP32 → Copiar**, y pega esas 5 líneas en la sección `NUBE` del sketch.

El plan gratuito (Spark) alcanza de sobra: la Realtime Database no cobra por operación, y GreenBox sube
~1.000 escrituras pequeñas al día (estado cada 2 min + resumen cada 5 min) en vez de 43.200 (una cada 2 s).

### ESP32
Abre `esp32/invernadero_esp32/invernadero_esp32.ino` y cambia `WIFI_SSID`, `WIFI_PASSWORD`, `SECRETO`
y la IP fija (debe ser libre en tu red; la de `network_security_config.xml` debe ser la misma).
En la app: *Ajustes > WiFi → ESP32*, misma IP y la **misma clave compartida** que `SECRETO`.
Para la nube, pega en la sección `NUBE` del sketch lo que entrega *Ajustes → Nube → Vincular ESP32*.
Usa el core **esp32 de Espressif 3.x** (trae `WiFiClientSecure` y `HTTPClient`). En el Monitor Serie verás
`NUBE: sesión iniciada` cuando suba datos.

### Dos (o más) teléfonos
| Teléfono A (central) | Teléfono B (remoto) |
|---|---|
| Ajustes: WiFi o Bluetooth → ESP32 | Ajustes: **Otro teléfono (central)** |
| Activa **Compartir con otros teléfonos** | Pulsa **Buscar en la red** (o escribe la IP que muestra A) |
| Misma clave compartida | Misma clave compartida |

Ambos en la misma red WiFi (sirve el hotspot de uno de ellos). B ve la misma caja animada y puede
enviar órdenes desde Controles; A las reenvía al ESP32 y las registra en auditoría con el usuario de B.

---

## 2. Qué cubre de la rúbrica y dónde mostrarlo

| Criterio | Dónde está |
|---|---|
| Login con usuario y contraseña | `ui/acceso`, `nube/Nube.kt`: Firebase Authentication (correo/contraseña y Google); misma cuenta en todos los teléfonos |
| Herramientas y bibliotecas adicionales | Ver sección 3 (Firebase BoM: Auth + Realtime Database, Credential Manager + Google ID, coroutines-play-services; en el ESP32: WiFiClientSecure + HTTPClient) |
| Pantallas Monitoreo y Controles, Material Design | Monitoreo (caja 3D + datos), Controles (RadioButtons, Switch, Sliders, botones, campos de texto numéricos con validación para las reglas del modo automático, diálogo de confirmación, Snackbar) |
| Conexiones inalámbricas eficientes y seguras | WiFi (HTTP firmado), Bluetooth SPP (enlace emparejado + firma), reconexión automática, timeouts, polling de 2 s |
| Uso innovador de la conexión | Gemelo digital 3D en vivo, teléfono central que comparte con remotos, descubrimiento automático NSD |
| Seguridad ISO 27001 / 27400 | Ver sección 3 |
| Lineamientos OT documentados | Ver sección 4 |
| Interconexión entre apps móviles | Modo central/remoto (`conexion/Interconexion.kt`) |
| Estabilidad y eficiencia en escenarios | Historial > *Ver registro de actividad* > *Estabilidad de la conexión* + sección 5 |
| Almacenamiento y consulta de datos | Híbrido: SQLite (lecturas cada 30 s, 7 días) + Firebase Realtime Database (estado cada 2 min, resúmenes cada 5 min, avisos); el Historial junta ambos |
| Interoperabilidad de los datos de los sensores | El ESP32 escribe JSON por la API REST de Firebase (HTTPS); la app lo lee con el SDK. Mismo formato de campos que la API local `/datos` |
| Permisos | `AndroidManifest.xml` + permiso de Bluetooth pedido en tiempo de ejecución (Ajustes) |

---

## 3. Justificación de decisiones

**Herramientas** — Kotlin + Jetpack Compose + Material 3 (estándar actual recomendado por Google),
Navigation Compose para la navegación, corrutinas/Flow para trabajo en segundo plano sin bloquear la UI.
Solo APIs nativas de Android para SQLite, cifrado, Bluetooth, sockets y NSD: menos dependencias = menos
superficie de ataque y más fácil de mantener.

**Almacenamiento y autenticación: modelo híbrido SQLite + Firebase**

| | SQLite (local) | **Firebase** (Auth + Realtime Database) | MySQL | API externa propia |
|---|---|---|---|---|
| Funciona sin Internet | Sí | Sí, con caché local y cola de escrituras | No | No |
| Varios teléfonos / iniciar sesión en otro | No | **Sí** | Sí | Sí |
| Servidor que mantener | No | No (servicio administrado) | Sí (servidor + driver o API intermedia) | Sí |
| Autenticación | Hecha a mano | **OAuth 2.0 / OpenID Connect** (correo, Google, anónimo) | Hecha a mano | Hecha a mano |
| Acceso desde el ESP32 | No | **API REST + JSON por HTTPS** | Necesita una API intermedia | Sí, si se programa |
| Seguridad por registro | No aplica | **Reglas declarativas** en el servidor | Usuarios/permisos SQL | Depende |
| Costo para este proyecto | 0 | 0 (plan Spark) | Hosting | Hosting |

Se eligió un **modelo híbrido**:
- **SQLite** (API nativa `SQLiteOpenHelper`, estándar de Android para datos estructurados locales) guarda el detalle
  fino: una lectura cada 30 s y la auditoría local. No depende de Internet.
- **Firebase Realtime Database** guarda lo que debe estar en todos lados: el *estado* que sube el ESP32 cada 2 min,
  un *resumen* cada 5 min (promedio, mínimo y máximo), *avisos*, los pases de visita y una copia de la auditoría.
  Se eligió Realtime Database (no Firestore) porque es un árbol JSON accesible por **REST** con una simple petición
  HTTPS, ideal para un microcontrolador, no cobra por operación y sincroniza en tiempo real con el SDK de Android.
- **Firebase Authentication** reemplaza las cuentas locales: contraseñas guardadas y protegidas por Google
  (nunca pasan por la base de la app), recuperación por correo, *Continuar con Google* y sesiones anónimas
  temporales para los invitados.

**Interoperabilidad de los datos de los sensores.** El ESP32 publica JSON con los mismos nombres de campos que usa
la app (`temperatura`, `humedadAire`, `humedades[]`, `pwm`, `ventana`, `parametros{}`), con marca de tiempo del
servidor o de NTP. Cualquier cliente que hable HTTPS + JSON (otra app, una web, Python, una hoja de cálculo) puede
leerlos con la misma API REST, siempre bajo las reglas de seguridad.

**Bibliotecas agregadas** (`gradle/libs.versions.toml`):

| Biblioteca | Para qué |
|---|---|
| `com.google.gms.google-services` (plugin) | Lee `google-services.json` |
| `firebase-bom` → `firebase-auth`, `firebase-database` | Cuentas y base de datos en la nube |
| `androidx.credentials` + `credentials-play-services-auth` + `googleid` | *Continuar con Google* con **Credential Manager**, la API de inicio de sesión que recomienda Android |
| `kotlinx-coroutines-play-services` | `await()` para usar Firebase con corrutinas (sin bloquear la interfaz) |
| ESP32: `WiFiClientSecure`, `HTTPClient` (core Espressif) | HTTPS verificado hacia Firebase |

**Comunicación**

| | WiFi (HTTP local) | Bluetooth clásico | MQTT |
|---|---|---|---|
| Distancia | Toda la cobertura del router (~30–50 m interior) | ~10 m | Ilimitada (vía Internet) |
| Consumo ESP32 | Medio | Bajo–medio | Medio + broker |
| Estabilidad | Alta con router estable | Alta a corta distancia | Depende de Internet y del broker |
| Varios teléfonos | Sí | 1 a la vez | Sí |
| Infraestructura | Router o hotspot | Ninguna | Broker externo |

Se eligió **WiFi** como principal (alcance, varios clientes, sin Internet), **Bluetooth** como respaldo
cuando no hay router, y **TCP entre teléfonos** para la interconexión (no requiere broker externo).
Para ver el invernadero **desde lejos** se usa Firebase en vez de MQTT: no hay broker que mantener, las
cuentas y los permisos ya vienen integrados, y si la conexión local se cae la app muestra sola el último estado
de la nube (*Mirando desde la nube*). Por la nube **no viajan órdenes**: el control sigue siendo local y firmado.

---

## 4. Seguridad (ISO/IEC 27001 Anexo A, ISO/IEC 27400 IoT, IEC 62443 OT)

| Medida | Implementación | Referencia |
|---|---|---|
| Sin contraseñas por defecto | No existe ninguna cuenta hasta que el usuario crea la suya | 27400 (credenciales únicas), IEC 62443 CR 1.5 |
| Contraseñas protegidas | Las guarda Firebase Authentication (hash en el servidor de Google); nunca se guardan en el teléfono ni en la base de la app. Recuperación solo por enlace al correo | 27001 A.8.5 / A.5.17 |
| Política de contraseñas | Mín. 8, letras y números, no contiene el nombre (además de la política de Firebase) | 27001 A.5.17 |
| Bloqueo por fuerza bruta | En el teléfono: 5 intentos fallidos → 5 min (guardado cifrado, no se salta reiniciando la app). En el servidor: límite de Firebase por dispositivo. Mismo mensaje si falla el correo o la clave; "olvidé mi contraseña" no revela si el correo existe | 27001 A.8.5, IEC 62443 CR 1.11 |
| Autenticación federada | *Continuar con Google* por Credential Manager (OpenID Connect); el token de Google se cambia por una sesión de Firebase | 27001 A.8.5 |
| Reglas de la base (servidor) | `firebase/database.rules.json`: todo cerrado por defecto; cada cuenta solo lee su invernadero; validación de tipos y rangos de cada campo | 27001 A.5.15 / A.8.3, IEC 62443 CR 2.1 |
| Mínimo privilegio del ESP32 | Cuenta propia (correo interno + clave al azar de 24 caracteres, se muestra una vez); solo puede escribir *estado*, *resúmenes* y *avisos* de su invernadero; "Generar credenciales nuevas" revoca la anterior | 27400, IEC 62443 CR 1.2 / CR 2.1 |
| Nube solo para mirar | Por Firebase no llegan órdenes al ESP32: los comandos siguen siendo locales y firmados con HMAC | IEC 62443 (zonas y conductos) |
| Control de acceso | Cuentas de usuario e invitados con pase temporal ("solo mirar" o "regar y ventilar"); el ESP32 solo recibe órdenes permitidas | 27001 A.5.15 / A.8.2, IEC 62443 CR 2.1 |
| Cierre de sesión por inactividad | 10 min (salvo "Recordarme", que el usuario elige en su propio teléfono) | 27001 A.8.5, IEC 62443 CR 2.6 |
| "Recordarme en este teléfono" | La sesión de Firebase se conserva solo si se marcó; 30 días desde el último uso (marca cifrada con Android Keystore); se borra al cerrar sesión. Sin marcarlo, al abrir la app se cierra la sesión guardada | 27001 A.8.5 |
| Pases de visita en la nube | Clave de 12 caracteres (≈59 bits), solo su SHA-256 en la base; el invitado entra con una identidad anónima temporal que se borra al terminar; las reglas comprueban vencimiento y revocación en el servidor. El vencimiento se calcula con la **hora del servidor** (`nube/Reloj.kt`), así un teléfono con la hora mal puesta no crea pases ya vencidos | 27001 A.5.16 / A.5.18 |
| Cifrado de datos sensibles en el teléfono | Ajustes y clave compartida cifrados con AES-256-GCM en Android Keystore | 27001 A.8.24 |
| Sin respaldo automático de Android | `allowBackup=false`, `data_extraction_rules` (la base local, los ajustes y la caché de Firebase no se copian) | 27001 A.8.10 / A.8.12 |
| Datos en la nube minimizados | Solo resúmenes cada 5 min y el último estado (no cada lectura); resúmenes y avisos de más de 30 días y pases vencidos se borran solos | 27001 A.5.34 / A.8.10 |
| HTTPS verificado desde el ESP32 | `WiFiClientSecure` con los certificados raíz de Google (GTS R1/R4) y hora por NTP; sin `setInsecure()` | 27001 A.8.20 / A.8.24, IEC 62443 CR 3.1 |
| Integridad de los datos del sensor | Cada respuesta del ESP32 firmada con HMAC-SHA256 | 27400, IEC 62443 CR 3.1 |
| Autenticidad de comandos y anti-repetición | Firma HMAC(comando + nonce), nonce de un solo uso | IEC 62443 CR 3.1 / CR 1.2 |
| Comunicación entre teléfonos cifrada | AES-256-GCM, id y hora en cada mensaje, rechazo de repetidos y viejos | 27001 A.8.20 / A.8.24 |
| Tráfico en claro limitado | `network_security_config`: HTTP solo hacia la IP del ESP32 | 27001 A.8.20 |
| Validación de entradas | Lista blanca de comandos en app y ESP32; rangos validados (también las reglas T/L/H/U y que la ventana abra al menos 2 °C después de donde empieza); consultas SQL con parámetros | 27001 A.8.28 |
| Cambio de reglas del modo automático | Solo cuentas con permiso de activar (los pases de visita no pueden), confirmación previa, firmadas como cualquier comando, guardadas en la memoria del ESP32 y auditadas | 27001 A.8.9 / A.8.15, IEC 62443 CR 2.1 |
| Protección ante saturación | Máx. 5 teléfonos remotos; ESP32 bloquea 60 s tras 5 firmas inválidas | IEC 62443 CR 7.1 |
| Registro de auditoría | Login, cierre, comandos (quién, desde dónde, resultado), cambios de ajustes, pases y vinculación del ESP32. Copia en la nube **solo de agregar** (las reglas no permiten editar ni borrar) | 27001 A.8.15, IEC 62443 CR 2.8 |
| Minimización de datos | Historial de 7 días; la clave compartida nunca se registra | 27001 A.5.34 |
| Operación segura ante fallas (OT) | Si se pierde la conexión el ESP32 sigue en automático con su lógica local y las reglas guardadas | IEC 62443 CR 7.6 |
| Separación de redes (OT) | El ESP32 no abre puertos hacia Internet: solo hace conexiones salientes HTTPS a Firebase. El servidor de comandos sigue siendo solo de la red local | IEC 62443 (zonas y conductos) |

**Limitaciones conocidas** (decirlo da puntos en la defensa): en la red local el ESP32 no usa HTTPS (sin certificado);
se compensa con firmas HMAC y nonce, pero los datos de sensores viajan legibles en la red local (hacia Firebase sí van
por HTTPS). El puerto USB acepta comandos sin firma porque requiere acceso físico (mantenimiento). La primera vez que
se entra en un teléfono se necesita Internet. No hay notificaciones push de los avisos (se ven en Historial).

---

## 5. Plan de pruebas (llenar con tus resultados)

| # | Prueba | Cómo | Resultado esperado |
|---|---|---|---|
| 1 | Login correcto | Usuario y clave válidos | Entra a Monitoreo; auditoría LOGIN OK |
| 2 | Login incorrecto ×5 | Clave mala 5 veces | Cuenta bloqueada 5 min; auditoría BLOQUEADO |
| 3 | Pase "Solo mirar" | Crear un pase de visita "Solo mirar" y entrar como Invitado | Controles bloqueado |
| 4 | Expiración | No tocar 10 min | Vuelve al login |
| 5 | Datos WiFi | Modo WiFi | Datos cada 2 s, latencia en Historial |
| 6 | Comando WiFi | Controles > Máximo | Ventilador real a 255; auditoría OK |
| 7 | Clave distinta | Cambiar clave en la app | "firma inválida"; el ESP32 no obedece |
| 8 | Bluetooth | Modo Bluetooth (emparejado) | Datos y comandos funcionan |
| 9 | Interconexión | A central + B remoto | B ve los datos y su comando mueve el ESP32 |
| 10 | Corte de WiFi | Apagar el router 20 s | "Sin datos", +1 corte; reconecta solo |
| 11 | Reinicio del ESP32 | Desenchufar y volver a enchufar | Reconecta solo; comandos vuelven a funcionar |
| 12 | Distancia | Alejarse 5 / 10 / 20 m | Anotar latencia y % de lecturas recibidas |
| 13 | Varios remotos | 3 teléfonos conectados al central | Todos reciben; métrica "teléfonos conectados" |
| 14 | Reglas propias | Controles > Reglas: ventilador sobre 22 °C > Guardar; reiniciar el ESP32 | El ventilador se enciende sobre 22 °C y la regla sigue tras el reinicio; auditoría "Regla: …" OK |
| 15 | Regla inválida | Escribir ventana abierta del todo menor que el inicio | Error en el campo; Guardar deshabilitado |
| 16 | Cuenta en dos teléfonos | Crear cuenta en A; entrar con el mismo correo en B | B ve el mismo invernadero (modo *Desde la nube*) |
| 17 | Continuar con Google | Botón en Entrar | Entra; auditoría "Inicio de sesión con Google" OK |
| 18 | Recuperar contraseña | *¿Olvidaste tu contraseña?* con tu correo | Llega el enlace; con la clave nueva se entra |
| 19 | Subida del ESP32 | Vincular ESP32, cargar el sketch, esperar 5 min | En la consola de Firebase aparecen `estado` y `resumenes`; en Ajustes "Último dato… hace X min" |
| 20 | Respaldo nube | Modo WiFi; apagar el router de la casa con el teléfono en datos móviles | A los ~8 s Controles muestra *Mirando desde la nube*; los comandos se rechazan |
| 21 | Sin Internet en el ESP32 | Desconectar el router de Internet 20 min y volver a conectarlo | Los resúmenes de esos 20 min aparecen después (cola) |
| 22 | Pase en otro teléfono | Crear pase en A; en B pestaña Invitado | B entra; al revocar en A, B sale en ≤ 15 s |
| 23 | Reglas de la base | En el *Simulador de reglas* de Firebase, leer `/invernaderos/<id>` sin autenticar | Denegado |

Para el criterio de estabilidad, saca captura de **Historial > Ver registro de actividad > Estabilidad de la conexión** en cada escenario
(lecturas recibidas %, latencia promedio/máxima, cortes y % de comandos con éxito).

---

## 6. Acceso GreenBox (cuentas, recuperación e invitados)

| Función | Cómo funciona | Dónde |
|---|---|---|
| Entrar / Crear cuenta / Invitado | Pestañas en el acceso. Cuentas en **Firebase Authentication** con **correo y contraseña** o **Continuar con Google**. Al crear la cuenta se crea también su invernadero en la nube (`usuarios/{uid}` → `invernaderos/{id}`). Tras entrar se abre Monitoreo | `ui/acceso/PantallaAcceso.kt`, `nube/Nube.kt` |
| Recordarme | Casilla en Entrar y Crear cuenta; ver tabla de seguridad | `nube/Nube.kt` (`restaurar`) |
| ¿Olvidaste tu contraseña? | Firebase envía un enlace al correo para crear otra. El mensaje es el mismo exista o no la cuenta | `nube/Nube.kt` (`recuperar`) |
| Pases de visita | Cualquier cuenta genera una clave temporal `XXXX-XXXX-XXXX` (1 h, 8 h, 1 día o 3 días; "regar y ventilar" o "solo mirar"). Se guarda en la nube solo su SHA-256; máx. 5 pases abiertos; 5 claves erróneas cierran la puerta de visitas 5 min en ese teléfono; el invitado nunca recibe más permisos que su anfitrión; su sesión termina al vencer o al revocar el pase (≤ 15 s). **Sirve en el teléfono del invitado** (con Internet) | `nube/Nube.kt`, `ui/ajustes/SeccionesAcceso.kt` |
| Nube | Ajustes → Nube: último dato del ESP32 y **Vincular ESP32** (crea su cuenta y entrega las 5 líneas para el sketch) | `ui/ajustes/SeccionesAcceso.kt` |
| Historial y registro | Historial: evolución de humedad de la tierra (promedio o por planta), temperatura y humedad del aire en 1 hora / 24 horas / 7 días; junta las lecturas de este teléfono con los resúmenes de la nube, y muestra los avisos del ESP32. Botón "Ver registro de actividad": auditoría con filtros + estabilidad de la conexión | `ui/historial/` |

Las cuentas locales anteriores (nombre + semilla) ya no se usan: el código antiguo quedó en `_to_delete/`.
La base de datos local sigue en la versión 3 (no cambió su esquema; las tablas `usuarios` e `invitaciones` quedan sin uso).
Tipografía: Bricolage Grotesque en títulos y cifras grandes, e Instrument Sans (cifras tabulares) en cuerpo, etiquetas y datos; ambas con SIL Open Font License (`res/font/`, licencias en `licencias/`).
