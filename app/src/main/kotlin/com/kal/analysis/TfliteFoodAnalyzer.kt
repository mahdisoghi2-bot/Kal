package com.kal.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.kal.core.Calories
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * On-device TFLite pipeline.
 *  - assets/food_classifier.tflite + assets/food_labels.txt : image classifier, input [1,H,W,3], output [1,N]
 *  - assets/food_detector.tflite (optional): standard TFLite SSD detector
 *      (outputs: boxes[1,N,4] as ymin,xmin,ymax,xmax / classes[1,N] / scores[1,N] / count[1]).
 *    If absent, the model-free [Saliency] proposer supplies the box.
 * Labels are mapped to Persian names + kcal via foods.json (id or aliases).
 */
class TfliteFoodAnalyzer private constructor(
    private val classifier: Interpreter,
    private val labels: List<String>,
    private val detector: Interpreter?,
    private val db: FoodDb,
) : FoodAnalyzer {
    override val isReady = true

    override fun analyze(frame: Bitmap): List<Detection> {
        val boxes = detectBoxes(frame)
        val out = ArrayList<Detection>()
        for (box in boxes.take(3)) {
            val crop = crop(frame, box) ?: continue
            val (label, conf) = classify(crop) ?: continue
            val entry = db.byLabel(label) ?: continue
            val area = box.width() * box.height()
            out.add(Detection(box, entry.fa, conf, Calories.portionKcal(entry.kcal, area)))
        }
        return out
    }

    private fun detectBoxes(frame: Bitmap): List<RectF> {
        val det = detector ?: return listOfNotNull(Saliency.propose(frame)?.box)
        val inT = det.getInputTensor(0)
        val shape = inT.shape()                       // [1,h,w,3]
        val scaled = Bitmap.createScaledBitmap(frame, shape[2], shape[1], true)
        val input = toBuffer(scaled, inT.dataType())
        val n = det.getOutputTensor(0).shape()[1]
        val boxes = Array(1) { Array(n) { FloatArray(4) } }
        val classes = Array(1) { FloatArray(n) }
        val scores = Array(1) { FloatArray(n) }
        val count = FloatArray(1)
        det.runForMultipleInputsOutputs(
            arrayOf<Any>(input),
            mapOf(0 to boxes, 1 to classes, 2 to scores, 3 to count),
        )
        val res = ArrayList<Pair<Float, RectF>>()
        for (i in 0 until n) {
            if (scores[0][i] < 0.4f) continue
            val b = boxes[0][i]
            res.add(scores[0][i] to RectF(b[1].coerceIn(0f, 1f), b[0].coerceIn(0f, 1f), b[3].coerceIn(0f, 1f), b[2].coerceIn(0f, 1f)))
        }
        return res.sortedByDescending { (it.second.width() * it.second.height()) }.map { it.second }
    }

    private fun crop(frame: Bitmap, b: RectF): Bitmap? {
        val x = (b.left * frame.width).toInt().coerceIn(0, frame.width - 2)
        val y = (b.top * frame.height).toInt().coerceIn(0, frame.height - 2)
        val w = (b.width() * frame.width).toInt().coerceIn(2, frame.width - x)
        val h = (b.height() * frame.height).toInt().coerceIn(2, frame.height - y)
        return Bitmap.createBitmap(frame, x, y, w, h)
    }

    private fun classify(bmp: Bitmap): Pair<String, Float>? {
        val inT = classifier.getInputTensor(0)
        val shape = inT.shape()
        val scaled = Bitmap.createScaledBitmap(bmp, shape[2], shape[1], true)
        val input = toBuffer(scaled, inT.dataType())
        val outT = classifier.getOutputTensor(0)
        val n = outT.shape().last()
        val probs = FloatArray(n)
        if (outT.dataType() == DataType.FLOAT32) {
            val out = Array(1) { FloatArray(n) }
            classifier.run(input, out); out[0].copyInto(probs)
        } else {
            val out = Array(1) { ByteArray(n) }
            classifier.run(input, out)
            val q = outT.quantizationParams()
            for (i in 0 until n) probs[i] = ((out[0][i].toInt() and 0xFF) - q.zeroPoint) * q.scale
        }
        var best = 0
        for (i in 1 until n) if (probs[i] > probs[best]) best = i
        val label = labels.getOrNull(best) ?: return null
        return label to probs[best]
    }

    /** UINT8 models take raw bytes; FLOAT32 models take (v-127.5)/127.5. */
    private fun toBuffer(bmp: Bitmap, type: DataType): ByteBuffer {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val bytes = if (type == DataType.FLOAT32) 4 else 1
        val buf = ByteBuffer.allocateDirect(w * h * 3 * bytes).order(ByteOrder.nativeOrder())
        for (p in px) {
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            if (type == DataType.FLOAT32) {
                buf.putFloat((r - 127.5f) / 127.5f); buf.putFloat((g - 127.5f) / 127.5f); buf.putFloat((b - 127.5f) / 127.5f)
            } else {
                buf.put(r.toByte()); buf.put(g.toByte()); buf.put(b.toByte())
            }
        }
        buf.rewind(); return buf
    }

    override fun close() { classifier.close(); detector?.close() }

    companion object {
        private fun load(ctx: Context, name: String): Interpreter? = runCatching {
            val fd = ctx.assets.openFd(name)
            val map = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            Interpreter(map, Interpreter.Options().setNumThreads(2))
        }.getOrNull()

        /** Returns null when no classifier model is bundled. */
        fun createOrNull(ctx: Context, db: FoodDb): TfliteFoodAnalyzer? {
            val cls = load(ctx, "food_classifier.tflite") ?: return null
            val labels = runCatching {
                ctx.assets.open("food_labels.txt").bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
            }.getOrNull() ?: return null
            return TfliteFoodAnalyzer(cls, labels, load(ctx, "food_detector.tflite"), db)
        }
    }
}
