# GreenBox — Manual de usuario

> Manual vivo: se actualiza cuando cambia algo que el usuario ve o que afecta la seguridad.
> Última actualización: 2026-11-12 · Versión de la app: 1.0 (base de datos v3)

GreenBox es una app Android para monitorear y controlar un invernadero conectado a un ESP32:
temperatura, humedad del suelo y del aire, ventilador y ventana. Funciona **sin Internet**: las
cuentas y el historial viven en el teléfono, y la conexión con el ESP32 es por WiFi local o Bluetooth.

---

## 1. Primeros pasos

1. **Abre la app.** Verás las pestañas **Entrar**, **Crear cuenta** e **Invitado**.
   Si aún no hay cuentas, *Entrar* te invita a crear la primera.
2. **Crea tu cuenta** (no existen contraseñas por defecto):
   - Nombre: de 3 a 30 caracteres (letras, números, espacios, punto o guion). Con él entrarás.
   - Contraseña: mínimo 8 caracteres, con letras y números, y sin contener el nombre.
3. **Guarda tu ID de cuenta y tu semilla de recuperación.** Cada cuenta recibe un ID (ej. `GB-0001`)
   y una semilla que se muestra una sola vez (ej. `QUILLAY-7K4P-QR2D-9XW3`). Anótalos en papel o en
   un gestor de contraseñas: sin la semilla no se puede recuperar la contraseña.
4. Al entrar se abre siempre **Monitoreo**. La app parte en modo **Simulador** para probar sin hardware;
   conecta el ESP32 cuando esté listo (ver sección 7).

---

## 2. Quién puede hacer qué

Por ahora GreenBox es **solo para usuarios**: no hay panel de administración. Cada cuenta puede ver
sus plantas, controlarlas, ver su evolución y el registro de actividad, configurar la conexión e
invitar a alguien.

| Quién | Ver plantas | Regar / ventilar | Historial y registro | Conexión | Invitar |
|---|---|---|---|---|---|
| **Usuario** (cuenta) | Sí | Sí | Sí | Sí | Sí |
| **Invitado** (pase de visita) | Sí | Según el pase | Sí | No | No |

> Las cuentas creadas con versiones anteriores siguen funcionando igual.

---

## 3. Acceso

La pantalla de acceso tiene tres pestañas: **Entrar**, **Crear cuenta** e **Invitado**.

### Entrar
Nombre y contraseña → *Entrar al invernadero*.
- Al entrar se abre siempre **Monitoreo**.
- **Recordarme en este teléfono:** al marcarlo no tendrás que entrar cada vez que abras la app.
  Dura **30 días desde el último uso** y se borra al tocar *Cerrar sesión*, al cambiar la contraseña
  o si se elimina la cuenta. Úsalo solo en tu propio teléfono.
- Tras **5 intentos fallidos** la cuenta queda bloqueada **5 minutos**.
- El mensaje de error es el mismo si falla el nombre o la contraseña (no revela qué cuentas existen).
- Sin *Recordarme*, la sesión se cierra sola tras **10 minutos** sin tocar la pantalla.

### Crear cuenta
Elige un nombre y una contraseña (mismas reglas que arriba); puedes marcar *Recordarme* →
*Crear mi cuenta*. Al terminar verás tu **ID de cuenta** y tu **semilla de recuperación**
(*Copiar ID y semilla*); marca *"La guardé en un lugar seguro"* para continuar.

### ¿Olvidaste tu contraseña?
En *Entrar*, toca **¿Olvidaste tu contraseña?**:
1. Escribe tu **nombre o tu ID de cuenta** (ej. `GB-0001`) y tu semilla (mayúsculas, minúsculas,
   espacios y guiones dan igual).
2. Escribe la contraseña nueva dos veces → *Restablecer contraseña*.
3. La semilla usada deja de servir y recibes **una nueva**: guárdala.

Los intentos fallidos cuentan para el mismo bloqueo de 5 intentos del login.

**¿Perdiste también la semilla?** Sin ella no es posible recuperar la contraseña. Si aún puedes
entrar, genera una semilla nueva en *Ajustes → Semilla de recuperación*.

### Invitado
Para quien recibió un **pase de visita**: escribe tu nombre y la clave temporal (`XXXX-XXXX`)
→ *Entrar de visita*. Ver sección 5.

---

## 4. Pantallas

### Monitoreo
Gemelo digital 3D del invernadero con los datos en vivo: arriba de la caja la temperatura, la
**humedad del aire dentro de la caja** (si el sensor la entrega), el ventilador y la ventana; abajo, en media luna,
la humedad de suelo de cada planta. Botones **Día / Tarde** cambian la paleta de toda la app.
Al tocar un dato se abre un panel con acciones rápidas (*Ventilar*, *Automático*, abrir/cerrar ventana)
si tu rol permite activar. El fondo muestra colinas con árboles, pinos, arbustos y pasto que se mecen con la brisa.

### Controles
- **Modo de control:** *Automático* (el ESP32 decide con tus reglas) o *Manual*.
- **Ventilador:** interruptor, deslizador de velocidad (PWM 0–255), botones Bajar / Subir (de a 25) / Máximo.
- **Ventana:** deslizador de apertura (0–90°), botones Abrir / Cerrar.
- **Reglas del modo automático:** cada cliente decide cuándo ventilar según su cultivo. Una barra de
  temperatura muestra de un vistazo las zonas (ventana cerrada / abriendo / abierta), dónde se enciende el
  ventilador y la temperatura de ahora. Puedes escribir cada valor o subirlo/bajarlo con las flechas:

  | Regla | Qué hace | Rango | De fábrica |
  |---|---|---|---|
  | Encender el ventilador sobre | El ventilador se enciende sobre esa temperatura (a toda potencia 10 °C más arriba) | 15–40 °C | 25 °C |
  | La ventana empieza a abrir en | Más frío que esto la ventana queda cerrada | 5–40 °C | 20 °C |
  | La ventana queda abierta del todo en | Entre ambos valores abre en proporción (al menos 2 °C más que el inicio) | 10–45 °C | 27 °C |
  | Ventilar por humedad del aire | Si el aire supera ese %, ventila suave y abre a medias aunque no haga calor (se puede desactivar) | 40–99 % | 85 % |

  **Guardar reglas** pide confirmación, envía solo lo que cambió y el ESP32 las guarda en su memoria:
  siguen funcionando aunque cierres la app o se reinicie. **Valores de fábrica** vuelve a los de la tabla.
  Los pases de visita pueden ver las reglas pero no cambiarlas. Si el ESP32 tiene el sketch antiguo,
  la sección pide cargar el sketch actualizado.

Cada orden y cada cambio de regla queda registrado a tu nombre. Si entraste con un pase *Solo mirar*,
la pantalla aparece bloqueada.

### Historial — *Así han crecido tus plantas*
- Elige el periodo: **1 hora**, **24 horas** o **7 días**.
- Si hay varias plantas (sensores), elige **Todas** (promedio) o una **Planta** en particular;
  cada opción muestra su humedad actual (ej. *Planta 2 · 41%*).
- Gráficas de **humedad de la tierra**, **temperatura** y **humedad del aire** (si el sensor la entrega).
  Cada una muestra el valor actual, cuánto cambió en el periodo, y abajo el **mínimo, promedio y máximo**.
  El eje de tiempo cubre siempre el periodo elegido completo: si la app lleva poco tiempo guardando datos,
  la parte sin datos aparece marcada como *sin registros*. La línea se corta donde no hubo lecturas.
  **Toca o desliza el dedo** sobre la gráfica para leer el valor exacto de ese momento.
  En temperatura, una franja marca el rango en que la ventana abre sola (según tus reglas); en humedad
  del aire, la franja marca desde dónde se ventila por humedad.
  Se guarda una lectura cada 30 s y se conservan 7 días.
- Botón **Ver registro de actividad** → pantalla aparte con:
  - **Estabilidad de la conexión** arriba: lecturas recibidas, latencia y cortes; **Detalle** muestra
    intervalos, fallas y comandos con éxito;
  - todo lo ocurrido, agrupado por día (*Hoy*, *Ayer*, fecha): entradas (logins), cierres de sesión,
    órdenes enviadas, visitas, recuperaciones y cambios de ajustes; filtros
    **Todo / Entradas / Órdenes / Visitas / Ajustes** con la cantidad de cada uno, y **Solo fallidos**
    para ver únicamente lo que falló (en rojo).

### Ajustes (para invitados se llama *Visita*)
- **Sesión:** tu nombre, **ID de cuenta** y si la sesión está recordada; *Cerrar sesión* (también
  olvida el *Recordarme*). Para invitados: hasta cuándo vale el pase y *Terminar visita*.
- **Conexión inalámbrica**: ver sección 7 (los invitados no la ven).
- **Abre la puerta a alguien:** crear pases de visita (sección 5).
- **Semilla de recuperación:** generar una nueva (invalida la anterior).

---

## 5. Pases de visita (invitados)

Para cuando no estás en casa y alguien de confianza debe cuidar el invernadero.

**Crear un pase** (Ajustes → *Abre la puerta a alguien*):
1. Elige la duración: **1 hora, 8 horas, 1 día o 3 días**.
2. Elige qué podrá hacer: **Regar y ventilar** (usa los Controles) o **Solo mirar**.
3. *Generar clave temporal* → aparece la clave `XXXX-XXXX`. Se muestra **una sola vez**:
   usa *Compartir* (WhatsApp, SMS, etc.) o *Copiar*.

**Reglas:**
- Máximo **5 pases abiertos** por persona.
- El invitado nunca recibe más permisos que quien lo invitó.
- **5 claves incorrectas** seguidas cierran la entrada de invitados por **5 minutos**.
- La visita termina sola al **vencer** el pase o al **revocarlo** (en menos de 15 segundos).
- En *Tus pases abiertos* ves si alguien ya lo usó y puedes **Revocar**.
- Todo queda en auditoría con el nombre del visitante marcado como *(invitado)*.

> **Importante:** el pase abre GreenBox **en el dispositivo donde se creó**. Créalo en el teléfono o
> tablet que se queda en casa / en el invernadero.

---

## 6. Seguridad (resumen)

| Medida | Detalle |
|---|---|
| Contraseñas y semillas | Nunca se guardan: solo un hash PBKDF2-HMAC-SHA256 (120.000 iteraciones, sal aleatoria) |
| Claves de visita | Solo se guarda su SHA-256; se muestran una sola vez |
| Fuerza bruta | 5 intentos → bloqueo de 5 min (login y recuperación comparten contador; invitados tienen el suyo) |
| Sesiones | En memoria, se cierran tras 10 min de inactividad o al vencer/revocar un pase |
| Recordarme | Token aleatorio de 256 bits cifrado con el Android Keystore (no sirve en otro teléfono); en la base solo su SHA-256; vence a los 30 días sin uso; se invalida al cerrar sesión, cambiar la contraseña o eliminar la cuenta. Con *Recordarme* no aplica el cierre por inactividad |
| Mínimo privilegio | Usuario (cuenta) e Invitado (pase temporal con permiso de mirar o de regar/ventilar); los invitados no ven la conexión ni pueden invitar |
| Datos en el teléfono | Ajustes y clave compartida cifrados con AES-256-GCM (Android Keystore); sin respaldo en la nube |
| Portapapeles | Semillas, claves de visita y contraseñas temporales se copian marcadas como sensibles (Android 13+) |
| Comunicación con el ESP32 | Respuestas y comandos firmados con HMAC-SHA256 + nonce de un solo uso; lista blanca de comandos |
| Entre teléfonos | Mensajes cifrados AES-256-GCM, rechazo de repetidos y de mensajes con más de 60 s |
| Auditoría | Entradas, cierres, comandos, cambios de ajustes, cuentas, visitas y recuperaciones |

Detalle técnico y referencias ISO 27001 / 27400 / IEC 62443: ver `LEEME.md`.

---

## 7. Conexión con el invernadero

Ajustes → **Conexión inalámbrica** (cualquier cuenta; los invitados no):

| Modo | Cuándo usarlo |
|---|---|
| **Simulador** | Pruebas sin hardware (1 a 8 sensores simulados) |
| **WiFi → ESP32** | Teléfono y ESP32 en la misma red; escribe la IP fija del ESP32 |
| **Bluetooth → ESP32** | Sin router, a menos de ~10 m; empareja antes el ESP32 en Ajustes del teléfono |
| **Otro teléfono (central)** | Este teléfono recibe los datos de otro que está conectado al ESP32 |

- La **clave compartida** (mín. 12 caracteres) debe ser igual en el ESP32 (`SECRETO`) y en todos los teléfonos.
- **Compartir con otros teléfonos** convierte este teléfono en *central* (máx. 5 remotos). Los remotos
  pueden usar *Buscar en la red* para encontrarlo.
- *Guardar y conectar* aplica los cambios.

---

## 8. Problemas frecuentes

| Problema | Qué hacer |
|---|---|
| "Demasiados intentos" | Espera 5 minutos |
| Olvidé la contraseña | *¿Olvidaste tu contraseña?* con tu nombre o ID de cuenta y tu semilla (sin semilla no se puede recuperar) |
| Entré con *Recordarme* en un teléfono ajeno | Abre la app → Ajustes → *Cerrar sesión* (borra lo recordado), o cambia tu contraseña |
| La clave de visita no funciona | Revisa que no haya vencido ni sido revocada, y que estés en el dispositivo donde se creó |
| "Sin datos" / cortes | Revisa WiFi o Bluetooth; la app reconecta sola. El ESP32 sigue en automático si se pierde la conexión |
| "Firma inválida" | La clave compartida no coincide entre la app y el ESP32 |
| No puedo activar nada | Entraste con un pase *Solo mirar*, o es una cuenta antigua de *Solo lectura* |
| Se cerró la sesión | Pasaron 10 min sin uso (sin *Recordarme*), o venció / revocaron tu pase de visita |

---

## 9. Limitaciones conocidas

- Las cuentas y pases son **por dispositivo** (no se sincronizan entre teléfonos).
- El ESP32 no usa HTTPS; se compensa con firmas HMAC y nonce, pero las lecturas viajan legibles en la red local.
- El puerto USB del ESP32 acepta comandos sin firma (requiere acceso físico, para mantenimiento).

---

## Historial de cambios del manual

| Fecha | Cambio |
|---|---|
| 2026-11-12 | Primera versión: acceso GreenBox (entrar, crear cuenta, recuperación con semilla, pases de visita), panel Raíces del administrador, roles, pantallas, conexión y seguridad |
| 2026-11-12 | El acceso abre siempre con Entrar / Crear cuenta / Invitado (la primera cuenta es la del administrador); campo "Nombre" con espacios; ID de cuenta (GB-0001) para recuperar; "Recordarme en este teléfono" |
| 2026-11-12 | GreenBox solo para usuarios: se quita el panel Raíces y la administración; al entrar se abre Monitoreo; Historial muestra la evolución de las plantas por periodo y por planta; el registro de actividad pasa a una pantalla aparte (botón en Historial); cualquier cuenta configura la conexión |
| 2026-11-12 | Rediseño visual: nueva tipografía; Historial con periodos 1 h / 24 h / 7 días, humedad por planta en el filtro, gráficas con ejes, resumen y lectura al tocar; Registro agrupado por día, con conteos, filtro "Solo fallidos" y estabilidad resumida arriba |
| 2026-11-12 | Monitoreo: la humedad del aire tiene su propia tarjeta; las plantas se muestran en media luna bajo la caja |
| 2026-11-12 | "Activador" pasa a llamarse **Controles**; se quita el comando manual; nuevas **reglas del modo automático** definidas por el cliente (guardadas en el ESP32); el periodo del Historial ahora cambia el eje de las gráficas; vegetación en el fondo de Monitoreo |
