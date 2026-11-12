# Invernadero IoT — App Android (TI3042 Unidad 2)

> Este proyecto ya tiene todo copiado y compila (`BUILD SUCCESSFUL`). Los pasos de abajo sirven
> si algún día necesitas armarlo de nuevo en otro computador. El sketch del ESP32 está en `esp32/`.

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
│   ├── BaseDatos.kt           ← SQLite: usuarios, lecturas, auditoría
│   ├── UsuariosRepo.kt        ← login, bloqueo por intentos, roles
│   ├── Sesion.kt              ← sesión en memoria con expiración
│   └── AjustesRepo.kt         ← modo de conexión, IPs, clave compartida
├── conexion/
│   ├── Modelos.kt             ← LecturaEsp32, ParametrosControl (reglas), lista blanca de comandos
│   ├── SimuladorEsp32.kt      ← pruebas sin hardware
│   ├── Esp32Wifi.kt           ← HTTP firmado con el ESP32
│   ├── Esp32Bluetooth.kt      ← Bluetooth SPP firmado con el ESP32
│   ├── Interconexion.kt       ← teléfono central ↔ teléfonos remotos (TCP cifrado + NSD)
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

6. **Ejecuta** ▶. La primera vez pide crear una cuenta (no hay contraseñas por defecto).
   Empieza en modo **Simulador** para probar todo sin hardware.

### ESP32
Abre `esp32/invernadero_esp32/invernadero_esp32.ino` y cambia `WIFI_SSID`, `WIFI_PASSWORD`, `SECRETO`
y la IP fija (debe ser libre en tu red; la de `network_security_config.xml` debe ser la misma).
En la app: *Ajustes > WiFi → ESP32*, misma IP y la **misma clave compartida** que `SECRETO`.

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
| Login con usuario y contraseña | `ui/acceso`, `datos/UsuariosRepo.kt` (SQLite local) |
| Pantallas Monitoreo y Controles, Material Design | Monitoreo (caja 3D + datos), Controles (RadioButtons, Switch, Sliders, botones, campos de texto numéricos con validación para las reglas del modo automático, diálogo de confirmación, Snackbar) |
| Conexiones inalámbricas eficientes y seguras | WiFi (HTTP firmado), Bluetooth SPP (enlace emparejado + firma), reconexión automática, timeouts, polling de 2 s |
| Uso innovador de la conexión | Gemelo digital 3D en vivo, teléfono central que comparte con remotos, descubrimiento automático NSD |
| Seguridad ISO 27001 / 27400 | Ver sección 3 |
| Lineamientos OT documentados | Ver sección 4 |
| Interconexión entre apps móviles | Modo central/remoto (`conexion/Interconexion.kt`) |
| Estabilidad y eficiencia en escenarios | Historial > *Ver registro de actividad* > *Estabilidad de la conexión* + sección 5 |
| Almacenamiento y consulta de datos | SQLite: lecturas cada 30 s (7 días), gráficas en Historial |
| Permisos | `AndroidManifest.xml` + permiso de Bluetooth pedido en tiempo de ejecución (Ajustes) |

---

## 3. Justificación de decisiones

**Herramientas** — Kotlin + Jetpack Compose + Material 3 (estándar actual recomendado por Google),
Navigation Compose para la navegación, corrutinas/Flow para trabajo en segundo plano sin bloquear la UI.
Solo APIs nativas de Android para SQLite, cifrado, Bluetooth, sockets y NSD: menos dependencias = menos
superficie de ataque y más fácil de mantener.

**Autenticación: SQLite local** — el invernadero debe poder operarse sin Internet (zona rural / red OT
aislada). Firebase obligaría a depender de la nube; una API MySQL exige un servidor. Contrapartida: los
usuarios son por teléfono (se documenta como mejora futura sincronizar con un backend).

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

---

## 4. Seguridad (ISO/IEC 27001 Anexo A, ISO/IEC 27400 IoT, IEC 62443 OT)

| Medida | Implementación | Referencia |
|---|---|---|
| Sin contraseñas por defecto | No existe ninguna cuenta hasta que el usuario crea la suya | 27400 (credenciales únicas), IEC 62443 CR 1.5 |
| Contraseñas protegidas | PBKDF2-HMAC-SHA256, 120.000 iteraciones, sal aleatoria; nunca en texto plano | 27001 A.8.5 / A.5.17 |
| Política de contraseñas | Mín. 8, letras y números, no contiene el usuario | 27001 A.5.17 |
| Bloqueo por fuerza bruta | 5 intentos fallidos → 5 min bloqueada; mismo mensaje para usuario o clave incorrectos | 27001 A.8.5, IEC 62443 CR 1.11 |
| Control de acceso | Cuentas de usuario e invitados con pase temporal ("solo mirar" o "regar y ventilar"); el ESP32 solo recibe órdenes permitidas | 27001 A.5.15 / A.8.2, IEC 62443 CR 2.1 |
| Cierre de sesión por inactividad | 10 min (salvo "Recordarme", que el usuario elige en su propio teléfono) | 27001 A.8.5, IEC 62443 CR 2.6 |
| "Recordarme en este teléfono" | Token de 256 bits cifrado en Android Keystore, solo SHA-256 en la base, 30 días desde el último uso, se invalida al cerrar sesión o cambiar la contraseña | 27001 A.8.5 |
| Cifrado de datos sensibles en el teléfono | Ajustes y clave compartida cifrados con AES-256-GCM en Android Keystore | 27001 A.8.24 |
| Sin respaldo en la nube | `allowBackup=false`, `data_extraction_rules` | 27001 A.8.10 / A.8.12 |
| Integridad de los datos del sensor | Cada respuesta del ESP32 firmada con HMAC-SHA256 | 27400, IEC 62443 CR 3.1 |
| Autenticidad de comandos y anti-repetición | Firma HMAC(comando + nonce), nonce de un solo uso | IEC 62443 CR 3.1 / CR 1.2 |
| Comunicación entre teléfonos cifrada | AES-256-GCM, id y hora en cada mensaje, rechazo de repetidos y viejos | 27001 A.8.20 / A.8.24 |
| Tráfico en claro limitado | `network_security_config`: HTTP solo hacia la IP del ESP32 | 27001 A.8.20 |
| Validación de entradas | Lista blanca de comandos en app y ESP32; rangos validados (también las reglas T/L/H/U y que la ventana abra al menos 2 °C después de donde empieza); consultas SQL con parámetros | 27001 A.8.28 |
| Cambio de reglas del modo automático | Solo cuentas con permiso de activar (los pases de visita no pueden), confirmación previa, firmadas como cualquier comando, guardadas en la memoria del ESP32 y auditadas | 27001 A.8.9 / A.8.15, IEC 62443 CR 2.1 |
| Protección ante saturación | Máx. 5 teléfonos remotos; ESP32 bloquea 60 s tras 5 firmas inválidas | IEC 62443 CR 7.1 |
| Registro de auditoría | Login, cierre, comandos (quién, desde dónde, resultado), cambios de ajustes y usuarios | 27001 A.8.15, IEC 62443 CR 2.8 |
| Minimización de datos | Historial de 7 días; la clave compartida nunca se registra | 27001 A.5.34 |
| Operación segura ante fallas (OT) | Si se pierde la conexión el ESP32 sigue en automático con su lógica local y las reglas guardadas | IEC 62443 CR 7.6 |
| Separación de redes (OT) | ESP32 en red local, sin exposición a Internet | IEC 62443 (zonas y conductos) |

**Limitaciones conocidas** (decirlo da puntos en la defensa): el ESP32 no usa HTTPS (sin certificado); se
compensa con firmas HMAC y nonce, pero los datos de sensores viajan legibles en la red local. El puerto
USB acepta comandos sin firma porque requiere acceso físico (mantenimiento).

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

Para el criterio de estabilidad, saca captura de **Historial > Ver registro de actividad > Estabilidad de la conexión** en cada escenario
(lecturas recibidas %, latencia promedio/máxima, cortes y % de comandos con éxito).

---

## 6. Acceso GreenBox (cuentas, recuperación e invitados)

| Función | Cómo funciona | Dónde |
|---|---|---|
| Entrar / Crear cuenta / Invitado | Pestañas en el acceso desde la primera apertura. Por ahora la app es **solo para usuarios**: toda cuenta nueva puede ver y controlar sus plantas. Se entra con **nombre** (admite espacios) y cada cuenta tiene un **ID** `GB-0001`. Tras entrar se abre Monitoreo |
| Recordarme | Casilla en Entrar y Crear cuenta; ver tabla de seguridad | `datos/SesionRecordada.kt` | `ui/acceso/PantallaAcceso.kt` |
| ¿Olvidaste tu contraseña? | Al crear la cuenta se entrega una **semilla de recuperación** (`QUILLAY-7K4P-QR2D-9XW3`) que se muestra una sola vez y se guarda como hash PBKDF2. Nombre o ID de cuenta + semilla → contraseña nueva, y se entrega una semilla nueva. Comparte el bloqueo de 5 intentos del login. Funciona sin Internet ni correo | `datos/UsuariosRepo.kt` |
| Sin semilla | No se puede recuperar la contraseña; mientras se pueda entrar, se genera otra semilla en Ajustes | `ui/ajustes/SeccionesAcceso.kt` |
| Pases de visita | Cualquier cuenta genera una clave temporal `XXXX-XXXX` (1 h, 8 h, 1 día o 3 días; "regar y ventilar" o "solo mirar"). Solo se guarda su SHA-256; máx. 5 pases abiertos por persona; 5 claves erróneas cierran la puerta de visitas 5 min; el invitado nunca recibe más permisos que su anfitrión; su sesión termina al vencer o al revocar el pase (≤ 15 s). El pase abre GreenBox en el dispositivo donde se creó | `datos/InvitacionesRepo.kt` |
| Historial y registro | Historial: evolución de humedad de la tierra (promedio o por planta), temperatura y humedad del aire en 1 hora / 24 horas / 7 días (el eje cubre el periodo completo). Botón "Ver registro de actividad": auditoría con filtros + estabilidad de la conexión | `ui/historial/` |

La base de datos va en la versión 3 (`onUpgrade` agrega las columnas de la semilla, la tabla `invitaciones` y las columnas de "Recordarme" sin borrar nada).
Tipografía: Bricolage Grotesque en títulos y cifras grandes, e Instrument Sans (cifras tabulares) en cuerpo, etiquetas y datos; ambas con SIL Open Font License (`res/font/`, licencias en `licencias/`).
