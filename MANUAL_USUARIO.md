# GreenBox — Manual de usuario

> Manual vivo: se actualiza cuando cambia algo que el usuario ve o que afecta la seguridad.
> Última actualización: 2026-11-13 · Versión de la app: 1.0 (base de datos v3)

GreenBox es una app Android para monitorear y controlar un invernadero conectado a un ESP32:
temperatura, humedad del suelo y del aire, ventilador y ventana. Funciona de forma **híbrida**:
- **En el invernadero** (misma red WiFi o Bluetooth) ves todo en tiempo real, cada 2 segundos, y puedes
  mover el ventilador y la ventana. Esto funciona aunque no haya Internet.
- **Desde lejos** ves lo que el ESP32 sube a la nube (Firebase): el estado cada 2 minutos y un resumen cada 5.
  Tu cuenta está en la nube, así que entras con ella en cualquier teléfono.

---

## 1. Primeros pasos

1. **Abre la app.** Verás las pestañas **Entrar**, **Crear cuenta** e **Invitado**.
2. **Crea tu cuenta** (no existen contraseñas por defecto), de una de dos formas:
   - **Con correo:** tu nombre (3 a 30 caracteres), tu correo y una contraseña de mínimo 8 caracteres,
     con letras y números, que no contenga tu nombre. Te llegará un correo para confirmar tu dirección.
   - **Continuar con Google:** eliges tu cuenta de Google y listo.
   La primera vez necesitas Internet. Al crear la cuenta se crea también **tu invernadero en la nube**.
3. Al entrar se abre siempre **Monitoreo**. Mientras no configures el ESP32 en este teléfono, la app muestra
   el **Simulador** (datos de demostración) y avisa arriba: *Datos de demostración: aún no configuras tu ESP32*
   con el botón **Configurar**, que lleva a Ajustes (ver sección 7).
4. **En otro teléfono** entra con la misma cuenta y elige **WiFi + nube** en Ajustes: lejos del invernadero
   verás lo que el ESP32 sube a la nube.

---

## 2. Quién puede hacer qué

Por ahora GreenBox es **solo para usuarios**: no hay panel de administración. Cada cuenta puede ver
sus plantas, controlarlas, ver su evolución y el registro de actividad, configurar la conexión e
invitar a alguien.

| Quién | Ver plantas | Regar / ventilar | Historial y registro | Conexión | Invitar |
|---|---|---|---|---|---|
| **Usuario** (cuenta) | Sí | Sí | Sí | Sí | Sí |
| **Invitado** (pase de visita) | Sí | Según el pase (solo en la red del invernadero) | Sí | No | No |

> Las cuentas locales de versiones anteriores (nombre + semilla) ya no sirven para entrar: crea tu cuenta
> con correo o con Google. El historial guardado en el teléfono se conserva.

---

## 3. Acceso

La pantalla de acceso tiene tres pestañas: **Entrar**, **Crear cuenta** e **Invitado**.

### Entrar
Correo y contraseña → *Entrar al invernadero*, o **Continuar con Google**.
- Al entrar se abre siempre **Monitoreo**.
- **Recordarme en este teléfono:** al marcarlo no tendrás que entrar cada vez que abras la app (también
  funciona sin Internet). Dura **30 días desde el último uso** y se borra al tocar *Cerrar sesión*.
  Úsalo solo en tu propio teléfono.
- Tras **5 intentos fallidos** en este teléfono hay que esperar **5 minutos** (además, Firebase frena los
  intentos excesivos desde su lado).
- El mensaje de error es el mismo si falla el correo o la contraseña (no revela qué cuentas existen).
- Sin *Recordarme*, la sesión se cierra sola tras **10 minutos** sin tocar la pantalla.

### Crear cuenta
Tu nombre, correo y contraseña (dos veces); puedes marcar *Recordarme* → *Crear mi cuenta*.
También puedes usar **Continuar con Google**.

### ¿Olvidaste tu contraseña?
En *Entrar*, toca **¿Olvidaste tu contraseña?**, escribe tu correo → *Enviar enlace*. Te llegará un correo
con un enlace para crear una contraseña nueva (revisa también *spam*). Por seguridad, el aviso es el mismo
aunque ese correo no tenga cuenta. Si entras con Google, tu contraseña es la de Google.

### Invitado
Para quien recibió un **pase de visita**: escribe tu nombre y la clave temporal (`XXXX-XXXX-XXXX`)
→ *Entrar de visita*. Necesitas Internet. Ver sección 5.

---

## 4. Pantallas

### Monitoreo
Gemelo digital 3D del invernadero (la caja va al centro y deja ver el paisaje) con los datos en vivo en tarjetas: temperatura, **humedad del aire dentro
de la caja** (si el sensor la entrega), ventilador y ventana. Botones **Día / Tarde** cambian la paleta de toda la app.
- **Toca una tarjeta** (o su punto en la caja) para abrir un panel con acciones rápidas (*Ventilar*, *Automático*,
  abrir/cerrar ventana) si tu rol permite activar.
- **Mantén presionada una tarjeta** (medio segundo, el teléfono vibra) y arrástrala para dejarla donde
  prefieras; así un roce no la cambia de lugar. La línea que la une a la caja rodea las otras tarjetas en vez
  de pasar por encima. Las posiciones se guardan en este teléfono. Para quien no lo sepa, Monitoreo lo
  muestra solo: una tarjeta se "levanta" y se desplaza con el aviso *Mantén presionada una tarjeta para
  moverla*. Aparece las primeras 4 veces mientras no hayas movido ninguna y después de vez en cuando
  (una de cada 10 veces que abres Monitoreo); se va sola a los pocos segundos o al tocar la pantalla.
- Los paneles que se abren son un poco translúcidos, para no perder de vista la escena.
- **La caja se ve siempre**, aunque no haya datos: mientras se conecta, las tarjetas dicen *Cargando…*; si la
  conexión falla dicen *Error*.
- **Aviso bajo la barra superior** (solo cuando hace falta): *Datos de demostración / Modo demostración*
  (botón **Configurar** o **Cambiar**), *Mirando desde la nube · hace X min*, *Error: …* con el motivo, o
  *Sin conexión con el ESP32* (lo que ves puede estar desactualizado).
- **Hoja con número** (esquina inferior izquierda): cuántas plantas hay. Al tocarla se ve cada planta con su
  humedad de tierra y su estado, y con el lápiz le pones **nombre** (máx. 24 letras; ej. *Tomate cherry*).
  El nombre se guarda en este teléfono, queda en el registro de actividad y aparece en el Historial.
  Los pases de visita no pueden renombrar.
- **Botón +** (junto a la hoja): **añadir o quitar plantas**. *Añadir planta* reserva su lugar (en la caja se ve
  un brote con un círculo punteado y en la lista dice *Esperando sensor*); cuando conectas su sensor al ESP32 y
  este lo detecta, la planta aparece sola con su humedad. Con el basurero quitas una planta: si aún no tenía
  sensor desaparece; si ya tenía, deja de mostrarse pero su sensor sigue conectado y puedes usar
  *Volver a mostrar*. Máximo 12 plantas. Cada cambio queda en el registro de actividad. Los pases de visita
  no pueden añadir ni quitar plantas.
- Los controles responden al instante: muestran lo que pediste mientras el ESP32 lo confirma (unos 2 s).

El fondo cubre toda la pantalla, también detrás de la barra de estado y la cámara, con colinas, árboles,
pinos, arbustos y pasto que se mecen con la brisa. Por las colinas, de lejos y de cerca, pasean ciervos, zorros,
conejos, vacas y ovejas (de vez en cuando se detienen a pastar, olfatear o mirar) y por el cielo cruzan bandadas de aves. Es solo
decorativo: no depende de los datos del invernadero.

**Con el teléfono en horizontal** (o en tablet) el menú pasa a un riel a la izquierda, la caja queda al centro
con las tarjetas a los costados y el paisaje se extiende a lo ancho sin deformarse.
El estado de la conexión se ve en Controles y en Ajustes.

### Controles
- **Modo de control:** *Automático* (el ESP32 decide con tus reglas) o *Manual*.
- **Ventilador:** interruptor, deslizador de velocidad (0–100 %), botones Bajar / Subir (≈10 % cada vez) / Máximo.
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

**Mirando desde la nube:** si no hay conexión local (estás lejos, o se cayó el WiFi del invernadero) aparece
este aviso con la hora del último dato. Desde la nube **solo se mira**: para mover el ventilador o la ventana
tienes que estar en la red del invernadero (WiFi o Bluetooth). Las órdenes nunca viajan por Internet.

### Historial — *Así han crecido tus plantas*
- Elige el periodo: **1 hora**, **24 horas** o **7 días**.
- Si hay varias plantas (sensores), elige **Todas** (promedio) o una planta en particular, por su nombre;
  cada opción muestra su humedad actual (ej. *Tomate cherry · 41%*).
- Gráficas de **humedad de la tierra**, **temperatura** y **humedad del aire** (si el sensor la entrega).
  Cada una muestra el valor actual, cuánto cambió en el periodo, y abajo el **mínimo, promedio y máximo**.
  El eje de tiempo cubre siempre el periodo elegido completo: si la app lleva poco tiempo guardando datos,
  la parte sin datos aparece marcada como *sin registros*. La línea se corta donde no hubo lecturas.
  **Toca o desliza el dedo** sobre la gráfica para leer el valor exacto de ese momento.
  En temperatura, una franja marca el rango en que la ventana abre sola (según tus reglas); en humedad
  del aire, la franja marca desde dónde se ventila por humedad.
  Este teléfono guarda una lectura cada 30 s (se conservan 7 días). Lo que falte (por ejemplo si el
  teléfono estuvo lejos, o si es otro teléfono tuyo) se completa con los **resúmenes de 5 minutos** que
  el ESP32 sube a la nube (se conservan 30 días).
- **Avisos del invernadero** (arriba de las gráficas, si los hay): lo que el ESP32 avisó en los últimos 7 días,
  por ejemplo *Hace calor*, *Hace frío*, *La tierra está seca*, *El sensor no responde* o que se reinició.
- Botón **Ver registro de actividad** → pantalla aparte con:
  - **Estabilidad de la conexión** arriba: lecturas recibidas, latencia y cortes; **Detalle** muestra
    intervalos, fallas y comandos con éxito;
  - todo lo ocurrido, agrupado por día (*Hoy*, *Ayer*, fecha): entradas (logins), cierres de sesión,
    órdenes enviadas, visitas, recuperaciones y cambios de ajustes; filtros
    **Todo / Entradas / Órdenes / Visitas / Ajustes** (incluye lo de la nube) con la cantidad de cada uno, y **Solo fallidos**
    para ver únicamente lo que falló (en rojo).

### Ajustes (para invitados se llama *Visita*)
- **Sesión:** tu nombre, tu **correo** y si la sesión está recordada; *Cerrar sesión* (también
  olvida el *Recordarme*). Para invitados: hasta cuándo vale el pase y *Terminar visita*.
- **Conexión inalámbrica**: ver sección 7 (los invitados no la ven).
- **Nube:** cuándo llegó el último dato del ESP32 y **Vincular ESP32** (sección 7).
- **Abre la puerta a alguien:** crear pases de visita (sección 5).

---

## 5. Pases de visita (invitados)

Para cuando no estás en casa y alguien de confianza debe cuidar el invernadero.

**Crear un pase** (Ajustes → *Abre la puerta a alguien*):
1. Elige la duración: **1 hora, 8 horas, 1 día o 3 días**.
2. Elige qué podrá hacer: **Regar y ventilar** (usa los Controles) o **Solo mirar**.
3. *Generar clave temporal* → aparece la clave `XXXX-XXXX-XXXX`. Se muestra **una sola vez**:
   usa *Compartir* (WhatsApp, SMS, etc.) o *Copiar*.

**El invitado** abre GreenBox **en su propio teléfono**, toca *Invitado*, escribe su nombre y la clave
(necesita Internet). Verá el invernadero desde la nube. Si además está en la red del invernadero en un
teléfono con la conexión ya configurada, con *Regar y ventilar* también puede usar los Controles.

**Reglas:**
- Máximo **5 pases abiertos** por persona.
- El invitado nunca recibe más permisos que quien lo invitó.
- **5 claves incorrectas** seguidas cierran la entrada de invitados por **5 minutos** en ese teléfono.
- La visita termina sola al **vencer** el pase o al **revocarlo** (en menos de 15 segundos).
- En *Tus pases abiertos* ves si alguien ya lo usó (y cuántas veces) y puedes **Revocar**.
- Todo queda en auditoría con el nombre del visitante marcado como *(invitado)*.

---

## 6. Seguridad (resumen)

| Medida | Detalle |
|---|---|
| Contraseñas | Las guarda Firebase Authentication (Google); nunca se guardan en el teléfono ni en la base de la app. Recuperación solo por enlace al correo |
| Inicio con Google | Por Credential Manager de Android; GreenBox nunca ve tu contraseña de Google |
| Claves de visita | 12 caracteres; en la nube solo se guarda su SHA-256; se muestran una sola vez |
| Fuerza bruta | 5 intentos → espera de 5 min en el teléfono (también para invitados), más el límite de Firebase |
| Sesiones | Se cierran tras 10 min de inactividad (sin *Recordarme*) o al vencer/revocar un pase; la identidad temporal del invitado se borra al terminar |
| Recordarme | Solo si lo marcas; 30 días desde el último uso; se borra al cerrar sesión |
| Reglas de la nube | Cada cuenta solo ve su invernadero; el ESP32 tiene su propia cuenta y solo puede subir su estado, resúmenes y avisos; se validan tipos y rangos de cada dato |
| Mínimo privilegio | Usuario (cuenta) e Invitado (pase temporal con permiso de mirar o de regar/ventilar); los invitados no ven la conexión ni pueden invitar |
| Datos en el teléfono | Ajustes y clave compartida cifrados con AES-256-GCM (Android Keystore); sin respaldo automático de Android |
| Datos en la nube | Solo resúmenes cada 5 min y el último estado; lo de más de 30 días se borra solo |
| Portapapeles | Claves de visita y credenciales del ESP32 se copian marcadas como sensibles (Android 13+) |
| Comunicación con el ESP32 | En la red local: respuestas y comandos firmados con HMAC-SHA256 + nonce de un solo uso; lista blanca de comandos. Hacia la nube: HTTPS verificado con los certificados de Google |
| Órdenes | Solo por la red local; nunca por la nube |
| Entre teléfonos | Mensajes cifrados AES-256-GCM, rechazo de repetidos y de mensajes con más de 60 s |
| Auditoría | Entradas, cierres, comandos, cambios de ajustes, visitas, pases y vinculación del ESP32; copia en la nube que no se puede editar ni borrar |

Detalle técnico y referencias ISO 27001 / 27400 / IEC 62443: ver `LEEME.md`.

---

## 7. Conexión con el invernadero

Ajustes → **Conexión inalámbrica** (cualquier cuenta; los invitados no):

| Modo (en este orden) | Cuándo usarlo |
|---|---|
| **WiFi + nube** | El modo normal. En la red del invernadero: tiempo real (cada 2 s) y controles. Lejos: lo que el ESP32 sube a Firebase (cada 2 min), solo mirar. Escribe la IP fija del ESP32 |
| **Bluetooth → ESP32** | Sin router, a menos de ~10 m; empareja antes el ESP32 en Ajustes del teléfono. Si se corta, también muestra la nube |
| **Otro teléfono (central)** | Un segundo teléfono en la misma red WiFi recibe los datos de otro que ya está conectado al ESP32 (útil con Bluetooth, que acepta un solo teléfono a la vez) |
| **Simulador (demostración)** | Datos de prueba, sin ESP32 (1 a 8 sensores). Es el modo inicial hasta que configures tu ESP32 |

Los invitados que entran desde su propio teléfono no configuran nada: ven lo que hay en la nube.

- La **clave compartida** (mín. 12 caracteres) debe ser igual en el ESP32 (`SECRETO`) y en todos los teléfonos.
- **Compartir con otros teléfonos** convierte este teléfono en *central* (máx. 5 remotos). Los remotos
  pueden usar *Buscar en la red* para encontrarlo.
- *Guardar y conectar* aplica los cambios.
- Con WiFi, Bluetooth u *Otro teléfono*, si no llegan datos locales por ~8 s la app muestra sola lo último de la nube
  y vuelve al tiempo real apenas se recupera la conexión.

### Nube (Ajustes → Nube)
- Muestra cuándo llegó el **último dato del ESP32** a la nube.
- **Vincular ESP32:** crea una cuenta solo para tu ESP32 y muestra **una vez** 5 líneas (*Copiar*) que se pegan
  en la sección `NUBE` del sketch antes de cargarlo. El ESP32 sube el estado cada 2 min, un resumen cada 5 min
  y avisos cuando pasa algo; sin Internet guarda hasta 2 horas de resúmenes y los sube al volver.
- **Generar credenciales nuevas** (si ya estaba vinculado) deja sin acceso al ESP32 anterior hasta que cargues
  el sketch con las nuevas.

---

## 8. Problemas frecuentes

| Problema | Qué hacer |
|---|---|
| "Demasiados intentos" | Espera 5 minutos |
| Olvidé la contraseña | *¿Olvidaste tu contraseña?* con tu correo; revisa también *spam* |
| "Sin Internet: para entrar necesitas conexión" | La primera vez en un teléfono hace falta Internet; después, con *Recordarme*, entra sin conexión |
| *Continuar con Google* no funciona | Falta activar Google o la huella SHA-1 en Firebase (ver `LEEME.md`, sección 1.1) |
| Entré con *Recordarme* en un teléfono ajeno | Abre la app → Ajustes → *Cerrar sesión*, o cambia tu contraseña |
| La clave de visita no funciona | Revisa que tenga 12 caracteres, que no haya vencido ni sido revocada, y que haya Internet |
| "Mirando desde la nube" | No hay conexión local con el ESP32: revisa WiFi o Bluetooth. Desde la nube no se puede activar nada |
| "El ESP32 aún no sube datos a la nube" / dato muy antiguo | Vincula el ESP32 (Ajustes → Nube), pega las 5 líneas en el sketch y revisa que el WiFi del ESP32 tenga Internet. En el Monitor Serie debe decir `NUBE: sesión iniciada` |
| "La nube no dio permiso" | Falta publicar las reglas de `firebase/database.rules.json` en la Realtime Database |
| "Sin datos" / cortes | Revisa WiFi o Bluetooth; la app reconecta sola. El ESP32 sigue en automático si se pierde la conexión |
| "Firma inválida" | La clave compartida no coincide entre la app y el ESP32 |
| No puedo activar nada | Entraste con un pase *Solo mirar*, o estás mirando desde la nube |
| Se cerró la sesión | Pasaron 10 min sin uso (sin *Recordarme*), o venció / revocaron tu pase de visita |

---

## 9. Limitaciones conocidas

- Desde la nube **solo se mira**: las órdenes al ESP32 requieren estar en su red local.
- La primera vez que entras en un teléfono (y para entrar de visita) se necesita Internet.
- En la nube los datos llegan cada 2 min (estado) y cada 5 min (resumen), no en tiempo real.
- Los avisos del ESP32 se ven en Historial; no llegan como notificación al teléfono.
- En la red local el ESP32 no usa HTTPS; se compensa con firmas HMAC y nonce, pero las lecturas viajan legibles en esa red
  (hacia la nube sí van cifradas).
- El puerto USB del ESP32 acepta comandos sin firma (requiere acceso físico, para mantenimiento).
- Los nombres de las plantas y la posición de las tarjetas se guardan en cada teléfono (no en la nube).
- Añadir o quitar plantas solo cambia lo que muestra este teléfono: el sensor hay que conectarlo físicamente al
  ESP32, y una planta quitada sigue registrándose en el Historial mientras su sensor esté conectado.

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
| 2026-11-12 | Monitoreo: tarjetas que se pueden arrastrar (las líneas rodean las otras) y botón para ordenarlas; hoja con la cantidad de plantas en vez de las tarjetas de humedad de suelo; nombres para cada planta (también en Historial); fondo a pantalla completa; caja más abajo; los controles responden al instante |
| 2026-11-12 | Vista horizontal: menú en riel lateral, caja más grande con tarjetas a los costados, barra de arriba con el estado y paisaje que no se estira |
| 2026-11-12 | Las tarjetas se mueven con toque mantenido; se quita el botón para reordenarlas; paneles translúcidos; la velocidad del ventilador se muestra solo en %; más vegetación en el fondo |
| 2026-11-12 | Monitoreo: se quita la barra de estado de la conexión (abajo en vertical, arriba en horizontal) |
| 2026-11-12 | Monitoreo: animales que pasean por las colinas del fondo y aves que cruzan el cielo |
| 2026-11-12 | Monitoreo: caja al centro; animales también en la colina cercana; aviso animado para mover las tarjetas; botón + para añadir o quitar plantas (máx. 12) |
| 2026-11-12 | **Nube con Firebase (modelo híbrido):** cuentas con correo o Google en todos tus teléfonos (recuperación por correo, sin semilla); pases de visita en la nube (clave de 12 caracteres, en el teléfono del invitado); modo *Desde la nube* y respaldo automático; Ajustes → Nube con *Vincular ESP32*; Historial completado con resúmenes de la nube y avisos del ESP32 |
| 2026-11-13 | Sin configurar, la app parte en el Simulador con el aviso *Configurar*; la caja de Monitoreo se ve siempre, con *Cargando…* o *Error* en las tarjetas y un aviso del estado de la conexión; modos de conexión en orden WiFi + nube, Bluetooth, Otro teléfono, Simulador |
| 2026-11-13 | Monitoreo: vacas y ovejas en las colinas del fondo |
