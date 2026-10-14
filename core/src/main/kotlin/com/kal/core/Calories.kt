package com.kal.core

import java.time.LocalDate
import java.time.LocalDateTime

data class Meal(val id: Long, val epochMs: Long, val kcal: Int, val name: String)

object Calories {
    const val WEDGES = 10
    const val DAY_ROLLOVER_HOUR = 4

    fun dailyLimit(maintenance: Int, deficit: Int) = (maintenance - deficit).coerceAtLeast(0)

    /** The day rolls over at 04:00 local time. */
    fun dayKey(at: LocalDateTime): LocalDate = at.minusHours(DAY_ROLLOVER_HOUR.toLong()).toLocalDate()

    fun remaining(limit: Int, eaten: Int) = limit - eaten
    fun isOver(limit: Int, eaten: Int) = eaten > limit

    fun fraction(eaten: Int, limit: Int): Float =
        if (limit <= 0) (if (eaten > 0) 1f else 0f) else eaten.toFloat() / limit

    /** Portion estimate: relative box area vs typical, clamped, rounded to nearest 5 kcal. */
    fun portionKcal(base: Int, boxAreaFraction: Float, typical: Float = 0.30f): Int {
        val scale = (boxAreaFraction / typical).coerceIn(0.6f, 1.6f)
        return roundTo5((base * scale).toInt())
    }

    fun roundTo5(v: Int) = ((v + 2) / 5) * 5

    /** Fill (0..1) of wedge i (0 = first) given a 0..1 day fraction. Partial fills allowed. */
    fun wedgeFill(i: Int, dayFraction: Float): Float =
        (dayFraction * WEDGES - i).coerceIn(0f, 1f)

    fun stepTo50(v: Int) = ((v + 25) / 50) * 50
}
