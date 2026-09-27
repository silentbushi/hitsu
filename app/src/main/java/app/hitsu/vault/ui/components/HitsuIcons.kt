package app.hitsu.vault.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object HitsuIcons {
    val Delete: ImageVector by lazy {
        lucide(
            "delete",
            "M10 5a2 2 0 0 0 -1.344 0.519l-6.328 5.74a1 1 0 0 0 0 1.481l6.328 5.741A2 2 0 0 0 10 19h10a2 2 0 0 0 2 -2V7a2 2 0 0 0 -2 -2z",
            "M12 9l6 6",
            "M18 9l-6 6",
        )
    }

    val Plus: ImageVector by lazy {
        lucide("plus", "M12 5v14", "M5 12h14", strokeWidth = 1.75f)
    }

    val ArrowLeft: ImageVector by lazy {
        lucide("arrow-left", "M19 12H5", "M12 19l-7 -7 7 -7")
    }

    /** An arrow leaving a tray: what the selection mockup draws over "Exportar". */
    val Export: ImageVector by lazy {
        lucide(
            "export",
            "M12 4v11",
            "M8 8l4 -4 4 4",
            "M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2v-3",
        )
    }

    /** A pencil: what the albums mockup puts next to a name you can change. */
    val Edit: ImageVector by lazy {
        lucide(
            "edit",
            "M12 20h9",
            "M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1 -4z",
        )
    }

    /** A closed padlock: the mockup puts one next to the album count. */
    val Lock: ImageVector by lazy {
        lucide(
            "lock",
            "M5 11a1 1 0 0 1 1 -1h12a1 1 0 0 1 1 1v9a1 1 0 0 1 -1 1H6a1 1 0 0 1 -1 -1z",
            "M8 10V7a4 4 0 0 1 8 0v3",
        )
    }

    val Check: ImageVector by lazy {
        lucide("check", "M20 6L9 17l-5 -5")
    }

    val ChevronRight: ImageVector by lazy {
        lucide("chevron-right", "M9 18l6 -6 -6 -6")
    }

    /**
     * A cog: ring, hub and eight teeth. The home mockup draws a sun here, but that glyph reads as
     * screen brightness, which is what the player uses it for.
     */
    val Settings: ImageVector by lazy {
        lucide(
            "settings",
            "M12 5.5a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0 -13",
            "M12 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0 -5",
            "M18.5 12h2.5",
            "M16.6 16.6l1.76 1.76",
            "M12 18.5v2.5",
            "M7.4 16.6l-1.76 1.76",
            "M5.5 12H3",
            "M7.4 7.4l-1.76 -1.76",
            "M12 5.5V3",
            "M16.6 7.4l1.76 -1.76",
        )
    }

    val ChevronLeft: ImageVector by lazy {
        lucide("chevron-left", "M15 18l-6 -6 6 -6")
    }

    val Info: ImageVector by lazy {
        lucide(
            "info",
            "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0 -20",
            "M12 16v-4",
            "M12 8h.01",
        )
    }

    val Close: ImageVector by lazy {
        lucide("x", "M18 6L6 18", "M6 6l12 12")
    }

    val Play: ImageVector by lazy {
        lucide("play", "M6 4l14 8 -14 8z")
    }

    val Pause: ImageVector by lazy {
        lucide("pause", "M9 4v16", "M15 4v16")
    }

    val VolumeOn: ImageVector by lazy {
        lucide(
            "volume-2",
            "M11 5L6 9H3v6h3l5 4z",
            "M16 9a4 4 0 0 1 0 6",
            "M19 6a8 8 0 0 1 0 12",
        )
    }

    val VolumeOff: ImageVector by lazy {
        lucide("volume-x", "M11 5L6 9H3v6h3l5 4z", "M17 9l4 6", "M21 9l-4 6")
    }

    val PictureInPicture: ImageVector by lazy {
        lucide(
            "picture-in-picture",
            "M5 4h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2z",
            "M13 11h5a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z",
        )
    }

    val More: ImageVector by lazy {
        lucide(
            "more-vertical",
            "M12 4a1 1 0 1 0 0 2a1 1 0 1 0 0 -2",
            "M12 11a1 1 0 1 0 0 2a1 1 0 1 0 0 -2",
            "M12 18a1 1 0 1 0 0 2a1 1 0 1 0 0 -2",
        )
    }

    val Rewind: ImageVector by lazy {
        lucide("rotate-ccw", "M3 12a9 9 0 1 0 3 -6.7L3 8", "M3 3v5h5")
    }

    val Forward: ImageVector by lazy {
        lucide("rotate-cw", "M21 12a9 9 0 1 1 -3 -6.7L21 8", "M21 3v5h-5")
    }

    val Brightness: ImageVector by lazy {
        lucide(
            "sun",
            "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0 -6",
            "M12 2v3",
            "M12 19v3",
            "M4.2 4.2l2.1 2.1",
            "M17.7 17.7l2.1 2.1",
            "M2 12h3",
            "M19 12h3",
            "M4.2 19.8l2.1 -2.1",
            "M17.7 6.3l2.1 -2.1",
        )
    }

    val Fingerprint: ImageVector by lazy {
        lucide(
            "fingerprint-scan",
            "M3 8V5a2 2 0 0 1 2 -2h3",
            "M16 3h3a2 2 0 0 1 2 2v3",
            "M21 16v3a2 2 0 0 1 -2 2h-3",
            "M8 21H5a2 2 0 0 1 -2 -2v-3",
            "M12 9v3a4 4 0 0 1 -1 2.6",
            "M9.5 9.5a2.5 2.5 0 0 1 5 0V13",
        )
    }
}

private fun lucide(name: String, vararg paths: String, strokeWidth: Float = 1.5f): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
