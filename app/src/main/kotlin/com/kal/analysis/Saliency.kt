package com.kal.analysis

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.sqrt

/** Result of the cheap region proposer: where "something food-like" probably is, plus its mean hue. */
data class Region(val box: RectF, val meanHue: Float, val coverage: Float)

/**
 * Tiny, model-free region proposer: finds the area that differs most from the frame in
 * saturation/brightness. Used as the box source when no detector model is bundled.
 */
object Saliency {
    private const val GW = 24
    private const val GH = 32

    fun propose(frame: Bitmap): Region? {
        val small = Bitmap.createScaledBitmap(frame, GW, GH, true)
        val px = IntArray(GW * GH)
        small.getPixels(px, 0, GW, 0, 0, GW, GH)
        if (small !== frame) small.recycle()

        val hsv = FloatArray(3)
        val sat = FloatArray(px.size); val lum = FloatArray(px.size); val hue = FloatArray(px.size)
        var meanL = 0f
        for (i in px.indices) {
            Color.colorToHSV(px[i], hsv)
            hue[i] = hsv[0]; sat[i] = hsv[1]; lum[i] = hsv[2]; meanL += hsv[2]
        }
        meanL /= px.size
        val score = FloatArray(px.size) { sat[it] * 0.7f + abs(lum[it] - meanL) * 0.3f }
        val mean = score.average().toFloat()
        val std = sqrt(score.map { (it - mean) * (it - mean) }.average().toFloat())
        val thr = mean + 0.4f * std

        val xs = ArrayList<Int>(); val ys = ArrayList<Int>(); var hueSum = 0f
        for (y in 0 until GH) for (x in 0 until GW) {
            val i = y * GW + x
            // bias toward the middle of the frame, where people point the camera
            val cx = abs(x - GW / 2f) / (GW / 2f); val cy = abs(y - GH / 2f) / (GH / 2f)
            if (score[i] > thr && cx < 0.9f && cy < 0.9f) { xs.add(x); ys.add(y); hueSum += hue[i] }
        }
        val coverage = xs.size / (GW * GH).toFloat()
        if (coverage < 0.10f || coverage > 0.80f) return null
        xs.sort(); ys.sort()
        val lo = (xs.size * 0.06f).toInt(); val hi = (xs.size * 0.94f).toInt().coerceAtMost(xs.size - 1)
        val box = RectF(
            xs[lo] / GW.toFloat(), ys[lo.coerceAtMost(ys.size - 1)] / GH.toFloat(),
            (xs[hi] + 1) / GW.toFloat(), (ys[hi] + 1) / GH.toFloat(),
        )
        if (box.width() < 0.18f || box.height() < 0.18f) return null
        return Region(box, hueSum / xs.size, coverage)
    }
}
