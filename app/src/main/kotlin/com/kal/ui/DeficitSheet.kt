package com.kal.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kal.core.Calories
import com.kal.core.Persian
import kotlinx.coroutines.launch

private val ITEM_H = 52.dp

@Composable
fun DeficitSheet(
    open: Boolean,
    maintenance: Int,
    deficit: Int,
    onMaintenance: (Int) -> Unit,
    onDeficit: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val hidden = remember { Animatable(1f) }                 // 0 = shown, 1 = off-screen
    var dragPx by remember { mutableFloatStateOf(0f) }
    var sheetH by remember { mutableIntStateOf(1) }
    val onDismissNow by rememberUpdatedState(onDismiss)

    LaunchedEffect(open) {
        hidden.animateTo(if (open) 0f else 1f, tween(if (open) KalMotion.SHEET else (KalMotion.SHEET * 0.7f).toInt(), easing = FastOutSlowInEasing))
    }
    if (!open && hidden.value >= 0.999f) return

    val density = LocalDensity.current
    val shape = remember(density) { SquircleShape(with(density) { KalSpace.sheetRadius.toPx() }, 0f) }
    var curM by remember { mutableIntStateOf(maintenance) }
    var curD by remember { mutableIntStateOf(deficit) }
    val limit = Calories.dailyLimit(curM, curD)

    Box(Modifier.fillMaxSize()) {
        // 40% black scrim, flat (no blur).
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = (1f - hidden.value).coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.4f))
                .pointerInput(Unit) { detectTapGestures { onDismissNow() } }
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { sheetH = it.height.coerceAtLeast(1) }
                .graphicsLayer { translationY = sheetH * hidden.value + dragPx }
                .clip(shape)
                .background(KalColors.graphite)
                .draggable(
                    state = rememberDraggableState { d -> dragPx = (dragPx + d).coerceAtLeast(0f) },
                    orientation = Orientation.Vertical,
                    onDragStopped = { v ->
                        if (dragPx > sheetH * 0.25f || v > 1500f) {
                            hidden.snapTo((dragPx / sheetH).coerceIn(0f, 1f)); dragPx = 0f; onDismissNow()
                        } else animate(dragPx, 0f, animationSpec = tween(200)) { x, _ -> dragPx = x }
                    },
                )
                .pointerInput(Unit) { detectTapGestures { } }          // swallow taps on the sheet
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = KalSpace.margin, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width(40.dp).height(4.dp).clip(SquircleShape(6f)).background(KalColors.line))
            Spacer(Modifier.height(16.dp))

            WheelRow("نیاز روزانه", (1200..5000 step 50).toList(), curM) { curM = it; onMaintenance(it) }
            Spacer(Modifier.height(8.dp))
            WheelRow("کسری", (0..1500 step 50).toList(), curD) { curD = it; onDeficit(it) }

            Spacer(Modifier.height(20.dp))
            KalText("سقف روزانه ${Persian.format(limit)}", KalType.title, KalColors.phosphor, capped = true)
        }
    }
}

@Composable
private fun WheelRow(label: String, values: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        KalText(label, KalType.caption, KalColors.text2)
        Wheel(label, values, selected, onSelect)
    }
}

@Composable
private fun Wheel(label: String, values: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val itemPx = with(density) { ITEM_H.toPx() }
    val startIdx = values.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIdx)
    val center by remember {
        androidx.compose.runtime.derivedStateOf {
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0)
                .coerceIn(0, values.lastIndex)
        }
    }
    var last by remember { mutableIntStateOf(startIdx) }
    val onSelectNow by rememberUpdatedState(onSelect)
    LaunchedEffect(state) {
        snapshotFlow { center }.collect { i ->
            if (i != last) { last = i; Haptics.segment(view); onSelectNow(values[i]) }
        }
    }

    fun step(delta: Int) {
        val i = (center + delta).coerceIn(0, values.lastIndex)
        scope.launch { state.animateScrollToItem(i) }
    }

    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(vertical = ITEM_H),
        modifier = Modifier
            .width(220.dp).height(ITEM_H * 3)
            .semantics {
                contentDescription = label
                stateDescription = Persian.format(values[center])
                progressBarRangeInfo = ProgressBarRangeInfo(values[center].toFloat(), values.first().toFloat()..values.last().toFloat(), values.size - 2)
                setProgress { v ->
                    val i = values.indices.minByOrNull { kotlin.math.abs(values[it] - v) } ?: 0
                    scope.launch { state.animateScrollToItem(i) }; true
                }
                customActions = listOf(
                    CustomAccessibilityAction("افزایش ۵۰") { step(1); true },
                    CustomAccessibilityAction("کاهش ۵۰") { step(-1); true },
                )
            },
    ) {
        items(values.size) { i ->
            Box(Modifier.fillMaxWidth().height(ITEM_H), contentAlignment = Alignment.Center) {
                KalText(
                    Persian.format(values[i]), KalType.title,
                    if (i == center) KalColors.text1 else KalColors.text3, capped = true,
                )
            }
        }
    }
}
