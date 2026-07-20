# AutoSend

App Android (Kotlin + Jetpack Compose) para **programar mensajes** — con texto, imágenes y archivos —
que se envían **automáticamente** a la hora indicada en **WhatsApp** (Messenger en fase futura).

A la hora programada, la app enciende la pantalla, abre el chat correcto con el mensaje ya cargado y
**pulsa "Enviar" por ti** mediante un Servicio de Accesibilidad de Android.

> **Uso personal.** Automatizar envíos en WhatsApp va técnicamente contra sus Términos de Servicio.
> Úsala de forma responsable (mensajes propios, bajo volumen). Los envíos masivos/spam pueden derivar
> en el bloqueo de tu cuenta.

---

## ⚠️ Sobre el "desbloqueo automático"

Android **no permite que ninguna app quite un bloqueo seguro** (PIN, patrón, contraseña o huella).
Es una barrera del sistema operativo. El envío 100% automático con la pantalla bloqueada solo funciona si:

- El teléfono usa bloqueo **"Deslizar" o "Ninguno"**, **o**
- Tienes **Smart Lock** activo (lugar o dispositivo Bluetooth de confianza).

Con **PIN/patrón/huella**, AutoSend enciende la pantalla y deja el chat listo, pero **tú** debes
desbloquear para que se complete el envío (modo semi-automático).

---

## Cómo compilar (Windows)

Requisitos: **JDK 17+** (el que trae Android Studio en `...\Android Studio\jbr` sirve) y el **SDK de
Android** con la plataforma **35** y build-tools **35.x**. El proyecto ya incluye el Gradle wrapper
(usa Gradle 8.13) y compila correctamente (`compileSdk`/`targetSdk = 35`, `minSdk = 27`).

**Opción A — Android Studio (recomendada):**
1. Instala **Android Studio** (Ladybug o posterior).
2. **Abre** la carpeta `autosend` como proyecto; sincroniza dependencias.
3. Conecta un **teléfono físico** con depuración USB (WhatsApp real no existe en el emulador).
4. Pulsa **Run ▶**.

**Opción B — línea de comandos (PowerShell):**
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # o cualquier JDK 17+
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
En máquinas con poca RAM libre, agrega `--no-daemon --no-parallel` para evitar quedarte sin memoria.

---

## Primer uso

1. Abre la app y toca el ícono de **ajustes** (arriba a la derecha).
2. Activa los cuatro permisos:
   - **Servicio de accesibilidad** (el clave — activa "AutoSend — envío automático").
   - **Alarmas exactas**.
   - **Notificaciones**.
   - **Sin optimización de batería**.
3. Vuelve y toca **+** para programar un mensaje:
   - App destino: WhatsApp.
   - **Teléfono** con código de país sin `+` (ej. `5215512345678`) — habilita el enlace directo al chat.
   - **Nombre del contacto** tal como aparece en WhatsApp (necesario para adjuntos y para Messenger).
   - Escribe el mensaje, adjunta imágenes/archivos, elige fecha y hora.
   - Deja **Envío automático** activado.
4. Guarda. A la hora programada el mensaje se enviará solo.

---

## Cómo probar cada parte

| Qué probar | Cómo |
|---|---|
| Disparo exacto | Programa un mensaje a **+2 min** con la pantalla apagada. Debe dispararse a tiempo. Simula Doze con `adb shell dumpsys deviceidle force-idle`. |
| Encender/desbloquear | Con bloqueo "Deslizar": apaga la pantalla y verifica que se enciende y descarta sola. Con PIN: verás la notificación y el chat listo tras desbloquear. |
| Envío de texto | Programa un mensaje a un **contacto de prueba tuyo**; confirma que WhatsApp abre y se envía solo. |
| Adjuntos | Programa un mensaje con 1 imagen + 1 PDF; confirma selección de contacto, caption y envío automático. |
| Reinicio | Reinicia el teléfono con un mensaje pendiente; sigue programado (`BootReceiver`). |

---

## Arquitectura (resumen)

- **UI** (`ui/`): pantallas Compose — lista, edición y onboarding de permisos.
- **Datos** (`data/`): Room (`ScheduledMessage`, `Attachment`) + `MessageRepository` que mantiene en
  sincronía la base de datos, los archivos adjuntos y las alarmas.
- **Programación** (`scheduler/`): `AlarmScheduler` usa `setAlarmClock` (exacto, despierta el equipo,
  sin depender del toggle de alarmas exactas); `AlarmReceiver` dispara el envío.
- **Ejecución** (`sender/`): `SendService` (foreground) orquesta; `WakeActivity` enciende la pantalla
  y descarta el keyguard; `WhatsAppSender` construye el Intent (`wa.me` para texto, `ACTION_SEND` para
  adjuntos); `PendingSend` coordina el envío en curso.
- **Automatización** (`accessibility/`): `AutoSendAccessibilityService` pulsa "Enviar" y navega el
  selector de contactos, con varios selectores (id, content-description y texto) por robustez.
- **Arranque** (`boot/`): `BootReceiver` reprograma las alarmas tras reiniciar o actualizar.

## Limitaciones conocidas

- Los IDs/textos de los botones de WhatsApp cambian entre versiones e idiomas; el servicio de
  accesibilidad usa varios criterios, pero puede requerir mantenimiento si WhatsApp actualiza su UI.
- Editar un mensaje permite **agregar** adjuntos; quitar adjuntos ya guardados no está implementado
  (elimina y recrea el mensaje si lo necesitas).
- Fabricantes con gestión agresiva de batería (Xiaomi, Huawei, Samsung) pueden matar el servicio;
  el paso de "Sin optimización de batería" ayuda, y a veces hay que fijar la app en "Recientes".
- Messenger (fase futura) no permite pre-cargar texto por Intent, así que dependerá casi por completo
  del servicio de accesibilidad.
