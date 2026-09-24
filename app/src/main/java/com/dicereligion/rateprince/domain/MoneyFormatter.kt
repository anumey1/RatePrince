package com.dicereligion.rateprince.domain

import com.dicereligion.rateprince.domain.model.CurrencyMeta
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/**
 * Locale-aware money output. Grouping follows the user's locale, so INR on en-IN
 * renders with lakh grouping (₹1,23,456.00) — correct, do not normalise it away.
 * java.text on the JVM cannot do lakh grouping and Android's ICU-backed java.text
 * may, so grouping is applied here by [group] to behave identically on both.
 *
 * Fraction digits and symbols come from [CurrencyMeta] (the bundled catalog), never
 * from `java.util.Currency`, which does not know every active code and renders
 * several symbols as the bare code.
 *
 * Not thread-safe (NumberFormat isn't). Create one per screen / composition.
 */
class MoneyFormatter(private val locale: Locale = Locale.getDefault()) {

    private val currencyFormats = mutableMapOf<CurrencyMeta, NumberFormat>()
    private val plainFormats = mutableMapOf<Int, NumberFormat>()
    private val groupingSeparator = DecimalFormatSymbols.getInstance(locale).groupingSeparator
    private val secondaryGroupSize = if (usesLakhGrouping(locale)) 2 else 3

    /** With symbol, placed where the locale puts it: "₹870.00", "1 500 ¥" (fr-FR). */
    fun format(amount: BigDecimal, currency: CurrencyMeta): String {
        val formatted = currencyFormats.getOrPut(currency) { currencyFormat(currency) }.format(amount)
        return withCurrencySpacing(group(formatted), currency.symbol)
    }

    /** Symbol-free, for tight widget layouts where the symbol is in a separate label. */
    fun formatPlain(amount: BigDecimal, currency: CurrencyMeta): String = group(
        plainFormats.getOrPut(currency.fractionDigits) {
            numberFormat(currency.fractionDigits, currency.fractionDigits)
        }.format(amount)
    )

    /**
     * An exchange rate, at its own precision rather than a currency's digits:
     * 0.58 → "0.58", 0.0058 → "0.0058", 1/0.58 → "1.72414". Six significant digits.
     */
    fun formatRate(rate: BigDecimal): String {
        val rounded = rate.round(MathContext(RATE_SIGNIFICANT_DIGITS, RoundingMode.HALF_UP))
            .stripTrailingZeros()
        return group(numberFormat(0, rounded.scale().coerceAtLeast(0)).format(rounded))
    }

    private fun currencyFormat(currency: CurrencyMeta): NumberFormat =
        // getCurrencyInstance returns a DecimalFormat on both the JVM and Android.
        (NumberFormat.getCurrencyInstance(locale) as DecimalFormat).apply {
            decimalFormatSymbols = decimalFormatSymbols.apply { currencySymbol = currency.symbol }
            minimumFractionDigits = currency.fractionDigits
            maximumFractionDigits = currency.fractionDigits
            roundingMode = RoundingMode.HALF_UP
            isGroupingUsed = false
        }

    private fun numberFormat(minFraction: Int, maxFraction: Int): NumberFormat =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = minFraction
            maximumFractionDigits = maxFraction
            roundingMode = RoundingMode.HALF_UP
            isGroupingUsed = false
        }

    /** Inserts grouping separators into the integer digits: 3, then [secondaryGroupSize]. */
    private fun group(formatted: String): String {
        val start = formatted.indexOfFirst { it.isDigit() }
        if (start < 0) return formatted
        var end = start
        while (end < formatted.length && formatted[end].isDigit()) end++
        val digits = formatted.substring(start, end)
        if (digits.length <= 3) return formatted

        val leading = digits.dropLast(3).reversed().chunked(secondaryGroupSize).map { it.reversed() }.reversed()
        val grouped = (leading + digits.takeLast(3)).joinToString(groupingSeparator.toString())
        return formatted.substring(0, start) + grouped + formatted.substring(end)
    }

    private companion object {
        const val RATE_SIGNIFICANT_DIGITS = 6
    }
}

/**
 * java.text does not apply CLDR currency spacing, so a letter symbol runs into the
 * digits ("Rs1,000.00"). Insert a no-break space between a letter and a digit at the
 * symbol boundary. Symbols like "₹" or "$" are left touching the number.
 */
internal fun withCurrencySpacing(formatted: String, symbol: String): String {
    if (symbol.isEmpty()) return formatted
    val start = formatted.indexOf(symbol)
    if (start < 0) return formatted
    val end = start + symbol.length
    return when {
        start == 0 && end < formatted.length &&
            symbol.last().isLetter() && formatted[end].isDigit() ->
            formatted.substring(0, end) + NBSP + formatted.substring(end)

        start > 0 && end == formatted.length &&
            symbol.first().isLetter() && formatted[start - 1].isDigit() ->
            formatted.substring(0, start) + NBSP + formatted.substring(start)

        else -> formatted
    }
}

private const val NBSP = ' '

/** Regions whose CLDR number pattern is `#,##,##0` (lakh/crore grouping). */
private val LAKH_REGIONS = setOf("IN", "BD", "NP", "PK")

internal fun usesLakhGrouping(locale: Locale): Boolean = locale.country in LAKH_REGIONS
