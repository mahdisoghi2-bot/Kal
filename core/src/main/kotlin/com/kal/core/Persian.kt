package com.kal.core

/** The one number formatter. Persian digits + Persian thousands separator (٬). */
object Persian {
    private const val THOUSANDS = '\u066C'
    private const val MINUS = '\u2212'
    private const val LRI = '\u2066' // left-to-right isolate
    private const val PDI = '\u2069'
    private val digits = charArrayOf('۰','۱','۲','۳','۴','۵','۶','۷','۸','۹')

    fun digits(n: Int): String = format(n, group = false)

    fun format(value: Int, group: Boolean = true): String {
        val neg = value < 0
        val s = Math.abs(value.toLong()).toString()
        val sb = StringBuilder()
        for ((i, c) in s.withIndex()) {
            if (group && i > 0 && (s.length - i) % 3 == 0) sb.append(THOUSANDS)
            sb.append(digits[c - '0'])
        }
        return if (neg) "$LRI$MINUS$sb$PDI" else sb.toString()
    }

    /** Signed with explicit sign: +۱۲۰ / −۵۰۰. LTR isolate keeps the sign left of the digits. */
    fun signed(value: Int, plus: Boolean = false): String {
        val body = format(Math.abs(value))
        return when {
            value < 0 -> "$LRI$MINUS$body$PDI"
            plus && value > 0 -> "$LRI+$body$PDI"
            else -> body
        }
    }

    fun percent(p: Int): String = digits(p) + "٪"
}
