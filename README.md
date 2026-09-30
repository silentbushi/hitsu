# Hitsu 櫃

Hitsu es un cofre. Importas fotos y vídeos, o compartes un enlace y los descarga, y todo se cifra en
el teléfono. Se abre con PIN o huella. Sin cuenta, sin nube y sin nada del cofre que salga del
dispositivo.

App Android personal, en Kotlin y Jetpack Compose. No está en ninguna tienda: se instala desde el
APK de la sección [Releases](../../releases) o compilándola.

---

## Qué hace

### El cofre

Cada archivo y su miniatura se cifran con **AES-256-GCM**. La clave del cofre (la DEK) se envuelve
dos veces: primero con una clave derivada de tu PIN (**PBKDF2-HmacSHA256, 310 000 iteraciones**) y
después con una clave **no exportable del Android Keystore**, con StrongBox donde lo hay. Eso
significa que una copia de los archivos, fuera de este teléfono, no se abre ni con el PIN.

Los objetos se cifran **en bloques de 1 MiB** encadenados, así que un vídeo de gigabytes se descifra
mientras se reproduce, con progreso, y un archivo al que le falte el final se detecta en vez de
parecer completo.

Se abre con **PIN o huella**. La huella no descifra nada por sí sola: autoriza una llave del Keystore
que guarda una segunda copia de la DEK, y si cambias las huellas del sistema esa llave se invalida y
vuelve a pedir el PIN. El PIN se puede cambiar sin recifrar ni un byte del contenido.

Se cierra solo cuando la app se va de la pantalla, con el retardo que elijas: desde inmediato hasta
15 minutos, o **nunca** si aceptas el aviso de lo que eso cuesta. Para eso está **Cerrar el cofre**
en Ajustes, que lo bloquea al momento. Aunque elijas «Nunca», la clave vive solo en memoria: si el
sistema cierra la app o reinicias el teléfono, al volver pide PIN. Todas las ventanas llevan
`FLAG_SECURE`: ni capturas ni miniatura en recientes.

### Entrar contenido

- **Importar** desde el selector de fotos del sistema. Hitsu **copia**: no borra ni modifica tu
  galería, los originales los borras tú.
- **Compartir a Hitsu** desde cualquier app, con Hitsu abierta o cerrada. Si el cofre está cerrado, lo
  compartido se guarda aparte y se cifra en cuanto entras.
- **Descargar** el contenido de un post: comparte el enlace y **yt-dlp** lo baja directo al cofre, con
  notificación al terminar y un acceso de *descarga rápida* en la hoja de compartir que no abre la
  app. Para los sitios que piden sesión hay una ventana de login propia; sus cookies se guardan
  cifradas con la llave del cofre y solo se escriben en claro los segundos que yt-dlp tarda en
  leerlas. yt-dlp se actualiza solo cuando su binario envejece.
- **Sin duplicados**: reimportar el mismo archivo lo reconoce y lo omite. La huella es un HMAC con
  subclave derivada de la DEK, no un hash a secas, porque el índice está en claro y un hash normal
  dejaría demostrar desde fuera que una foto concreta está dentro.

### Ver

Visor de fotos con zoom y swipe. Reproductor propio sobre Media3 con gestos de **brillo** y
**volumen**, ±10 s con doble toque, y **picture-in-picture** que se cierra en cuanto el cofre se
bloquea.

### Organizar

**Álbumes**: un item puede estar en varios y sigue estando en Todos. Se crean vacíos o desde una
selección, y sacar algo de un álbum no lo borra del cofre. Hay dos destinos automáticos: dónde cae lo
que importas y dónde cae lo que descargas, que por defecto es un álbum «Descargas» para que lo que
baja quede junto hasta que lo repartas.

Selección con pulsación larga para exportar, archivar o borrar; y se cambia de pestaña deslizando.

### Sacar contenido

- **Exportar** copias a la galería del teléfono (`Pictures/Hitsu`, `Movies/Hitsu`), **siempre sin la
  ubicación**: los vídeos se reempaquetan sin recodificar y los HEIC salen como JPEG, que es la única
  forma de reescribirles los metadatos.
- **Respaldo**: un único archivo `.hitsubak` con todo el cofre, **cifrado con su propia contraseña**,
  no con el PIN — dentro del teléfono el PIN aguanta porque el Keystore limita los intentos, pero un
  archivo que sale del dispositivo no tiene esa red. Se restaura en otro teléfono con otro PIN, se
  lleva los álbumes, y lo que ya esté en el cofre no se duplica.

## Lo que no hace

Sin cuentas, sin nube, sin Firebase, sin analítica y sin publicidad. **La única parte que usa la red
es el descargador**, y solo con el enlace que tú compartes; nada del cofre sale del teléfono.

Desinstalar la app destruye la clave del Keystore y con ella el cofre: para eso está el respaldo.

## Compilar

Necesita JDK 17+ y el SDK de Android (plataforma 35). `minSdk` 29.

```bash
./gradlew :app:installDebug                       # instalar en el teléfono conectado
./gradlew :app:testDebugUnitTest :app:lintDebug   # pruebas JVM y lint
./gradlew :app:connectedDebugAndroidTest          # pruebas en el dispositivo
```

Hoy: 98 pruebas unitarias y 31 instrumentadas.

> Las pruebas instrumentadas **desinstalan la app**, y con ella el cofre del dispositivo. Haz un
> respaldo antes y restáuralo después.

## Documentación

- [`docs/HITSU_SPEC.md`](docs/HITSU_SPEC.md) es la fuente de verdad: requisitos, seguridad y flujos.
  Si algo no está ahí, no se implementa.
- [`PROGRESS.md`](PROGRESS.md) dice en qué punto está el trabajo y por qué se decidió lo que se
  decidió.
- [`docs/design/`](docs/design) tiene los mockups, uno por pantalla y estado.
- [`CLAUDE.md`](CLAUDE.md) resume stack, arquitectura y convenciones para quien (o lo que) siga.

## Créditos

- Descargas: [yt-dlp](https://github.com/yt-dlp/yt-dlp) a través de
  [youtubedl-android](https://github.com/JunkFood02/youtubedl-android).
- El kanji del icono viene de [Noto Sans JP](https://fonts.google.com/noto/specimen/Noto+Sans+JP)
  (SIL Open Font License).
