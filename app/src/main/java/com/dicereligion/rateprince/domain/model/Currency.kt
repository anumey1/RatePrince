package com.dicereligion.rateprince.domain.model

/** ISO 4217 alpha-3 code, e.g. "JPY". */
@JvmInline
value class CurrencyCode(val value: String) {
    init {
        require(value.length == 3 && value.all { it in 'A'..'Z' }) { "bad code: $value" }
    }

    override fun toString(): String = value
}

data class CurrencyMeta(
    val code: CurrencyCode,
    val displayName: String,          // "Japanese Yen"
    val symbol: String,               // "¥"
    val fractionDigits: Int,          // JPY = 0, INR = 2, KWD = 3
    val flagEmoji: String,            // "🇯🇵" — for the picker, not the widget; empty for multi-country codes
)
