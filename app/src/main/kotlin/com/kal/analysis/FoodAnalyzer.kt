package com.kal.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.json.JSONArray
import java.io.File

/** One detected dish. [box] is normalised (0..1) in the upright analysis frame. */
data class Detection(val box: RectF, val label: String, val confidence: Float, val kcal: Int)

/**
 * The seam for swapping models. Implement this for another on-device model or a remote API;
 * the UI only ever sees [Detection]s. analyze() is called off the main thread, latest frame only.
 */
interface FoodAnalyzer {
    val isReady: Boolean
    fun analyze(frame: Bitmap): List<Detection>
    fun close() {}
}

data class FoodEntry(val id: String, val fa: String, val kcal: Int, val aliases: List<String>)

/** Persian dish name -> kcal per typical serving. Edit files/foods.json on-device to override the bundled list. */
class FoodDb(val entries: List<FoodEntry>) {
    private fun norm(s: String) = s.lowercase().replace('_', ' ').trim()

    fun byLabel(label: String): FoodEntry? {
        val n = norm(label)
        return entries.firstOrNull { e ->
            norm(e.id) == n || e.fa == label || e.aliases.any { norm(it) == n }
        }
    }

    companion object {
        fun load(ctx: Context): FoodDb {
            val override = File(ctx.filesDir, "foods.json")
            val text = runCatching {
                if (override.exists()) override.readText() else ctx.assets.open("foods.json").bufferedReader().readText()
            }.getOrDefault("[]")
            val arr = JSONArray(text)
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val al = o.optJSONArray("aliases")
                FoodEntry(
                    o.getString("id"), o.getString("fa"), o.getInt("kcal"),
                    if (al == null) emptyList() else (0 until al.length()).map { al.getString(it) },
                )
            }
            return FoodDb(list)
        }
    }
}
