# Progreso

Este archivo es el estado real del proyecto. Se lee al empezar una sesión, junto con los últimos
commits, y se actualiza al terminarla. El plan completo vive en `docs/HITSU_SPEC.md` §16; aquí solo
está en qué punto de ese plan estamos.

## Estado actual

_Actualizado: 27 sep 2026._

Hay una app funcionando en el teléfono (Galaxy SM-S948B, Android 16), instalada por `installDebug` y
probada a mano. 79 pruebas unitarias en verde y lint sin errores.

Completado del orden de §16: **pasos 1 a 9, el 13 y el 14**.

- **1–2. Cofre.** Setup de PIN, bloqueo, DEK envuelta por PIN + Keystore, cifrado en streaming por
  bloques de 1 MiB.
- **3–4. Índice y entrada.** Room con el índice, importación desde el Photo Picker, miniaturas, grid
  por días, aviso en el setup de que Hitsu solo copia, y deduplicación por huella con clave.
- **5–7. Ver.** Visor de fotos con zoom y swipe, reproductor Media3 con gestos de brillo y volumen,
  ±10 s, botón central de play/pausa y picture-in-picture que se cierra al bloquear el cofre.
- **8. Compartir hacia Hitsu.** Staging antes del PIN, cifrado al abrir.
- **9. Selección, borrar y exportar.** Long-press abre la selección, "Todo", borrado con confirmación
  y exportación a la galería (`Pictures/Hitsu` y `Movies/Hitsu`) que nunca saca la ubicación.
- **13. Descargador.** yt-dlp empotrado con actualización por antigüedad, enlace compartido,
  descarga rápida desde la hoja de compartir, notificación al terminar, y cookies creadas desde una
  ventana de login propia y guardadas cifradas.
- **14. Respaldo (§7.10).** Ajustes → Respaldo crea un archivo `.hitsubak` con todo el cofre, cifrado
  con una contraseña propia, y lo restaura. Probado en el teléfono: crear, restaurar sobre el mismo
  cofre sin duplicar, y contraseña equivocada.
- **10 a medias.** Auto-bloqueo configurable y FLAG_SECURE están; faltan biometría y cambio de PIN.

Sin hacer: **10** (lo que falta), **11** (álbumes) y **12** (pulido de movimiento e icono).

## En progreso

Nada a medio hacer. El paso 14 quedó cerrado y probado el 27 sep 2026, así que el trabajo continúa
por lo que falta del paso 10.

Con el respaldo funcionando, **ya es seguro correr los tests instrumentados**
(`:app:connectedDebugAndroidTest`), que desinstalan la app: basta hacer un respaldo antes y
restaurarlo después. Conviene avisar igualmente antes de lanzarlos.

## Próximos pasos

1. **Resto del paso 10**: biometría y cambio de PIN; sus filas en Ajustes están ocultas hasta que
   funcionen.
2. **Paso 11**: álbumes, incluida la pestaña de la pantalla principal y el botón "Álbum" de la barra de
   selección, que hoy no se dibuja a propósito.
3. **Paso 12**: pulido de movimiento e icono definitivo.
4. **Sueltos**: el botón "Liberar espacio" del diálogo de sin espacio (hoy dice "Reintentar"), y el
   texto al revés en los campos del login de TikTok (solo ahí; se rodea pegando el usuario).

## Decisiones y notas

- **27 sep 2026 — El respaldo lleva contraseña propia, no el PIN.** Dentro del teléfono el PIN aguanta
  porque el Keystore envuelve la DEK con una clave no exportable y limita los intentos; un archivo que
  sale del dispositivo no tiene esa red, así que seis dígitos se prueban enteros sin que nadie lo
  impida. Se pide dos veces al crearlo y, si se pierde, no hay forma de abrirlo.
- **27 sep 2026 — Las entradas del respaldo van por bloques, sin tamaño por delante.** Ni SAF deja
  volver atrás a rellenar un tamaño ya escrito, ni el índice sabe lo que pesa una foto guardada (se le
  quitó el GPS al importarla). Cada entrada es una sucesión de bloques y un cero que la cierra.
- **27 sep 2026 — Restaurar pasa por el importador normal.** Así lo restaurado queda cifrado con la DEK
  de *este* teléfono, con su miniatura y su fila, y la huella evita duplicados. Es lo que hace que un
  respaldo abra en otro dispositivo con otro PIN.

- **27 sep 2026 — Exportar sin ubicación.** Al exportar, el MP4/MOV se reempaqueta copiando las pistas
  sin recodificar, el HEIC sale como JPEG de calidad 95 (enderezado antes, porque su orientación vivía
  en los metadatos que se quedan atrás) y el WebM/MKV del descargador se copia tal cual. Se eligió
  "quitarla siempre" frente a avisar o preguntar cada vez. JPEG, PNG y WebP ya se limpian al importar.
- **27 sep 2026 — El login de Instagram salía en negro.** No era la página ni el user agent: Compose
  medía el WebView como wrap-content y un WebView sin altura definitiva resuelve `vh`, `svh`, `lvh` y
  `dvh` a **cero** (medido: 0 con la ventana a 733 px). Instagram arma su login con esas unidades. Se
  arregla dando `MATCH_PARENT` al WebView. Aplica a cualquier WebView que se meta en un `AndroidView`.
- **27 sep 2026 — Cookies fuera de la carpeta de trabajo.** El `cookies.txt` en claro vivía donde
  yt-dlp descarga; esa carpeta se borra al empezar cada descarga (así que las cookies desaparecían
  antes de usarse) y lo que queda dentro al terminar se importa al cofre (así que intentaba importar
  el `cookies.txt`). Ahora tiene carpeta propia y se destruye al terminar.
- **27 sep 2026 — Texto al revés en el login de TikTok.** Medido en el teléfono: el teclado compone
  cada letra y la confirma, y al cerrar la composición el cursor vuelve a 0. Se descartaron, midiendo,
  el layout, `FLAG_SECURE` y el modo privado del teclado. Solo pasa en los campos de TikTok. Se rodea
  pegando el texto.
- **27 sep 2026 — Deep links del login.** En la ventana de cookies, lo que no sea `http(s)` se ignora
  (TikTok intenta saltar a su app con `snssdk1233://`): abrirlo dejaría las cookies en esa app, donde
  Hitsu no puede leerlas. Si el enlace trae `browser_fallback_url`, se carga esa.
- **26 sep 2026 — La red entra en el proyecto.** El usuario aceptó el permiso `INTERNET` a cambio de no
  publicar la app. Lo usa solo yt-dlp con el enlace que se comparte; nada del cofre sale del teléfono.
- **26 sep 2026 — yt-dlp se actualiza por antigüedad.** La librería trae un binario que envejece y
  falla con posts de varios vídeos; se comprueba como mucho una vez al día y se actualiza si pasa de
  30 días. Medido: con el binario viejo, 0 archivos; tras actualizar, los 2 del post.
- **25 sep 2026 — Hitsu no borra originales.** Se quitó el diálogo de borrar la galería: el Photo
  Picker entrega URIs que casi nunca permiten borrar, así que prometía algo que no cumplía. El aviso
  se da una vez, en el setup.
- **25 sep 2026 — La huella es con clave.** HMAC-SHA256 con subclave derivada de la DEK en vez de
  SHA-256, porque el índice está en claro y un hash normal deja demostrar desde fuera que un archivo
  concreto está en el cofre.
- **Siempre — Los tests instrumentados borran el cofre.** `connectedDebugAndroidTest` desinstala la
  app. No se ejecutan sobre el teléfono del usuario sin avisar; desde que existe el respaldo (27 sep
  2026), la salida es hacer uno antes y restaurarlo después.
