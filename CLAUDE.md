# GreenBox (invernadero-iot) — instrucciones para Claude

App Android (Kotlin + Jetpack Compose + Material 3) para monitorear y controlar un invernadero con ESP32.
Paquete `com.invernadero`. Idioma de la app, del código y de la documentación: **español**.
Sin Internet por diseño: SQLite local, WiFi/Bluetooth locales, sin dependencias de nube.

## Documentación que hay que mantener

- `MANUAL_USUARIO.md` — manual de usuario (funcionalidades, roles, acceso, pantallas, seguridad,
  conexión, problemas frecuentes, limitaciones).
- `LEEME.md` — documento técnico / de la rúbrica (armado del proyecto, justificación, tabla de seguridad ISO, plan de pruebas).

### Regla de actualización (obligatoria al terminar cada tarea que cambie código)

Actualiza `MANUAL_USUARIO.md` **solo si el cambio afecta lo que está documentado**, por ejemplo:
- una función, pantalla, botón, texto visible o flujo nuevo / cambiado / eliminado;
- roles, permisos o quién puede hacer qué;
- medidas de seguridad, límites (intentos, tiempos, duraciones), cifrado o auditoría;
- configuración, conexión, requisitos o una limitación conocida.

**No** lo actualices por refactors, cambios de estilo visual menores, renombres internos, correcciones
de bugs que no cambian el comportamiento descrito, ni por cambios en comentarios o pruebas.

Al actualizarlo:
1. Edita solo las secciones afectadas (no reescribas el manual entero) y mantén el mismo tono y formato.
2. Cambia la fecha de "Última actualización" (y la versión si cambió `versionName` o la versión de la base de datos).
3. Agrega una fila al final en "Historial de cambios del manual" con la fecha y un resumen de una línea.
4. Si cambió algo de seguridad, actualiza también la tabla de seguridad de `LEEME.md`.
5. Menciona al usuario en una línea qué se actualizó del manual (o que no hizo falta).

## Notas del proyecto

- **Alcance actual: solo usuarios.** No hay panel ni funciones de administrador en la app (el usuario lo pidió:
  "los administradores no tienen nada que ver con los usuarios"). Tras entrar se abre Monitoreo. El antiguo
  panel "Raíces" quedó guardado en `_to_delete/PantallaRaices.kt.txt` por si se retoma en una app/sección aparte.

- No hay SDK de Android en las sesiones en la nube: no se puede compilar ahí; revisar con cuidado y pedir al usuario que compile.
- Base de datos: `datos/BaseDatos.kt` (versión actual 3). Todo cambio de esquema necesita `onUpgrade` sin borrar datos.
- Seguridad: nunca guardar contraseñas, semillas ni claves en texto plano; auditar acciones sensibles con `db.auditar(...)`.
- Diseño: títulos en Bricolage Grotesque y cuerpo/datos en Instrument Sans (`res/font/`, tema en `ui/Tema.kt`);
  bloques con `Seccion` (superficie tonal plana, sin sombra) y gráficas con `GraficaSerie` (`ui/Comunes.kt`); tono de los textos inspirado en la naturaleza
  (marca GreenBox), pero los botones dicen claramente su acción.
