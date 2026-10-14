package com.kal.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kal.Phase
import com.kal.ScanUi
import com.kal.Ui
import com.kal.core.Calories
import com.kal.core.Persian
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class Perm { Granted, Denied, Permanent }

private fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is ComponentActivity) return c; c = c.baseContext }
    return null
}

data class ResultArgs(val label: String, val kcal: Int, val boxRect: Rect, val frozen: ImageBitmap?)

@Composable
fun CameraScreen(
    ui: Ui,
    scan: ScanUi,
    hintVisible: Boolean,
    chromeAlpha: () -> Float,
    onFrame: (Bitmap) -> Unit,
    onOpenDeficit: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenResult: (ResultArgs) -> Unit,
) {
    val ctx = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var perm by remember { mutableStateOf(if (granted()) Perm.Granted else Perm.Denied) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        perm = when {
            ok -> Perm.Granted
            ctx.findActivity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } == true -> Perm.Denied
            else -> Perm.Permanent
        }
    }
    LaunchedEffect(Unit) { if (perm != Perm.Granted) launcher.launch(Manifest.permission.CAMERA) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (granted()) perm = Perm.Granted }

    if (perm != Perm.Granted) {
        MessageScreen(
            title = "دسترسی به دوربین لازم است",
            button = "باز کردن تنظیمات",
            onButton = {
                if (perm == Perm.Permanent) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else launcher.launch(Manifest.permission.CAMERA)
            },
        )
    } else {
        CameraContent(ui, scan, hintVisible, chromeAlpha, onFrame, onOpenDeficit, onOpenProgress, onOpenResult)
    }
}

@Composable
private fun MessageScreen(title: String, button: String? = null, onButton: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().background(KalColors.void), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(KalSpace.margin)) {
            KalText(title, KalType.title, KalColors.text1, capped = true, maxLines = 3)
            if (button != null) {
                Spacer(Modifier.height(32.dp))
                Box(
                    Modifier
                        .heightIn(min = 56.dp).widthIn(min = 160.dp)
                        .clip(SquircleShape(60f))
                        .border(1.5.dp, KalColors.phosphor, SquircleShape(60f))
                        .kalClickable(button, onButton)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) { KalText(button, KalType.body, KalColors.phosphor) }
            }
        }
    }
}

private fun centerRect(c: Offset, w: Float, h: Float) = Rect(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2)

private fun defaultBox(w: Float, h: Float): Rect {
    val bw = w * 0.76f; val bh = bw * 1.25f
    return Rect(w / 2 - bw / 2, h * 0.5f - bh / 2, w / 2 + bw / 2, h * 0.5f + bh / 2)
}

/** Maps a normalised box in the upright analysis frame to screen px, matching PreviewView FILL_CENTER. */
private fun mapBox(n: android.graphics.RectF, fw: Int, fh: Int, w: Float, h: Float): Rect {
    val s = max(w / fw, h / fh)
    val ox = (w - fw * s) / 2f; val oy = (h - fh * s) / 2f
    var r = Rect(ox + n.left * fw * s, oy + n.top * fh * s, ox + n.right * fw * s, oy + n.bottom * fh * s)
    val pad = w * 0.04f
    r = r.inflate(pad)
    val minW = w * 0.36f
    if (r.width < minW) r = centerRect(r.center, minW, r.height)
    if (r.height < minW) r = centerRect(r.center, r.width, minW)
    return Rect(r.left.coerceAtLeast(8f), r.top.coerceAtLeast(8f), r.right.coerceAtMost(w - 8f), r.bottom.coerceAtMost(h - 8f))
}

@Composable
private fun CameraContent(
    ui: Ui,
    scan: ScanUi,
    hintVisible: Boolean,
    chromeAlpha: () -> Float,
    onFrame: (Bitmap) -> Unit,
    onOpenDeficit: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenResult: (ResultArgs) -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val reduce = rememberReduceMotion()
    val onFrameNow by rememberUpdatedState(onFrame)
    var unavailable by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE   // TextureView: allows frame capture
            scaleType = PreviewView.ScaleType.FILL_CENTER
            keepScreenOn = true
        }
    }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(ctx)
        val exec = Executors.newSingleThreadExecutor()
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get(); provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(480, 640), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                var last = 0L
                analysis.setAnalyzer(exec) { proxy ->
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (now - last >= 150) {
                            last = now
                            val raw = proxy.toBitmap()
                            val rot = proxy.imageInfo.rotationDegrees
                            val up = if (rot == 0) raw else Bitmap.createBitmap(
                                raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rot.toFloat()) }, true
                            )
                            onFrameNow(up)
                        }
                    } catch (_: Throwable) {
                    } finally { proxy.close() }
                }
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                unavailable = false
            } catch (_: Exception) { unavailable = true }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { provider?.unbindAll(); exec.shutdown() }
    }

    if (unavailable) { MessageScreen("دوربین در دسترس نیست"); return }

    var size by remember { mutableStateOf(IntSize.Zero) }
    val w = size.width.toFloat(); val h = size.height.toFloat()

    val target = remember(scan.box, scan.phase, scan.frameW, scan.frameH, size) {
        if (size == IntSize.Zero) Rect.Zero
        else if (scan.phase == Phase.Locked || scan.phase == Phase.Searching) {
            scan.box?.let { mapBox(it, scan.frameW, scan.frameH, w, h) } ?: defaultBox(w, h)
        } else defaultBox(w, h)
    }
    val boxAnim = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    LaunchedEffect(target) {
        if (size == IntSize.Zero) return@LaunchedEffect
        if (boxAnim.value == Rect.Zero) { boxAnim.snapTo(target); return@LaunchedEffect }
        when (scan.phase) {
            Phase.Locked -> boxAnim.animateTo(target, tween(if (reduce) 200 else 250, easing = FastOutSlowInEasing))
            Phase.Searching -> boxAnim.animateTo(target, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium))
            else -> boxAnim.animateTo(target, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow))
        }
    }

    // Idle: the box breathes and drifts as if searching. Reduce motion: stops.
    val inf = rememberInfiniteTransition(label = "idle")
    val breatheMs = if (scan.warming) KalMotion.BREATHE * 2 else KalMotion.BREATHE
    val breathe by inf.animateFloat(1f, 1.02f, infiniteRepeatable(tween(breatheMs, easing = EaseInOut), RepeatMode.Reverse), label = "b")
    val driftX by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(5300, easing = EaseInOut), RepeatMode.Reverse), label = "dx")
    val driftY by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(7100, easing = EaseInOut), RepeatMode.Reverse), label = "dy")
    val idleLike = scan.phase == Phase.Idle || scan.phase == Phase.NotFound

    val strokeAlpha by animateFloatAsState(
        when {
            scan.warming -> 0.30f
            scan.phase == Phase.Locked -> 1f
            scan.phase == Phase.NotFound -> 0.40f
            else -> 0.80f
        }, tween(250), label = "stroke"
    )

    val locked = scan.phase == Phase.Locked
    val scanNow by rememberUpdatedState(scan)
    val sysGestures = WindowInsets.systemGestures
    val frac = ui.fraction
    val usedColor = KalColors.ripe(frac)

    fun currentRect(): Rect = boxAnim.value

    val announce = remember(scan.lockCount) {
        if (scan.lockCount > 0 && scan.label.isNotEmpty())
            "غذا پیدا شد: ${scan.label}، ${Persian.digits(scan.kcal)} کالری" else ""
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(KalColors.void)
            .onSizeChanged { size = it }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("باز کردن پیشرفت") { onOpenProgress(); true },
                    CustomAccessibilityAction("تنظیم کسری") { onOpenDeficit(); true },
                )
            }
            .pointerInput(locked) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val bottomInset = sysGestures.getBottom(this)
                    val fromBottom = down.position.y > this.size.height - bottomInset
                    var total = 0f; var swiped = false; var moved = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            if (!swiped && !moved && locked) {
                                onOpenResult(
                                    ResultArgs(scanNow.label, scanNow.kcal, currentRect(), previewView.bitmap?.asImageBitmap())
                                )
                            }
                            break
                        }
                        total += ch.positionChange().y
                        if (abs(ch.position.y - down.position.y) > viewConfiguration.touchSlop) moved = true
                        if (!fromBottom && !swiped && total < -80.dp.toPx()) { swiped = true; onOpenProgress() }
                    }
                }
            }
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Mask + scan box (flat; no blur).
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = chromeAlpha() }) {
            var r = currentRect()
            if (r == Rect.Zero) return@Canvas
            if (idleLike && !reduce) {
                val amp = 14.dp.toPx()
                r = centerRect(
                    Offset(r.center.x + driftX * amp, r.center.y + driftY * amp),
                    r.width * breathe, r.height * breathe,
                )
            }
            val rad = KalSpace.boxRadius.toPx()
            val path = squirclePath(r, rad)
            clipPath(path, ClipOp.Difference) { drawRect(KalColors.void.copy(alpha = 0.92f)) }
            clipPath(path) { drawPath(path, KalColors.phosphorFaint, style = Stroke(14.dp.toPx())) }
            val k = 2.25.dp.toPx()
            drawPath(squirclePath(r.inflate(k), rad + k), KalColors.void, style = Stroke(1.dp.toPx()))
            drawPath(path, KalColors.phosphor.copy(alpha = strokeAlpha), style = Stroke(1.5.dp.toPx()))
        }

        // Captions under the box.
        val caption = when {
            scan.lowLight -> "نور کم است"
            scan.phase == Phase.NotFound -> "غذایی پیدا نشد"
            locked && hintVisible -> "برای دیدن کالری لمس کنید"
            else -> ""
        }
        if (caption.isNotEmpty()) {
            Box(
                Modifier
                    .graphicsLayer { alpha = chromeAlpha() }
                    .offset { IntOffset(0, (currentRect().bottom + 16.dp.toPx()).toInt()) }
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) { KalText(caption, KalType.caption, KalColors.text3) }
        }

        // Top: deficit + wedge dots. Two stacked ≥48dp targets, ≥8dp apart.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 12.dp)
                .graphicsLayer { alpha = chromeAlpha() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.heightIn(min = 64.dp).widthIn(min = 120.dp)
                    .kalClickable("تنظیم کسری روزانه", onOpenDeficit),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    KalText(Persian.signed(-ui.deficit), KalType.title, usedColor, capped = true)
                    KalText("کالری در روز", KalType.caption, KalColors.text2)
                }
            }
            Spacer(Modifier.height(8.dp))
            val pct = (frac * 100).toInt().coerceAtLeast(0)
            Box(
                Modifier.height(48.dp).widthIn(min = 120.dp)
                    .kalClickable("${Persian.percent(pct)} از سقف امروز مصرف شده", onOpenProgress),
                contentAlignment = Alignment.Center,
            ) { WedgeDots(frac) }
        }

        Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announce })
    }
}

@Composable
fun WedgeDots(fraction: Float) {
    Canvas(Modifier.size(width = (10 * 6 + 9 * 8).dp, height = 6.dp)) {
        val d = 6.dp.toPx(); val gap = 8.dp.toPx()
        for (i in 0 until Calories.WEDGES) {
            val fill = Calories.wedgeFill(i, fraction)
            val pos = (i + 0.5f) / Calories.WEDGES
            val c = androidx.compose.ui.graphics.lerp(KalColors.line, KalColors.ripe(if (fraction > 1f) 1f else pos), fill)
            // wedges fill right to left (RTL)
            val x = size.width - d - i * (d + gap)
            drawCircle(c, d / 2, Offset(x + d / 2, size.height / 2))
        }
    }
}

