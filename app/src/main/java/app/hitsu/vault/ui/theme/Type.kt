@file:OptIn(ExperimentalTextApi::class)

package app.hitsu.vault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.hitsu.vault.R

private val Fraunces = FontFamily(
    Font(
        resId = R.font.fraunces,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.Setting("opsz", 48f),
            FontVariation.Setting("SOFT", 0f),
            FontVariation.Setting("WONK", 0f),
        ),
    ),
)

private val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal),
    Font(R.font.manrope, FontWeight.Medium),
    Font(R.font.manrope, FontWeight.SemiBold),
)

private const val TABULAR = "tnum"

object HitsuType {
    val WordmarkLarge = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        letterSpacing = (-0.3).sp,
    )
    val Wordmark = WordmarkLarge.copy(fontSize = 30.sp, letterSpacing = (-0.2).sp)

    val Title = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        letterSpacing = (-0.4).sp,
    )
    val Subtitle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    val Body = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp)
    val Caption = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp)
    val Fine = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 22.sp)
    val Meta = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = TABULAR,
    )
    val Label = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val Action = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    val Keypad = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        fontFeatureSettings = TABULAR,
    )
}

private val Base = Typography()

internal val HitsuTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Manrope),
    displayMedium = Base.displayMedium.copy(fontFamily = Manrope),
    displaySmall = Base.displaySmall.copy(fontFamily = Manrope),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Manrope),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Manrope),
    headlineSmall = HitsuType.Title,
    titleLarge = Base.titleLarge.copy(fontFamily = Manrope),
    titleMedium = HitsuType.Subtitle,
    titleSmall = Base.titleSmall.copy(fontFamily = Manrope),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Manrope),
    bodyMedium = HitsuType.Body,
    bodySmall = HitsuType.Meta,
    labelLarge = HitsuType.Label,
    labelMedium = Base.labelMedium.copy(fontFamily = Manrope),
    labelSmall = Base.labelSmall.copy(fontFamily = Manrope),
)
