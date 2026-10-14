package com.kal.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kal.core.Persian
import kotlinx.coroutines.flow.first
import kotlin.math.abs

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun lerpRect(a: Rect, b: Rect, t: Float) =
    Rect(lerpF(a.left, b.left, t), lerpF(a.top, b.top, t), lerpF(a.right, b.right, t), lerpF(a.bottom, b.bottom, t))

/**
 * The result screen is drawn as a morph: the locked scan box (squircle, radius 44dp) expands to the
 * full screen (radius 0) while the frozen camera frame fades to ink. [morph] 0..1 is driven by the
 * root (timed, or scrubbed by predictive back).
 */
@Composable
fun ResultLayer(
    args: ResultArgs,
    morph: () -> Float,
    overLimit: Boolean,
    hintVisible: Boolean,
    reduce: Boolean,
    onLog: () -> Unit,
    onDiscard: () -> Unit,
) {
    val view = LocalView.current
    val accent = if (overLimit) KalColors.tangerine else KalColors.phosphor
    val glow = if (overLimit) KalColors.tangerineFaint else KalColors.phosphorFaint
    val roll = remember(args) { Animatable(0f) }
    val onLogNow by rememberUpdatedState(onLog)
    val onDiscardNow by rememberUpdatedState(onDiscard)

    LaunchedEffect(args) {
        snapshotFlow { morph() }.first { it >= 0.45f }
        roll.animateTo(args.kcal.toFloat(), tween(if (reduce) 200 else KalMotion.ROLL, easing = FastOutSlowInEasing))
        Haptics.confirm(view)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "${args.label}، ${Persian.digits(args.kcal)} کالری"
                customActions = listOf(
                    CustomAccessibilityAction("ثبت وعده") { onLogNow(); true },
                    CustomAccessibilityAction("حذف") { onDiscardNow(); true },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var total = 0f; var moved = false; var done = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            if (!done && !moved && morph() >= 0.9f) onLogNow()
                            break
                        }
                        total += ch.positionChange().y
                        if (abs(ch.position.y - down.position.y) > viewConfiguration.touchSlop) moved = true
                        if (!done && total > 100.dp.toPx() && morph() >= 0.9f) { done = true; onDiscardNow() }
                    }
                }
            }
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val maxWpx = with(density) { (maxWidth - KalSpace.margin * 2).toPx() }

        Canvas(Modifier.fillMaxSize()) {
            val m = morph().coerceIn(0f, 1f)
            val full = Rect(0f, 0f, size.width, size.height)
            val r = lerpRect(args.boxRect, full, m)
            val rad = lerpF(KalSpace.boxRadius.toPx(), 0f, m)
            val path = squirclePath(r, rad)
            clipPath(path) {
                // frozen camera frame, fading out as the interior turns to ink
                args.frozen?.let {
                    drawImage(
                        it, srcOffset = IntOffset.Zero, srcSize = IntSize(it.width, it.height),
                        dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        alpha = (1f - m * 1.6f).coerceIn(0f, 1f),
                    )
                }
                drawRect(KalColors.ink, alpha = m)
                drawRect(
                    Brush.radialGradient(listOf(glow, Color.Transparent), Offset(size.width / 2, size.height / 2), size.width * 0.9f),
                    alpha = ((m - 0.4f) / 0.6f).coerceIn(0f, 1f),
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = ((morph() - 0.5f) / 0.5f).coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            FixedNumber(
                value = roll.value.toInt(),
                layoutDigits = Persian.digits(args.kcal).length,
                sign = "", style = KalType.hero, color = accent,
                maxWidthPx = maxWpx, minSp = 96f,
            )
            KalText("کالری", KalType.caption, KalColors.text2)
            Spacer(Modifier.height(24.dp))
            KalText(args.label, KalType.body, KalColors.text2, capped = false)
            Spacer(Modifier.weight(1f))
            if (hintVisible) {
                KalText(
                    "برای ثبت لمس کنید · برای حذف به پایین بکشید", KalType.caption, KalColors.text3,
                    Modifier.padding(horizontal = KalSpace.margin).windowInsetsPadding(WindowInsets.navigationBars),
                    maxLines = 2,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
