package com.kal

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kal.ui.CameraScreen
import com.kal.ui.DeficitSheet
import com.kal.ui.KalColors
import com.kal.ui.KalMotion
import com.kal.ui.ProgressArgs
import com.kal.ui.ProgressLayer
import com.kal.ui.ResultArgs
import com.kal.ui.ResultLayer
import com.kal.ui.rememberReduceMotion
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Back map: Camera -> exits app. Result -> discards (predictive: scrubs the morph).
 * Progress -> camera. Sheet -> closes.
 */
@Composable
fun KalApp(vm: KalViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val reduce = rememberReduceMotion()

    var result by remember { mutableStateOf<ResultArgs?>(null) }
    val morph = remember { Animatable(0f) }
    var hintAtOpen by remember { mutableStateOf(false) }

    var progress by remember { mutableStateOf<ProgressArgs?>(null) }
    val progressAnim = remember { Animatable(0f) }

    var sheetOpen by remember { mutableStateOf(false) }

    // The deficit sheet is the only onboarding: shown once on first launch.
    LaunchedEffect(ui.loaded, ui.onboarded) {
        if (ui.loaded && !ui.onboarded) { sheetOpen = true; vm.markOnboarded() }
    }

    fun openResult(a: ResultArgs) {
        if (result != null || progress != null) return
        hintAtOpen = ui.hintUses < 3
        vm.bumpHint()
        result = a
        scope.launch {
            morph.snapTo(0f)
            morph.animateTo(1f, tween(if (reduce) 200 else KalMotion.MORPH, easing = FastOutSlowInEasing))
        }
    }

    fun discardResult() {
        scope.launch {
            morph.animateTo(0f, tween(if (reduce) 150 else KalMotion.MORPH_EXIT, easing = FastOutSlowInEasing))
            result = null
        }
    }

    fun openProgress(a: ProgressArgs) {
        progress = a
        scope.launch {
            progressAnim.snapTo(0f)
            progressAnim.animateTo(1f, tween(KalMotion.SHEET, easing = FastOutSlowInEasing))
            if (result != null) { result = null; morph.snapTo(0f) }
        }
    }

    fun closeProgress() {
        scope.launch {
            progressAnim.animateTo(0f, tween((KalMotion.SHEET * 0.7f).toInt(), easing = FastOutSlowInEasing))
            progress = null
            vm.resetScan()
        }
    }

    fun logResult() {
        val r = result ?: return
        val before = ui.eaten
        val id = vm.logMeal(r.kcal, r.label)
        openProgress(ProgressArgs(before, r.kcal, id))
    }

    BackHandler(enabled = sheetOpen) { sheetOpen = false }
    BackHandler(enabled = progress != null && !sheetOpen) { closeProgress() }
    PredictiveBackHandler(enabled = result != null && progress == null && !sheetOpen) { events ->
        try {
            events.collect { e -> morph.snapTo((1f - e.progress).coerceIn(0f, 1f)) }
            morph.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            result = null
        } catch (c: CancellationException) {
            scope.launch { morph.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
        }
    }

    Box(Modifier.fillMaxSize().background(KalColors.void)) {
        CameraScreen(
            ui = ui,
            scan = scan,
            hintVisible = ui.hintUses < 3,
            chromeAlpha = { if (result != null) 1f - morph.value else 1f },
            onFrame = vm::onFrame,
            onOpenDeficit = { sheetOpen = true },
            onOpenProgress = { if (progress == null && result == null) openProgress(ProgressArgs(ui.eaten, 0, null)) },
            onOpenResult = ::openResult,
        )

        result?.let { r ->
            ResultLayer(
                args = r,
                morph = { morph.value },
                overLimit = ui.eaten + r.kcal > ui.limit,
                hintVisible = hintAtOpen,
                reduce = reduce,
                onLog = ::logResult,
                onDiscard = ::discardResult,
            )
        }

        progress?.let { p ->
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = (1f - progressAnim.value) * 40.dp.toPx() }) {
                ProgressLayer(
                    args = p,
                    limit = ui.limit,
                    liveEaten = ui.eaten,
                    reduce = reduce,
                    visibility = { progressAnim.value },
                    onUndo = vm::undo,
                    onDismiss = ::closeProgress,
                )
            }
        }

        DeficitSheet(
            open = sheetOpen,
            maintenance = ui.maintenance,
            deficit = ui.deficit,
            onMaintenance = vm::setMaintenance,
            onDeficit = vm::setDeficit,
            onDismiss = { sheetOpen = false },
        )
    }
}
