package com.kal.analysis

import android.graphics.Bitmap
import com.kal.core.Calories

/**
 * PLACEHOLDER analyzer used only when no model files are bundled (see README, "Adding the model").
 * It finds a region with [Saliency] and then picks a dish from foods.json from the region's colour,
 * so the whole UX can be tested end to end. It does NOT recognise food. Results are not real.
 */
class DemoAnalyzer(private val db: FoodDb) : FoodAnalyzer {
    override val isReady = db.entries.isNotEmpty()

    override fun analyze(frame: Bitmap): List<Detection> {
        val r = Saliency.propose(frame) ?: return emptyList()
        val bucket = (r.meanHue / 30f).toInt().coerceIn(0, 11)
        val size = if (r.coverage > 0.35f) 1 else 0
        val e = db.entries[(bucket * 3 + size * 7) % db.entries.size]
        val area = r.box.width() * r.box.height()
        return listOf(Detection(r.box, e.fa, 0.62f, Calories.portionKcal(e.kcal, area)))
    }
}
