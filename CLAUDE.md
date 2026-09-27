# Hitsu

App Android personal de galería cifrada. Kotlin + Jetpack Compose. Importas fotos y vídeos, o
compartes el enlace de un post y yt-dlp lo descarga, y todo se cifra en el teléfono. Se abre con PIN
o huella. Sin cuenta, sin nube y sin nada del cofre que salga del dispositivo. No se publica en
ninguna tienda: es para los dispositivos de su autor.

## Fuentes de verdad
- docs/HITSU_SPEC.md: requisitos, seguridad, flujos. Si algo no está ahí, no se implementa.
- docs/design/: mockups aprobados. La UI debe igualarlos; nombre del archivo = pantalla-estado.
- PROGRESS.md: en qué punto está el trabajo. Se lee al empezar y se actualiza al terminar.

## Reglas duras
- La red solo la usa yt-dlp al descargar un enlace compartido. Sin Firebase ni analytics, y nada del cofre sale del dispositivo.
- Hitsu nunca borra ni modifica la galería del sistema; los originales los borra el usuario.
- FLAG_SECURE en todas las activities.
- Nunca loguear PIN, DEK ni paths.
- Colores solo desde los tokens de SPEC §10.2; nada del theme default de Material.

## Stack

| Qué | Con qué |
|---|---|
| Lenguaje y UI | Kotlin 2.2.21, Jetpack Compose (BOM 2025.06.01), Material3 con el esquema de color sobrescrito entero |
| Build | AGP 8.13.2, Gradle 8.14.3, `compileSdk`/`targetSdk` 35, `minSdk` 29 |
| DI | Hilt 2.57.2 (KSP) |
| Navegación | Navigation Compose 2.9.0; la pantalla raíz la decide `VaultState` |
| Datos | Room 2.7.2, esquemas versionados en `app/schemas/` |
| Imágenes | Coil 3.1.0 con `Fetcher` propios y caché de disco desactivada |
| Vídeo | Media3 1.6.1 (ExoPlayer + `PlayerView` sin sus controles) |
| Descargas | `youtubedl-android` 0.18.1 (el fork de Seal), solo `arm64-v8a`, sin ffmpeg |

Las versiones de AndroidX están fijadas a las últimas que aceptan `compileSdk 35`; subirlas obliga a
subir el compileSdk. Todo pasa por `gradle/libs.versions.toml`.

## Arquitectura

- **Capas**: `ui/` (Compose y ViewModels que exponen `StateFlow`) → `data/` (repositorios, Room,
  ficheros) → `crypto/` y `domain/`, que evitan depender de Android para poder probarse en la JVM.
- **Cripto**: DEK aleatoria por cofre, envuelta dos veces: primero con una KEK derivada del PIN
  (PBKDF2-HmacSHA256, 310 000 iteraciones) y después con una clave no exportable del Android Keystore
  (StrongBox donde lo haya). `VaultGateway.cipher` solo existe mientras el cofre está abierto.
- **Formato de objeto**: cabecera `HTSU\x01` y luego bloques `flag || iv(12) || ciphertext+tag` de
  1 MiB, con el índice del bloque y el flag de último como AAD. Así se cifra y descifra en streaming,
  con progreso, y truncar el archivo se nota. Queda respaldo para los objetos antiguos de un solo bloque.
- **Huella de contenido**: HMAC-SHA256 con subclave derivada de la DEK, no un hash a secas, porque el
  índice de Room está en claro y un SHA-256 permitiría demostrar desde fuera que una foto concreta está
  dentro. Es lo que deduplica los imports.
- **Trabajo largo** (importar, exportar, descargar) corre en `appScope`, no en el del ViewModel, para que
  girar la pantalla o navegar no lo cancele; la UI solo observa su `StateFlow` de estado.
- **Nada en claro en disco** salvo lo imprescindible, y siempre con dueño: el staging de importación, la
  caché de reproducción, el `cookies.txt` que lee yt-dlp y la copia temporal al exportar un vídeo. Todas
  se sobrescriben y se borran en cuanto dejan de hacer falta.

## Convenciones de código

- Los comentarios explican **por qué**, no qué hace la línea siguiente, y van en inglés dentro del código.
  Un comentario que repite el código sobra. Si algo se decidió midiendo en el teléfono, el comentario dice
  qué se midió.
- Nombres y KDoc en inglés; el texto que ve el usuario, en español y en `strings.xml`, nunca como literal
  en el código. El tono lo fija §10.6: tú, corto, sin marketing.
- Composables: un `Route` conectado al ViewModel y un `Screen` sin Hilt para poder previsualizarlo; el
  estado, en un `data class UiState` propio de la pantalla.
- Colores, tipografía y formas solo desde `ui/theme` (tokens de §10.2).
- Antes de dar algo por bueno se compila **y se prueba en el teléfono**; cuando un fallo no es obvio, se
  instrumenta y se mide antes de tocar código, en vez de probar arreglos a ciegas.

## Comandos

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug   # pruebas JVM y lint
./gradlew :app:installDebug                       # instalar en el teléfono conectado
./gradlew :app:assembleDebug                      # solo compilar el APK
```

- `adb shell am force-stop app.hitsu.vault` después de instalar, para que arranque con el código nuevo.
- `adb logcat -s <TAG>` para leer lo que se instrumente; el registro nunca lleva PIN, DEK ni paths.
- **`:app:connectedDebugAndroidTest` desinstala la app y con ella el cofre del teléfono.** No se ejecuta
  sobre un dispositivo con contenido real sin avisar antes.

## Forma de trabajo
- Seguir el orden de SPEC §16, un paso a la vez.
- Cada paso debe compilar y correr antes de pasar al siguiente.
- Al terminar un paso: resumir qué se hizo y qué falta, y esperar mi confirmación.
- Commit por cada cambio funcional terminado, sin esperar al final de la tarea, con mensajes
  *conventional commits* (`feat:`, `fix:`, `refactor:`, `docs:`, `chore:`) que digan qué cambió y por qué.
- Al retomar una sesión: leer antes los últimos commits y `PROGRESS.md`. Al terminarla, actualizar
  `PROGRESS.md` con el estado real.
