package com.kal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.kal.core.Persian

private const val DIGITS = "۰۱۲۳۴۵۶۷۸۹"
private const val SEP = "\u066C"

/**
 * Fixed-width-cell number so the odometer never jitters. Every digit sits in a cell as wide as the
 * widest digit of the supplied font. The string is laid out LTR (numerals read left to right).
 * [maxWidthPx] auto-shrinks the size down to [minSp] to keep a single line.
 */
@Composable
fun FixedNumber(
    value: Int,                 // animated value (>= 0)
    layoutDigits: Int,          // digit count used for sizing (final value), keeps size stable while rolling
    sign: String,               // "", "+" or "−"
    style: TextStyle,
    color: Color,
    maxWidthPx: Float,
    minSp: Float,
    modifier: Modifier = Modifier,
    capped: Boolean = true,
) {
    val measurer: TextMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseSp = style.fontSize.value
    val cell = remember(style) {
        fun w(s: String, st: TextStyle) = measurer.measure(s, st, constraints = Constraints()).size.width.toFloat()
        val dw = DIGITS.maxOf { w(it.toString(), style) }
        Triple(dw, w(SEP, style), w("+", style).coerceAtLeast(w("\u2212", style)))
    }
    val groups = if (layoutDigits > 3) (layoutDigits - 1) / 3 else 0
    val total = cell.first * layoutDigits + cell.second * groups + (if (sign.isNotEmpty()) cell.third else 0f)
    val scale = if (total <= 0f) 1f else minOf(1f, maxWidthPx / total)
    val finalSp = (baseSp * scale).coerceAtLeast(minSp)
    val f = finalSp / baseSp
    val text = Persian.format(value)
    val st = style.copy(fontSize = finalSp.sp, lineHeight = (style.lineHeight.value * f).sp)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier) {
            fun cellDp(px: Float) = with(density) { (px * f).toDp() }
            if (sign.isNotEmpty()) {
                Box(Modifier.width(cellDp(cell.third))) { KalText(sign, st, color, capped = capped) }
            }
            for (ch in text) {
                val wpx = if (ch == '\u066C') cell.second else cell.first
                Box(Modifier.width(cellDp(wpx))) { KalText(ch.toString(), st, color, capped = capped) }
            }
        }
    }
}
