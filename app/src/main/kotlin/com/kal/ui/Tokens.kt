package com.kal.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kal.app.R
import com.kal.core.Palette
import com.kal.core.Ripening

/** Single source of truth for colour. No raw hex in components. Mirrored in /tokens.json. */
object KalColors {
    val void = Color(Palette.VOID)
    val ink = Color(Palette.INK)
    val graphite = Color(Palette.GRAPHITE)
    val line = Color(Palette.LINE)
    val phosphor = Color(Palette.PHOSPHOR)
    val phosphorDim = Color(Palette.PHOSPHOR_DIM)
    val phosphorFaint = Color(Palette.PHOSPHOR).copy(alpha = 0.12f)
    val tangerine = Color(Palette.TANGERINE)
    val tangerineDim = Color(Palette.TANGERINE_DIM)
    val tangerineFaint = Color(Palette.TANGERINE).copy(alpha = 0.12f)
    val text1 = Color(0xFFEAFFE5)
    val text2 = Color(0xFF7C8B79)
    val text3 = Color(0xFF6B7A68)

    /** Colour at a point along the day (0..1+). */
    fun ripe(fraction: Float) = Color(Ripening.bright(fraction))
    fun ripeDim(fraction: Float) = Color(Ripening.dim(fraction))
    fun ripeFaint(fraction: Float) = ripe(fraction).copy(alpha = 0.12f)
}

object KalMotion {
    const val LOCK_SNAP = 250
    const val MORPH = 650
    const val MORPH_EXIT = 450
    const val ROLL = 500
    const val WEDGE = 600
    const val COLOR = 400
    const val SHEET = 350
    const val BREATHE = 2400
}

object KalSpace {
    val margin = 24.dp
    val grid = 8.dp
    val minTouch = 48.dp
    val boxRadius = 44.dp
    val sheetRadius = 32.dp
}

/** Fonts: only the supplied files. Peyda for text, Radio Fill (pixel display) for hero numerals. */
object KalFonts {
    val peyda = FontFamily(
        Font(R.font.peyda_thin, FontWeight.Thin),
        Font(R.font.peyda_extralight, FontWeight.ExtraLight),
        Font(R.font.peyda_light, FontWeight.Light),
        Font(R.font.peyda_regular, FontWeight.Normal),
        Font(R.font.peyda_medium, FontWeight.Medium),
        Font(R.font.peyda_semibold, FontWeight.SemiBold),
        Font(R.font.peyda_extrabold, FontWeight.ExtraBold),
    )
    val radio = FontFamily(Font(R.font.radio_fill, FontWeight.Black))
}

private val platform = PlatformTextStyle(includeFontPadding = false)
private val lhStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(family: FontFamily, size: Float, weight: FontWeight, lh: Float) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = (size * lh).sp,
    letterSpacing = 0.sp,          // never track Persian text; it breaks joining
    platformStyle = platform,
    lineHeightStyle = lhStyle,
)

enum class KalRole(val capped: Boolean) { Hero(true), Display(true), Title(true), Body(false), Caption(false) }

object KalType {
    // Peyda has no Bold (700): nearest available weights are used, never synthetic bold.
    val hero = style(KalFonts.radio, 160f, FontWeight.Black, 1.1f)
    val display = style(KalFonts.radio, 96f, FontWeight.Black, 1.1f)
    val title = style(KalFonts.peyda, 28f, FontWeight.Medium, 1.1f)
    val body = style(KalFonts.peyda, 17f, FontWeight.Normal, 1.35f)
    val caption = style(KalFonts.peyda, 14f, FontWeight.Normal, 1.35f)
    const val MAX_SCALE = 1.15f
}

@Composable
fun KalText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    capped: Boolean = false,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 1,
) {
    val d = LocalDensity.current
    val dens = if (capped && d.fontScale > KalType.MAX_SCALE) Density(d.density, KalType.MAX_SCALE) else d
    CompositionLocalProvider(LocalDensity provides dens) {
        BasicText(
            text = text,
            modifier = modifier,
            style = style.copy(color = color, textAlign = textAlign),
            maxLines = maxLines,
            overflow = TextOverflow.Clip,
            softWrap = maxLines > 1,
        )
    }
}
