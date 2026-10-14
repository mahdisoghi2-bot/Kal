package com.kal.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kal.core.Calories
import com.kal.core.Persian
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** before = eaten kcal before this visit; kcal/mealId set when arriving from a freshly logged meal. */
data class ProgressArgs(val before: Int, val kcal: Int, val mealId: Long?)

@Composable
fun ProgressLayer(
    args: ProgressArgs,
    limit: Int,
    liveEaten: Int,
    reduce: Boolean,
    visibility: () -> Float,
    onUndo: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var undone by remember { mutableStateOf(false) }
    val eaten = when {
        args.mealId == null -> liveEaten
        undone -> args.before
        else -> args.before + args.kcal
    }
    val over = eaten > limit
    val afterFrac = Calories.fraction(eaten, limit)
    val beforeFrac = Calories.fraction(args.before, limit)

    // Fill animation: drawn in the draw phase from the end of the "eaten earlier" zone.
    val anim = remember { Animatable(beforeFrac) }
    LaunchedEffect(afterFrac) {
        anim.animateTo(afterFrac, tween(if (reduce) 200 else KalMotion.WEDGE, easing = FastOutSlowInEasing))
    }
    val remainAnim = remember { Animatable((limit - args.before).toFloat()) }
    LaunchedEffect(eaten, limit) {
        remainAnim.animateTo((limit - eaten).toFloat(), tween(if (reduce) 200 else KalMotion.ROLL, easing = FastOutSlowInEasing))
    }

    var undoVisible by remember { mutableStateOf(args.mealId != null) }
    LaunchedEffect(Unit) { if (args.mealId != null) { delay(4000); undoVisible = false } }
    val undoAlpha = remember { Animatable(1f) }
    LaunchedEffect(undoVisible) { if (!undoVisible) undoAlpha.animateTo(0f, tween(250)) }

    val onDismissNow by rememberUpdatedState(onDismiss)
    val shownFrac = anim.value
    val numColor = KalColors.ripe(if (over) 1f else shownFrac)
    val remaining = remainAnim.value.toInt()
    val pct = (afterFrac * 100).toInt().coerceAtLeast(0)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = visibility() }
            .background(KalColors.void)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var total = 0f; var moved = false; var done = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) { if (!done && !moved) onDismissNow(); break }
                        total += ch.positionChange().y
                        if (abs(ch.position.y - down.position.y) > viewConfiguration.touchSlop) moved = true
                        if (!done && total > 100.dp.toPx()) { done = true; onDismissNow() }
                    }
                }
            }
    ) {
        val density = LocalDensity.current
        val maxWpx = with(density) { (maxWidth - KalSpace.margin * 2).toPx() }

        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            FixedNumber(
                value = abs(remaining),
                layoutDigits = Persian.digits(abs(limit - eaten)).length.coerceAtLeast(1),
                sign = if (remaining < 0 || (over && remaining == 0)) "+" else "",
                style = KalType.display, color = numColor, maxWidthPx = maxWpx, minSp = 64f,
            )
            KalText(if (over) "بیشتر" else "مانده", KalType.caption, KalColors.text2)

            Spacer(Modifier.weight(1f))
            WedgeBar(
                before = beforeFrac, current = shownFrac, over = over,
                modifier = Modifier
                    .padding(horizontal = KalSpace.margin).fillMaxWidth().height(20.dp)
                    .semantics { contentDescription = "${Persian.percent(pct)} از سقف امروز مصرف شده" },
            )
            Spacer(Modifier.height(16.dp))
            KalText("${Persian.format(eaten)} / ${Persian.format(limit)}", KalType.caption, KalColors.text2)
            Spacer(Modifier.weight(1f))

            Box(Modifier.height(64.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (args.mealId != null && undoAlpha.value > 0.01f) {
                    Box(
                        Modifier
                            .graphicsLayer { this.alpha = undoAlpha.value }
                            .heightIn(min = 48.dp).widthIn(min = 120.dp)
                            .kalClickable("بازگردانی") {
                                if (!undone && undoVisible) { undone = true; undoVisible = false; onUndo(args.mealId) }
                            },
                        contentAlignment = Alignment.Center,
                    ) { KalText("بازگردانی", KalType.body, KalColors.text2) }
                }
            }
        }
    }
}

/** Ten capsule wedges, filling right to left. Drawn (not laid out) so animation costs no layout. */
@Composable
private fun WedgeBar(before: Float, current: Float, over: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val n = Calories.WEDGES
        val gap = 4.dp.toPx()
        val ww = (size.width - gap * (n - 1)) / n
        val hh = size.height
        val dimFrac = min(before, current).coerceAtLeast(0f)
        val brightFrac = current
        for (i in 0 until n) {
            val x1 = size.width - i * (ww + gap)      // right edge of wedge i (RTL: wedge 0 is rightmost)
            val x0 = x1 - ww
            val cap = Path().apply { addRoundRect(RoundRect(x0, 0f, x1, hh, CornerRadius(hh / 2))) }
            clipPath(cap) {
                drawRect(KalColors.graphite, Offset(x0, 0f), Size(ww, hh))
                val pos = if (over) 1f else (i + 0.5f) / n
                val dimPart = (dimFrac * n - i).coerceIn(0f, 1f)
                val brightPart = (brightFrac * n - i).coerceIn(0f, 1f)
                if (dimPart > 0f) drawRect(KalColors.ripeDim(pos), Offset(x1 - ww * dimPart, 0f), Size(ww * dimPart, hh))
                if (brightPart > dimPart) {
                    drawRect(KalColors.ripe(pos), Offset(x1 - ww * brightPart, 0f), Size(ww * (brightPart - dimPart), hh))
                }
            }
        }
    }
}
