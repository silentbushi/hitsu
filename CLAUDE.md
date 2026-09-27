# Hitsu

App Android de galería cifrada. Kotlin + Jetpack Compose.

## Fuentes de verdad
- docs/HITSU_SPEC.md: requisitos, seguridad, flujos. Si algo no está ahí, no se implementa.
- docs/design/: mockups aprobados. La UI debe igualarlos; nombre del archivo = pantalla-estado.

## Reglas duras
- La red solo la usa yt-dlp al descargar un enlace compartido. Sin Firebase ni analytics, y nada del cofre sale del dispositivo.
- Hitsu nunca borra ni modifica la galería del sistema; los originales los borra el usuario.
- FLAG_SECURE en todas las activities.
- Nunca loguear PIN, DEK ni paths.
- Colores solo desde los tokens de SPEC §10.2; nada del theme default de Material.

## Forma de trabajo
- Seguir el orden de SPEC §16, un paso a la vez.
- Cada paso debe compilar y correr antes de pasar al siguiente.
- Al terminar un paso: resumir qué se hizo y qué falta, y esperar mi confirmación.