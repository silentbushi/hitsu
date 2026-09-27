# Progreso

Este archivo es el estado real del proyecto. Se lee al empezar una sesión, junto con los últimos
commits, y se actualiza al terminarla. El plan completo vive en `docs/HITSU_SPEC.md` §16; aquí solo
está en qué punto de ese plan estamos.

## Estado actual

_Actualizado: 27 sep 2026._

Hay una app funcionando en el teléfono (Galaxy SM-S948B, Android 16), instalada por `installDebug` y
probada a mano. 97 pruebas unitarias y 31 instrumentadas en verde, y lint sin errores.

**Los catorce pasos de §16 están hechos.** Lo que queda son remates y lo que vaya saliendo al usarla.

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
- **11. Álbumes.** Pestaña con la rejilla del mockup `album/`, portada por álbum, crear vacío desde el
  encabezado, añadir desde la selección, pantalla de álbum con quitar, ajustes con renombrar y borrar,
  y dos destinos automáticos: ninguno al importar y «Descargas» para lo que baja yt-dlp.
- **12. Pulido.** Movimiento de §10.4 (fundido con escala 0.98→1 al navegar) e icono definitivo: el
  kanji 櫃 en negro sobre latón dentro de un marco de sello, con el trazo real de Noto Sans JP.
  Después, a petición del usuario, se añadió cambiar de pestaña deslizando en la pantalla principal.
- **10. Bloqueo completo.** Auto-bloqueo configurable, FLAG_SECURE, desbloqueo con huella y cambio de
  PIN, todo probado en el teléfono el 27 sep 2026.

Sin hacer: **11** (álbumes) y **12** (pulido de movimiento e icono).

## En progreso

Nada. Los catorce pasos de §16 están hechos y probados en el teléfono, y los remates que quedaban
apuntados también:

- El diálogo de sin espacio lleva a **Liberar espacio**, que abre el gestor del sistema y reintenta
  solo al volver.
- Los tests instrumentados pasan (31), incluidos los nuevos de álbumes y del respaldo. Llevaban sin
  compilarse desde el paso 9, así que hubo que ponerlos al día primero.
- El respaldo se probó de la forma que importa: respaldo → desinstalación por los tests → PIN nuevo →
  restaurar. Volvió todo, álbumes incluidos. Las cookies de los sitios no van en el respaldo y hay que
  rehacerlas.

## Próximos pasos

No hay nada pendiente. Lo que venga saldrá de usar la app.

Una cosa conocida y **decidida como aceptable** (27 sep 2026): en el login de TikTok los campos
escriben al revés, medido y acotado a esa página. Se rodea pegando el texto, y como la app es de uso
personal el usuario prefiere dejarlo así en vez de interceptar la composición del teclado.

## Decisiones y notas

- **27 sep 2026 — Los tests instrumentados se pasan con respaldo delante.** Desinstalan la app y se
  llevan el cofre, así que el orden es: crear respaldo, correrlos, reinstalar, restaurar. Se hizo así
  y funcionó; de paso es la única prueba real de que restaurar sirve.
- **27 sep 2026 — Dejar de compilar los tests instrumentados sale caro.** Entre el paso 9 y el 14 se
  quedaron atrás con los cambios del gateway y del repositorio, y hubo que arreglarlos antes de poder
  correrlos. Conviene compilarlos (`:app:compileDebugAndroidTestKotlin`) aunque no se ejecuten.

- **27 sep 2026 — El icono es el kanji de verdad, no un dibujo aproximado.** El trazo de 櫃 sale de
  Noto Sans JP (licencia SIL OFL, así que se puede empotrar) en vez de dibujarse a mano: dieciocho
  trazos hechos a ojo se leen como un error para quien conoce el carácter. Se genera una vez a un
  `pathData` y se queda en el repositorio; el guion que lo extrajo no hace falta para compilar.
- **27 sep 2026 — El respaldo guarda los álbumes por nombre.** Un id de este cofre no significa nada
  en otro teléfono, así que el manifiesto guarda nombres y qué archivos iban juntos. Al restaurar,
  cada archivo dice con qué id se quedó aquí — el nuevo, o el que ya tenía si estaba repetido — y con
  eso se rehacen los álbumes reutilizando los que ya existan con ese nombre.

- **27 sep 2026 — La huella autoriza, no deriva.** El cofre guarda una segunda copia de la DEK sellada
  con una llave del Keystore que exige autenticación para cada uso. De ahí que activarla necesite el
  cofre abierto (es cuando la DEK está en memoria) y que cambiar las huellas del sistema la invalide:
  se tira la llave y se vuelve al PIN, en vez de confiar en un dedo añadido después.
- **27 sep 2026 — Cambiar el PIN no recifra nada.** Solo reenvuelve la DEK con la clave nueva y sal
  nueva, así que el contenido no se toca y la copia que abre la huella sigue sirviendo. Un PIN actual
  equivocado no gasta intento ni arranca la espera: a esa pantalla solo se llega con el cofre abierto,
  donde un error es una errata y no alguien probando.

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
