# Hitsu（櫃）— Spec de producto e implementación

App Android: galería privada cifrada.  
Nombre: **Hitsu** (櫃, “cofre / baúl”).  
Package: `app.hitsu.vault`  
Idioma de UI: español (México) por defecto; strings externalizados para i18n.  
Este documento es la fuente de verdad. Si algo no está aquí, no se inventa estética ni features.

---

## 1. Visión

Hitsu es un cofre local para fotos y vídeos. El usuario importa media desde el dispositivo, la app la cifra y la guarda en almacenamiento privado. A partir de ahí solo se ve dentro de Hitsu, tras PIN/passphrase y/o biometría.

No es red social, no es editor, no es nube, no es “galería con IA”. Es una caja fuerte con visor moderno.

Promesa: **offline, cifrado en reposo, minimalista, predecible.**

---

## 2. Decisiones no negociables

1. **Cifrado real en reposo.** AES-256-GCM. Nada de “ocultar con `.nomedia`” como mecanismo principal.
2. **Hitsu nunca borra ni modifica la galería del sistema.** Importar solo copia al cofre; el original se queda donde estaba y lo borra el usuario por su cuenta, desde su galería. La app no pide permisos para borrar ni ofrece hacerlo.
3. **Nombre y marca: Hitsu.** Sin mascota, sin tagline cursi, sin onboarding de “welcome to your journey”.
4. **Kotlin + Jetpack Compose.** No XML de pantallas nuevas. No Views salvo si un player lo exige y se encapsula.
5. **La red existe para una sola cosa: el descargador.** Hitsu incluye yt-dlp para guardar el contenido de un post que el usuario comparta a la app. Fuera de eso no hay red: no Firebase, no ads, no analytics, no crashlytics remoto, y el cofre nunca sale del dispositivo. La app es de uso personal y no se publica; esa fue la condición para aceptar el permiso `INTERNET`.
6. **Material 3 desarmado.** Paleta propia. Cero purple seed, cero blobs, cero gradientes decorativos, cero ilustraciones genéricas.
7. **PiP de vídeo es feature de v1.**
8. **FLAG_SECURE** en todas las pantallas post-unlock (y en lock también).
9. Desinstalar la app **borra el vault** salvo que el usuario haya exportado o hecho backup. Hay que decirlo en onboarding y en Ajustes.

---

## 3. Fuera de alcance (v1)

- Nube / sync / cuentas
- Editor de imagen o vídeo
- Ocultar icono del launcher
- Device Admin / impedir desinstalación
- Vault señuelo (decoy) — candidato v2
- Reconocimiento facial de álbumes, “recuerdos”, mapas, personas
- Widgets
- Chromecast / DLNA
- Multi-usuario
- Cifrado de archivos que no sean imagen/vídeo

---

## 4. Stack

| Capa | Elección |
|---|---|
| Lenguaje | Kotlin 2.x |
| UI | Jetpack Compose, Material3 (colores override) |
| Min SDK | 29 |
| Target / compile | 35 |
| Nav | Navigation Compose |
| Async | Coroutines + Flow |
| DB índice | Room (metadatos en claro controlados + campos no sensibles). Blob de media **nunca** en Room |
| Cifrado | Android Keystore (AES-256-GCM) para wrapping de Data Encryption Key (DEK) |
| KDF del PIN | Argon2id (si la lib es aceptable y auditada) o PBKDF2-HmacSHA256 con 310k+ iteraciones si Argon2 complica el build. Documentar la elección en código |
| Prefs sensibles | EncryptedSharedPreferences o DataStore + Keystore |
| Imágenes (thumbs / decode) | Coil3. Thumbs = JPEG/WebP cifrados aparte, tamaño corto |
| Vídeo | Media3 (ExoPlayer). Decrypt a archivo temporal en cache privada o `DataSource` custom si es viable; si el custom DataSource se complica, decrypt streaming a temp file en cache con wipe al cerrar |
| Import trabajo | WorkManager (expedited cuando el usuario espera) |
| Biometría | `BiometricPrompt` + `BiometricManager` |
| Picker | Android Photo Picker (`PickMultipleVisualMedia`) como vía principal de import |
| Share in | `intent-filter` `SEND` / `SEND_MULTIPLE` `image/*` `video/*` |
| DI | Hilt |
| Descargas | yt-dlp empotrado (`youtubedl-android`), sin ffmpeg: solo formatos de archivo único. Actualizable desde Ajustes |
| Tests mínimos | Unlock, derive key, import one image, lock on background |

Proyecto Gradle version catalog. Un solo módulo `:app` en v1. Arquitectura simple: `ui / domain / data / crypto / media`.

---

## 5. Modelo de seguridad

### 5.1 Claves

- Al crear el vault, generar **DEK** aleatoria de 256 bit.
- El usuario define un **PIN de 6–12 dígitos** o **passphrase de ≥ 8 caracteres**.
- De la passphrase/PIN se deriva una **KEK** (key encryption key).
- DEK se guarda envuelta (`wrappedDEK`) en almacenamiento privado.
- Keystore guarda una clave de wrapping extra para habilitar biometría sin persistir el PIN en claro.
- Tras N fallos de PIN (configurable, default 8): delay exponencial. No wipe automático en v1 (demasiado destructivo sin backup). Mostrar aviso.

### 5.2 Biometría

- Opcional, se activa después de crear PIN.
- Desbloquear con huella/rostro unwrappea DEK vía Keystore.
- Si el usuario cambia las biometrías del sistema, invalidar y pedir PIN.

### 5.3 Archivos

Cada item importado:

1. Leer bytes del URI concedido (picker / share).
2. Extraer metadatos no secretos para el índice: `mime`, `width`, `height`, `durationMs`, `takenAt` (si EXIF), `importedAt`, `sizeBytes`, `originalDisplayName`.
3. **No guardar GPS en el índice.** EXIF GPS se descarta al cifrar, salvo que en v2 se ofrezca “conservar EXIF”.
4. Cifrar bytes → `files/vault/objects/{uuid}.hitsu`.
5. Generar thumbnail (imagen: downscale 480px lado largo; vídeo: frame en 1s o primer keyframe) → cifrar → `files/vault/thumbs/{uuid}.hitsu`.
6. Insertar fila Room `MediaEntity`.
7. **El original no se toca.** Permanece en la galería del sistema; Hitsu no lo borra ni lo modifica.
8. **No duplicar.** Antes de cifrar se calcula una huella del archivo y, si ya existe en el índice, el archivo se omite y se informa. La huella es un HMAC-SHA256 con subclave derivada de la DEK, no un hash a secas: el índice está en claro, y un SHA-256 permitiría a un tercero demostrar que una foto concreta está en el cofre comparando hashes.

### 5.4 Memoria y residuos

- Nunca loguear PIN, DEK, paths de objetos.
- FLAG_SECURE en activities.
- Al lock / process death: wipe handles de player, borrar temp decrypt.
- Cache de Coil de bitmaps descifrados solo en memoria. Disco: solo thumbs cifrados.
- Recientes del sistema: no mostrar preview de media (FLAG_SECURE).

### 5.5 Auto-lock

Ajustes: Inmediato / 10s / 30s / 1min / 5min / 15min / Nunca. Default: **inmediato al ir a background** (onStop no config change).  
Rotación no debe relockear.  
PiP: ver §8.

**Nunca**: el cofre no se cierra solo, ni en segundo plano ni con el tiempo. Se guarda como un
centinela (`-1`), no como un plazo larguísimo, para que nada pueda convertirlo en una cuenta atrás.
La DEK sigue viviendo solo en memoria: si el sistema mata el proceso o se reinicia el teléfono, al
volver pide PIN. Elegirlo exige confirmar un aviso que diga lo que cuesta: quien coja el teléfono
desbloqueado ve el cofre entero sin PIN.

**Cerrar el cofre** (Ajustes → Seguridad): bloquea al momento y vuelve a la pantalla de PIN. Es la
única salida cuando el auto-bloqueo está en «Nunca», y sirve igual antes de prestar el teléfono.

### 5.6 Recuperación

v1: **no hay “olvidé el PIN” que descifre el vault.**  
Pantalla de setup muestra:

> Si olvidas el PIN no hay forma de abrir el cofre.  
> Exporta copias cuando te importe.

Ajustes: cambiar PIN (exige PIN actual + rewrap DEK).  
Ajustes: exportar backup cifrado (un archivo `.hitsu-bak`) — **sí en v1 si el tiempo alcanza; si no, dejar interfaz “Próximamente” prohibida. Mejor implementarlo o no mostrarlo.** Prioridad: media. Incluirlo si no retrasa visor/player.

---

## 6. Almacenamiento y visibilidad

- Media cifrada **solo** en almacenamiento interno de la app (`context.filesDir`), no en `Pictures/`, no en MediaStore.
- Resultado: Google Photos, Samsung Gallery, Files, otras apps **no ven** los objetos.
- El usuario puede **exportar** un item o selección de vuelta a la galería del sistema (`MediaStore` Pictures/Videos/Hitsu o directorio elegido). Exportar escribe archivo descifrado nuevo; el item del vault permanece salvo que elija “exportar y quitar del cofre”.

Espacio: Ajustes muestra bytes usados por vault + thumbs + cache.

---

## 7. Flujos

### 7.1 Primer lanzamiento

1. Pantalla quieta: wordmark **Hitsu**, una línea: “Un cofre para lo que no va en la galería.”
2. Crear PIN (y confirmar). Toggle “Desbloquear con huella / rostro” si el hardware lo permite.
3. Texto legal corto: cifrado local, sin cuenta, desinstalar borra, y que Hitsu solo copia: los originales siguen en la galería y los borra el usuario. Este aviso se da aquí y no se repite en cada import.
4. CTA: **Importar**.
5. Photo Picker múltiple.
6. Progreso (nombre + barra). Al terminar, si algún archivo ya estaba en el cofre, se dice cuántos se omitieron.
7. Grid vacío-lleno.

Saltar import es válido → grid vacío con CTA.

### 7.2 Lanzamientos siguientes

Splash mínimo (logo 400ms o hasta que Keystore responda) → Lock.

Lock:

- Campo PIN (dots).
- Botón biométrico si está enrolled.
- Teclado numérico propio si el PIN es numérico; teclado sistema si passphrase.
- Sin pistas del contenido (cero thumbs detrás del lock).

Éxito → última sección visitada (grid raíz).

### 7.3 Import posterior

FAB o menú: Importar → Photo Picker.  
Sin diálogos: se copia al cofre y los originales se quedan en la galería.

### 7.4 Share into Hitsu

Si lo compartido es **texto con un enlace**, va al descargador: yt-dlp resuelve el post, descarga el archivo y de ahí sigue el mismo camino que cualquier import.


`AndroidManifest`:

```xml
<intent-filter>
  <action android:name="android.intent.action.SEND"/>
  <action android:name="android.intent.action.SEND_MULTIPLE"/>
  <category android:name="android.intent.category.DEFAULT"/>
  <data android:mimeType="image/*"/>
  <data android:mimeType="video/*"/>
</intent-filter>
```

Si la app está locked: mostrar lock primero, conservar los URIs (takePersistable si aplica; si no, copiar ya a temp privada **antes** de pedir PIN si el share provider se cierra — copiar a staging sin cifrar lo mínimo y cifrar post-unlock, o cifrar staging con clave de sesión efímera).  
Implementación preferida: leer bytes al recibir el intent a `cache/staging`, luego UI lock, luego cifrar e indexar, wipe staging.  
Lo compartido se copia, se cifra y se indexa; el original se queda en la app de origen, que Hitsu no toca.

### 7.5 Ver foto

Tap thumb → visor fullscreen.  
Swipe H entre items del álbum/filtro actual.  
Pinch zoom, double tap zoom, swipe down para cerrar.  
Tap para mostrar/ocultar chrome (hora, índice `12 / 84`, share, export, delete, info).  
Info: fecha import, dimensiones, duración si vídeo, tamaño.

### 7.6 Ver vídeo

Tap → player fullscreen landscape-capable.  
Ver §8.

### 7.7 Eliminar del vault

Desde el modo selección: **Borrar**, con confirmación (“Esto no se puede deshacer”). Borra objeto +
thumb + fila. Irreversible. Hitsu solo borra lo que está dentro del cofre; la galería del sistema no
se toca nunca.

### 7.8 Exportar

Desde el modo selección: **Exportar** saca una copia de cada seleccionado a la galería del teléfono,
insertándola en MediaStore (`Pictures/Hitsu` y `Movies/Hitsu`) con `IS_PENDING` hasta que está escrita,
para que un fallo no deje un archivo a medias visible. No hace falta ningún permiso nuevo.

Diálogo antes de empezar: “Quedará visible en otras galerías.”

**Nada sale con ubicación.** JPEG, PNG y WebP ya vienen limpios de §5.3; lo que se guardó intacto se
reconstruye al salir:

| Formato | Qué se hace | Coste |
|---|---|---|
| MP4 / MOV | Remux: se copian las pistas tal cual a un contenedor nuevo al que nunca se le dice dónde se grabó | Ninguno, no se recodifica |
| HEIC / HEIF | Se decodifica y sale como JPEG de calidad 95, enderezado antes porque la orientación vivía en los metadatos que se dejan atrás | Cambia de formato y se recodifica una vez |
| WebM / MKV | Se copia tal cual | Ninguno; vienen del descargador, no de una cámara, y no traen coordenadas |

El nombre es el que tenía al importarse, con la extensión de lo que realmente se escribe; lo que llegó
sin nombre toma el suyo del id del item. La copia en claro de un vídeo pasa por disco (es demasiado
grande para memoria) y se sobrescribe y se borra antes de terminar.

---

### 7.10 Respaldo del cofre

Exportar (§7.8) saca copias sueltas a la galería. El respaldo es la otra mitad: **un solo archivo
cifrado con todo el contenido del cofre**, para llevárselo a otro teléfono o guardarlo aparte. Vive en
Ajustes → Respaldo, con dos acciones: **Crear respaldo** y **Restaurar respaldo**.

**Contraseña aparte, no el PIN.** Dentro del teléfono, el PIN de 6 dígitos aguanta porque el Keystore
envuelve la DEK con una clave no exportable y limita los intentos (§5.1). Un archivo que sale del
dispositivo no tiene esa red: quien lo tenga puede probar el millón de combinaciones sin que nadie se
lo impida. Por eso el respaldo se cifra con una contraseña que se pide al crearlo — escrita dos veces
para no guardar una errata — y se vuelve a pedir al restaurarlo. Si se pierde, el respaldo no se abre:
no hay otra forma de entrar, y la pantalla lo dice antes de crearlo.

**Formato** (`.hitsubak`), en dos capas:

```
HTSUBAK | versión (1) | sal (16) | iteraciones (4)     ← en claro, es lo que hace falta para derivar
<el resto va cifrado con la clave derivada de la contraseña,
 en el mismo formato por bloques de 1 MiB de §5.3>
   HTSUARC | versión (1)
   entrada: nombre (longitud + utf8) | tamaño (8) | bytes
   ...
   fin: longitud 0
```

La primera entrada es `manifest.json` (versión, fecha y la lista de items con su nombre original,
mime, tipo y fechas); después va una entrada por objeto, `media/<id>`, con el archivo **en claro
dentro de la capa cifrada**. La clave sale de PBKDF2-HmacSHA256 con 310 000 iteraciones y sal nueva
por respaldo, y cifra con AES-256-GCM igual que todo lo demás.

**Nada en claro toca el disco.** El cofre se descifra con la DEK y se vuelve a cifrar con la clave del
respaldo sobre la marcha, de bloque en bloque; el archivo lo elige el usuario con `CREATE_DOCUMENT` y
se escribe en streaming, así que un vídeo de gigabytes no pasa por memoria ni deja copias.

**Restaurar** pide la contraseña, abre el archivo con `OPEN_DOCUMENT` y mete cada entrada por el mismo
camino que cualquier import: se cifra con la DEK **de este** teléfono, se le genera miniatura y se
indexa. Por eso el respaldo sirve en otro dispositivo y con otro PIN. La deduplicación por huella
(§5.3.8) hace que restaurar sobre un cofre que ya tiene esas fotos no las duplique; se informa de
cuántas se omitieron. Una contraseña equivocada se nota al primer bloque, porque GCM no entrega nada
sin comprobar su tag, y no se importa nada.

**El respaldo no es el cofre.** Es un archivo que el usuario guarda donde quiera y del que Hitsu no
sabe nada más; la app no lo sube a ningún sitio ni lo vigila.

### 7.9 Cookies de sitios

Hay posts que un sitio no entrega sin sesión iniciada. Para eso, Ajustes → yt-dlp → **Crear cookies** pide
primero la dirección donde vas a iniciar sesión (diálogo con campo URL y botón Pegar; si escribes solo el
host se le antepone `https://`) y abre esa página en una ventana propia a pantalla completa, con **✕**,
el título de la página y **Listo**.

- La sesión tiene que crearse dentro de Hitsu: las cookies viven en el almacenamiento privado de la app
  que las creó, así que un login hecho en el navegador del teléfono es ilegible desde aquí.
- **Listo** guarda las cookies del sitio que se esté viendo, selladas con la llave del cofre, una por
  dominio. Se listan y se borran desde la misma pantalla de Ajustes.
- La dirección la elige el usuario: los sitios cambian sus páginas de login a menudo y algunos solo
  responden desde su dirección móvil.
- Los enlaces que no son navegación web (`snssdk1233://`, `intent://`…) se ignoran: son el sitio
  intentando entregarle la sesión a su propia app, donde Hitsu no podría leer las cookies. Si el enlace
  trae `browser_fallback_url`, se carga esa.
- Al descargar, las cookies se escriben en claro en una carpeta propia, **fuera** de la carpeta donde
  trabaja yt-dlp: esa se borra al empezar cada descarga y todo lo que quede dentro al terminar se importa
  al cofre. El archivo en claro se destruye en cuanto yt-dlp termina.
- Esta ventana también lleva `FLAG_SECURE`, como el resto.

### 7.11 Presentación (pase de fotos)

Un pase de las fotos del cofre en pantalla completa, sin gestos que haya que aprender: se arranca, se
mira y se sale.

**De dónde salen las fotos.** La presentación se arranca desde tres sitios y el conjunto es siempre
el que se estaba viendo:

- Cabecera de la pantalla principal: la pestaña actual (**Todo** o **Fotos**).
- Cabecera de la pantalla de álbum: ese álbum.
- Visor de fotos: el mismo conjunto del visor, empezando por la foto que está delante.

**Solo fotos.** Los vídeos del conjunto se omiten: el pase no arranca el reproductor a mitad. Si al
filtrar no queda ninguna foto, el botón de arrancar no se muestra.

**Ajustes → Presentación**, y se recuerdan entre sesiones:

- **Tiempo por foto**: 3 s / 5 s / 10 s / 30 s / 1 min. Default 5 s.
- **Aleatorio**: el orden se baraja al arrancar. Apagado sigue el orden de la galería (§9).
- **Bucle**: al llegar al final vuelve a empezar. Apagado termina y sale a la pantalla de donde salió.

Con aleatorio y bucle a la vez, cada vuelta se vuelve a barajar, para que la segunda pasada no repita
el mismo orden.

**Transición.** Fundido cruzado de 600 ms con la curva de §10.4: la foto que sale se desvanece
mientras entra la siguiente. Nunca un corte seco, y nunca dos fotos nítidas a la vez peleándose.

**Mientras corre.** Fondo negro, foto `ContentScale.Fit`, sin recortar. El control —cerrar, el índice
(`3 / 48`) y pausa/reanudar— aparece al arrancar y se esconde solo a los 3 s para dejar la foto
limpia; un toque lo trae de vuelta o lo oculta, y en pausa se queda. Atrás sale.

**Deslizar pasa de foto** sin esperar al reloj: izquierda la siguiente, derecha la anterior, y el
temporizador empieza de cero, así que la foto que llega por dedo tiene su turno completo y no el
resto del turno de la anterior. El cambio sigue siendo el mismo fundido, para que una foto que llega
por dedo se vea igual que una que llega por reloj; mientras arrastras, la foto acompaña al dedo a
medias para acusar el gesto. Deslizar hacia atrás en la primera foto solo vuelve al principio si hay
bucle; hacia delante en la última hace lo mismo que el reloj: otra vuelta con bucle, o salir sin él.

La siguiente foto se descifra y decodifica por adelantado, porque una foto a tamaño completo tarda
más en abrirse que los 600 ms de la transición y el fundido enseñaría un hueco negro. La pantalla no se apaga durante el pase
(`FLAG_KEEP_SCREEN_ON`), que además evita que el auto-bloqueo se dispare por el apagado; al salir, la
bandera se quita. Si el usuario deja la app, se aplica el auto-bloqueo de §5.5 como en cualquier otra
pantalla, y la presentación no sobrevive a un cofre cerrado.

El pase no modifica nada: no borra, no exporta y no cambia álbumes.

## 8. Player de vídeo (v1, obligatorio)

Motor: Media3.

Controles:

| Gesto | Acción |
|---|---|
| Tap | mostrar/ocultar chrome |
| Horizontal drag | seek proporcional a duración |
| Vertical derecha | volumen sistema |
| Vertical izquierda | brillo de ventana |
| Double tap izq / der | −10s / +10s con flash de icono |
| Pinch | no requerido v1 |

Chrome cuando visible:

- Slider de progreso con scrub preview de tiempo
- Play/pause
- Posición / duración
- Botón PiP
- Botón mute
- Menú: velocidad 0.5 / 0.75 / 1 / 1.25 / 1.5 / 2, loop

PiP:

- `enterPictureInPictureMode` con `sourceRectHint` del surface.
- Aspect ratio del vídeo.
- Acciones PiP: −10s / play-pause / +10s (máximo 3 RemoteActions). El icono de play alterna a pause según el estado; actualizar la lista con setPictureInPictureParams cada vez que cambia playWhenReady.
- **Política de lock:** PiP solo con sesión desbloqueada. Si auto-lock dispara, **cerrar PiP** y volver a Lock (el vídeo no puede seguir en el escritorio con el vault locked). Documentado para no “olvidarlo”.
- Al volver de PiP, restaurar fullscreen.

Audio focus. Pausa en llamada. No reproducir al ir a Recientes si FLAG_SECURE ya oculta; pausar si `onUserLeaveHint` y no se entra a PiP.

No usar `VideoView`. No skin tipo botones 2014. Controles finos, tipografía de la app, fondo negro puro.

---

## 9. Galería (grid)

- Columnas: 3 en portrait, 5 en landscape (o adaptive `GridCells.Adaptive(120.dp)`).
- Agrupar por día, sticky header `21 sep 2026`.
- Thumbs square crop center, 1dp gap, **sin radius** o radius 2dp máximo. Nada de 16–24dp cards.
- Vídeos: badge duración esquina, icono mute-off pequeño.
- Long-press → modo selección (exportar, borrar, añadir a álbum).
- Scroll bar implícito; fast scroller de fecha en el borde derecho (tipo iOS) si no hincha el alcance; si se recorta, al menos headers.
- Empty state: una línea “El cofre está vacío” + botón Importar. Sin ilustración.

Álbumes v1 simples:

- Todos
- Fotos
- Vídeos
- **Álbumes**: una pestaña más, con la lista de los que haya. Tocar uno abre `album/{id}`, que es el
  mismo grid filtrado.
- Un item puede estar en varios álbumes (relación N:N en Room) y estar en uno no lo saca de Todos.
  Sin álbumes dentro de álbumes.
- Sacar algo de un álbum **no lo borra del cofre**; borrar un álbum tampoco borra lo que contenía.
  Borrar del cofre sí lo quita de sus álbumes.
- Desde el modo selección, **Álbum** añade lo seleccionado a uno existente o a uno nuevo.
- Ajustes → Álbumes (mockup `docs/design/settings/05-albumes.png`): renombrar y borrar álbumes, ver
  cuántos elementos tiene cada uno, ordenar la lista, y dos destinos automáticos:
  - **Álbum al importar**: dónde cae lo que entra por el selector de fotos o por compartir. Ninguno
    por defecto.
  - **Álbum de descargas**: dónde cae lo que trae yt-dlp, que por defecto es un álbum propio
    llamado «Descargas», creado la primera vez que hace falta. Así lo descargado queda junto y se
    reparte después, sin mezclarse con lo que se importa a mano.

Orden: `takenAt` desc, fallback `importedAt`.

---

## 10. UI / look (para que no parezca app de plantilla)

### 10.1 Principio

Silencio visual. Una tinta. Mucho negro. Tipografía estricta. Cero decoración que no sirva.

### 10.2 Color tokens (dark-first)

```
bg            #0B0B0C
bgElevated    #141416
surfaceInput  #1C1C1F
stroke        #2A2A2E
textPrimary   #F3F1EC
textMuted     #8A8680
accent        #C4A574    /* tinta / latón apagado, NO dorado brillante */
accentPress   #A88A5C
danger        #C45C4A
ok            #6F8F6A
```

Light mode v1 opcional. Si se hace:

```
bg            #F6F3EE
textPrimary   #161513
accent        #8A6A3E
```

Prohibido: purple 600, teal de Material default, gradiente indigo-pink, `tonalElevation` llamativo, FAB extendido con texto “Add memories”.

### 10.3 Tipo

- Display / wordmark: **Fraunces** o **Newsreader** (serif corto) solo en lock y about. Peso medium.
- UI: **Sora** o **Manrope**. Si se quiere cero fonts extra: `FontFamily.SansSerif` + letterspacing -0.2sp en títulos.
- Números de duración: tabular.

### 10.4 Layout

- Edge-to-edge. Status bar transparente, iconos claros.
- Padding horizontal 16. Grid gap 1.
- Botones: height 48, radius 8, no 28.
- FAB: cuadrado 56, radius 8, icono `+`, acento. No pill.
- Ripple suave, 200–240ms.
- Motion: `tween(220)`, FastOutSlowIn. Shared element thumb → visor si Navigation lo permite sin pelear; si no, fade+scale 0.98→1.

### 10.5 Iconos

Lucide / Phosphor regular, stroke 1.5–1.75. Un solo set. No iconos filled mixtos.

### 10.6 Copy

Tú, corto, sin marketing.

- “Cofre bloqueado”
- “Importar”
- “¿Eliminar originales de la galería?”
- “Esto no se puede deshacer”
- “Picture-in-picture”
- “El vídeo se cierra al bloquear el cofre”

Nada de “¡Listo, tus recuerdos están a salvo! 🎉”.

---

## 11. Pantallas

| Ruta | Contenido |
|---|---|
| `setup` | crear PIN, biometría, disclaimer |
| `lock` | PIN / bio |
| `home` | grid + tabs Todos/Fotos/Vídeos + álbumes + FAB |
| `album/{id}` | grid filtrado |
| `settings/albums` | renombrar, borrar y contar álbumes; destinos automáticos |
| `photo/{id}` | visor |
| `video/{id}` | player |
| `slideshow?album={id}&filter={f}&start={id}` | presentación a pantalla completa (§7.11) |
| `settings/slideshow` | tiempo por foto, aleatorio, bucle |
| `settings` | auto-lock, bio on/off, cambiar PIN, espacio usado, export all (si hay), about |
| `importProgress` | overlay o screen con lista |

Ajustes about: “Hitsu / 櫃 — cofre local. Cifrado en el dispositivo. Sin red.”

---

## 12. Permisos

Declarar solo:

- Photo Picker: **ningún** `READ_MEDIA_*` si el picker basta para import.
- Si se implementa “eliminar original” de URIs MediaStore del picker: a veces no hace falta permiso broad; usar el URI con permiso temporal. Si delete falla, mensaje claro.
- **No** pedir `MANAGE_EXTERNAL_STORAGE`.
- **No** `CAMERA` en v1 (no hay cámara in-app; el usuario comparte desde la cámara del sistema).
- `INTERNET`: solo para yt-dlp, al descargar un enlace compartido por el usuario.
- Biometric: normal.
- PiP: `android:supportsPictureInPicture="true"` `resizeableActivity`.
- `USE_BIOMETRIC`.

Si más adelante se quiere “escanear toda la galería”, eso es otro producto y otra política de Play. No en v1.

---

## 13. Manifest / sistema

- `android:supportsPictureInPicture="true"`
- `configChanges` razonables para no recrear el player al rotar
- Reciver/activity share con `launchMode` que no pierda el intent
- App name: `Hitsu`
- Icono: cofre/baúl mínimo, 2D, un color sobre negro. No degradado, no cara, no candado genérico de pack. Preferible un rectángulo con tapa (櫃) geométrico.

---

## 14. Datos Room (orientativo)

```
MediaEntity(
  id: String,            // uuid
  type: PHOTO | VIDEO,
  mime: String,
  objectPath: String,
  thumbPath: String,
  width: Int,
  height: Int,
  durationMs: Long?,
  sizeBytes: Long,
  takenAt: Long?,
  importedAt: Long,
  originalName: String?,
  contentFingerprint: String?,   // HMAC con subclave de la DEK; índice único, evita duplicados
  favorite: Boolean
)

AlbumEntity(id, name, createdAt)          // name único, sin distinguir mayúsculas
AlbumMediaCrossRef(albumId, mediaId, addedAt)  // borrar media o álbum se lleva la fila (CASCADE)
VaultMeta(wrappedDek, kdfSalt, kdfParams, pinKind, bioEnabled, failedUnlocks, lockTimeout)
```

Nada de plaintext paths fuera de filesDir.

---

## 15. Criterios de aceptación

- [ ] App abre en lock si el vault existe.
- [ ] PIN incorrecto no filtra thumbs.
- [ ] Import de 1 foto y 1 vídeo por Photo Picker funciona.
- [ ] Tras import, el original sigue intacto en la galería del sistema: Hitsu no borra nada.
- [ ] Reimportar el mismo archivo no lo duplica; se omite y se informa.
- [ ] Selección: long-press abre el modo, Todo selecciona lo visible, atrás y ✕ salen.
- [ ] Borrar pide confirmación y se lleva objeto, thumb y fila.
- [ ] Lo exportado aparece en la galería y no lleva ubicación dentro.
- [ ] El respaldo se crea con su propia contraseña y se restaura en otro teléfono con otro PIN.
- [ ] Una contraseña equivocada no importa nada y lo dice.
- [ ] Restaurar sobre un cofre que ya tiene ese contenido no lo duplica.
- [ ] Un item puede estar en varios álbumes y sigue apareciendo en Todos.
- [ ] Sacar de un álbum no borra nada del cofre; borrar un álbum tampoco.
- [ ] Lo que descarga yt-dlp aparece en el álbum de descargas sin tener que moverlo.
- [ ] Visor: swipe entre fotos, zoom.
- [ ] Player: seek, volumen gesto, brillo gesto, ±10s, PiP.
- [ ] PiP se cierra si corre auto-lock.
- [ ] FLAG_SECURE: screenshot bloqueado en vault abierto.
- [ ] El único código que usa la red es el descargador; nada del cofre se envía a ningún lado.
- [ ] Compartir un enlace a Hitsu descarga el contenido y lo guarda cifrado.
- [ ] Las cookies se guardan cifradas, una por dominio, y la copia en claro solo existe mientras yt-dlp
      la está leyendo.
- [ ] El archivo de cookies nunca acaba en el cofre ni se borra antes de que yt-dlp lo use.
- [ ] Desinstalar elimina media (documentado).
- [ ] UI dark cumple tokens de §10. No se usa el theme púrpura default.
- [ ] Share desde Google Fotos / Files entra a staging + lock + import.
- [ ] Background import no pierde el trabajo si se gira la pantalla.

---

## 16. Orden de implementación (para el agente)

No empieces por animaciones ni álbumes.

1. Proyecto Compose + theme tokens + lock + setup PIN (sin media).
2. Crypto: generar DEK, wrap/unwrap, cifrar/descifrar bytes en test.
3. Room índice + import de una imagen via picker + thumb + grid.
4. Aviso de originales en el setup + deduplicación por huella.
5. Visor foto.
6. Import vídeo + player Media3 + gestos.
7. PiP + política de lock.
8. Share intent + staging.
9. Selección múltiple, borrar del vault, exportar a la galería (§7.7 y §7.8).
10. Auto-lock, FLAG_SECURE, bio, settings.
11. Álbumes: pestaña, pantalla de álbum, añadir desde selección, ajustes y destinos automáticos (§9).
12. Pulido motion e icono.
13. Descargador: yt-dlp empotrado, enlace compartido, notificación y descarga rápida, actualización desde Ajustes y cookies mediante la ventana de login de §7.9.
14. Respaldo del cofre: crear y restaurar el archivo cifrado de §7.10.

Cada paso debe compilar y poder ejecutarse.

---

## 17. Estilo de código que el agente debe seguir

- Nombres en inglés en código; strings UI en español.
- Sin comentarios obvios. Comentarios solo en crypto y en la política PiP+lock.
- Sin `TODO` de features de v2 en la UI.
- Errores de import: snackbar o sheet con el nombre del archivo, no crash.
- Coroutines en `viewModelScope`. Crypto pesado en `Dispatchers.Default` / IO.
- Prohibido guardar PIN en `SharedPreferences` en claro.

---

## 18. Riesgos conocidos (no los “resuelvas” con trampas)

1. **Photo Picker URIs** a veces no se pueden borrar. UX honesta, no loops de permiso all-files.
2. **Vídeo 4K cifrado:** decrypt-to-temp puede llenar disco. Avisar si hay < 2× size libre.
3. **Process death en player:** re-lock y temp wipe.
4. **Play Store:** descripción debe decir galería privada local, no “esconder apps” ni “anti-desinstalación”.
5. **Backup de Android:** `android:allowBackup="false"` en v1 para no subir ciphertext+meta a la nube del usuario sin control.

---

## 19. Descripción corta (store / about)

Hitsu es un cofre. Importas fotos y vídeos, o compartes un enlace y los descarga, y todo se cifra en el teléfono. Se abre con PIN o huella. Sin cuenta, sin nube y sin nada que salga del dispositivo.

---

Fin del spec. Cualquier pantalla extra (tutorial de 4 pasos, tema aurora, candado 3D, nube) está fuera y no se implementa.
