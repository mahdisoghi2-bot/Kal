package com.kal.core

/** sRGB colours as packed ARGB ints, UI-free. */
object Palette {
    const val VOID = 0xFF000000.toInt()
    const val INK = 0xFF070807.toInt()
    const val GRAPHITE = 0xFF111411.toInt()
    const val LINE = 0xFF1E241E.toInt()
    const val PHOSPHOR = 0xFF39FF14.toInt()
    const val PHOSPHOR_DIM = 0xFF1F9E0A.toInt()
    const val TANGERINE = 0xFFFF8A1C.toInt()
    const val TANGERINE_DIM = 0xFF9C5410.toInt()
}

object Ripening {
    const val RIPE_START = 0.80f

    /** 0..0.8 → 0; 0.8..1.0 → smooth 0..1; >1 → 1. */
    fun ripeness(fraction: Float): Float {
        val t = ((fraction - RIPE_START) / (1f - RIPE_START)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun lerpColor(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return (x + (y - x) * t).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun bright(fraction: Float) = lerpColor(Palette.PHOSPHOR, Palette.TANGERINE, ripeness(fraction))
    fun dim(fraction: Float) = lerpColor(Palette.PHOSPHOR_DIM, Palette.TANGERINE_DIM, ripeness(fraction))
}
