# Hitsu

Hitsu es un cofre. Importas fotos y vídeos, o compartes un enlace y los descarga, y todo se cifra en
el teléfono. Se abre con PIN o huella. Sin cuenta, sin nube y sin nada del cofre que salga del
dispositivo.

App Android personal, en Kotlin y Jetpack Compose.

## Qué hace

- **Cofre cifrado.** AES-256-GCM sobre cada archivo y su miniatura, con la clave derivada del PIN
  (PBKDF2-HmacSHA256, 310 000 iteraciones) y envuelta además por una clave no exportable del Android
  Keystore, con StrongBox cuando el teléfono lo tiene.
- **Importar sin tocar tu galería.** Hitsu copia; los originales se quedan donde están y los borras
  tú. Reimportar el mismo archivo no lo duplica: se reconoce por una huella con clave, que sin la
  llave del cofre no dice nada.
- **Visor y reproductor propios**, con gestos de brillo y volumen, ±10 s y picture-in-picture que se
  cierra en cuanto el cofre se bloquea.
- **Descargador.** Comparte a Hitsu el enlace de un post y yt-dlp guarda su contenido directamente en
  el cofre, con notificación al terminar y un acceso de descarga rápida desde la hoja de compartir.
  Para los sitios que piden sesión hay una ventana de login propia que guarda sus cookies cifradas.
- **Exportar.** Devuelve copias a la galería del teléfono, siempre sin la ubicación que llevaran
  dentro.

## Lo que no hace

Sin cuentas, sin nube, sin Firebase, sin analítica y sin publicidad. La única parte que usa la red es
el descargador, y solo con el enlace que tú compartes.

## Compilar

Necesita JDK 17+ y el SDK de Android (plataforma 35). Con un teléfono conectado por USB y la
depuración activada:

```
./gradlew :app:installDebug
```

Las pruebas unitarias y el lint:

```
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Las pruebas instrumentadas (`:app:connectedDebugAndroidTest`) **desinstalan la app**, y con ella el
cofre del dispositivo. No las ejecutes sobre un teléfono con contenido que te importe.

## Documentación

- [`docs/HITSU_SPEC.md`](docs/HITSU_SPEC.md) es la fuente de verdad: requisitos, seguridad y flujos.
- [`docs/design/`](docs/design) tiene los mockups aprobados, uno por pantalla y estado.
