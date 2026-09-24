package com.dicereligion.rateprince.domain

import java.math.BigDecimal

/**
 * Parses what the user types. Locale-forgiving: `.` and `,` are both treated as the
 * decimal separator, because the numeric IME offers one or the other depending on locale.
 * Grouping separators are never accepted as input (ambiguous across locales).
 */
object AmountParser {
    /** 12 integer digits is past any realistic amount and keeps widget text from overflowing. */
    private val allowed = Regex("""^\d{0,12}([.,]\d{0,6})?$""")

    /** Returns null for "not yet a number" (empty, bare separator) and for invalid input. */
    fun parse(raw: String): BigDecimal? = parseWith(allowed, raw)

    /** Whether [raw] is an acceptable intermediate state while typing, e.g. "12." */
    fun isEditable(raw: String): Boolean = raw.isEmpty() || allowed.matches(clean(raw))
}

/**
 * Parses an exchange rate typed in Settings. Same separator rules as [AmountParser], but
 * allows 8 decimal places: a KWD-home/IDR-local rate is around 0.0000189 and needs them.
 */
object RateParser {
    private val allowed = Regex("""^\d{0,12}([.,]\d{0,8})?$""")

    /** Returns the rate only if it is a valid, strictly positive number. */
    fun parse(raw: String): BigDecimal? =
        parseWith(allowed, raw)?.takeIf { it > BigDecimal.ZERO }

    fun isEditable(raw: String): Boolean = raw.isEmpty() || allowed.matches(clean(raw))
}

private fun clean(raw: String): String = raw.trim().replace(" ", "").replace(" ", "")

private fun parseWith(allowed: Regex, raw: String): BigDecimal? {
    val cleaned = clean(raw)
    if (!allowed.matches(cleaned)) return null
    val normalized = cleaned.replace(',', '.')
    if (normalized.isEmpty() || normalized == ".") return null
    return normalized.toBigDecimalOrNull()
}
