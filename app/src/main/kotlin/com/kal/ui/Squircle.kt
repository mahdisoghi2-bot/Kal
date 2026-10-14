package com.kal.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

const val SQUIRCLE_N = 4.5f

private fun sp(v: Double, e: Double): Float = abs(v).pow(e).toFloat()

/** Continuous-curvature (superellipse) rounded rectangle path. Each radius is per corner. */
fun squirclePath(
    l: Float, t: Float, r: Float, b: Float,
    tl: Float, tr: Float = tl, br: Float = tl, bl: Float = tl,
    n: Float = SQUIRCLE_N,
): Path {
    val maxR = minOf(r - l, b - t) / 2f
    val rtl = tl.coerceIn(0f, maxR); val rtr = tr.coerceIn(0f, maxR)
    val rbr = br.coerceIn(0f, maxR); val rbl = bl.coerceIn(0f, maxR)
    val e = 2.0 / n
    val steps = 20
    val p = Path()
    p.moveTo(l + rtl, t)
    p.lineTo(r - rtr, t)
    // top-right: top -> right
    run {
        val cx = r - rtr; val cy = t + rtr
        for (i in 0..steps) {
            val a = (PI / 2) * (1 - i / steps.toDouble())
            p.lineTo(cx + rtr * sp(cos(a), e), cy - rtr * sp(sin(a), e))
        }
    }
    p.lineTo(r, b - rbr)
    // bottom-right: right -> bottom
    run {
        val cx = r - rbr; val cy = b - rbr
        for (i in 0..steps) {
            val a = (PI / 2) * (i / steps.toDouble())
            p.lineTo(cx + rbr * sp(cos(a), e), cy + rbr * sp(sin(a), e))
        }
    }
    p.lineTo(l + rbl, b)
    // bottom-left: bottom -> left
    run {
        val cx = l + rbl; val cy = b - rbl
        for (i in 0..steps) {
            val a = (PI / 2) * (1 - i / steps.toDouble())
            p.lineTo(cx - rbl * sp(cos(a), e), cy + rbl * sp(sin(a), e))
        }
    }
    p.lineTo(l, t + rtl)
    // top-left: left -> top
    run {
        val cx = l + rtl; val cy = t + rtl
        for (i in 0..steps) {
            val a = (PI / 2) * (i / steps.toDouble())
            p.lineTo(cx - rtl * sp(cos(a), e), cy - rtl * sp(sin(a), e))
        }
    }
    p.close()
    return p
}

fun squirclePath(rect: Rect, radius: Float, n: Float = SQUIRCLE_N): Path =
    squirclePath(rect.left, rect.top, rect.right, rect.bottom, radius, n = n)

class SquircleShape(
    private val topRadiusPx: Float,
    private val bottomRadiusPx: Float = topRadiusPx,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(
            squirclePath(0f, 0f, size.width, size.height, topRadiusPx, topRadiusPx, bottomRadiusPx, bottomRadiusPx)
        )
}
