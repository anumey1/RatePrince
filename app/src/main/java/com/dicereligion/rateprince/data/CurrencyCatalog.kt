package com.dicereligion.rateprince.data

import android.content.Context
import com.dicereligion.rateprince.domain.model.CurrencyCode
import com.dicereligion.rateprince.domain.model.CurrencyMeta
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Curated list of active, spendable currencies, bundled as `assets/currencies.json`.
 * `java.util.Currency.getAvailableCurrencies()` includes defunct, fund and metal codes
 * and misses newer ones (XCG, ZWG), so it is only a last-resort fallback here.
 */
class CurrencyCatalog(entries: List<CurrencyMeta>) {

    /** In catalog order (alphabetical by code). */
    val all: List<CurrencyMeta> = entries

    private val byCode: Map<CurrencyCode, CurrencyMeta> = entries.associateBy { it.code }

    fun meta(code: CurrencyCode): CurrencyMeta = byCode[code] ?: fallbackFromJdk(code)

    fun fractionDigits(code: CurrencyCode): Int = meta(code).fractionDigits

    /** Code-prefix matches first, then name-substring matches. Blank query returns [all]. */
    fun search(query: String): List<CurrencyMeta> {
        val q = query.trim()
        if (q.isEmpty()) return all
        val byPrefix = all.filter { it.code.value.startsWith(q, ignoreCase = true) }
        val byName = all.filter { it !in byPrefix && it.displayName.contains(q, ignoreCase = true) }
        return byPrefix + byName
    }

    companion object {
        private const val ASSET_NAME = "currencies.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): CurrencyCatalog =
            CurrencyCatalog(json.decodeFromString<List<CatalogEntry>>(text).map { it.toMeta() })

        fun fromAssets(context: Context): CurrencyCatalog =
            fromJson(context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() })
    }
}

@Serializable
private data class CatalogEntry(
    val code: String,
    val name: String,
    val symbol: String,
    val digits: Int,
) {
    fun toMeta() = CurrencyMeta(
        code = CurrencyCode(code),
        displayName = name,
        symbol = symbol,
        fractionDigits = digits,
        flagEmoji = flagEmoji(code),
    )
}

/** For a code the catalog doesn't know, e.g. one persisted by a future app version. */
private fun fallbackFromJdk(code: CurrencyCode): CurrencyMeta {
    val jdk = runCatching { java.util.Currency.getInstance(code.value) }.getOrNull()
    return CurrencyMeta(
        code = code,
        displayName = jdk?.displayName ?: code.value,
        symbol = code.value,
        fractionDigits = jdk?.defaultFractionDigits?.takeIf { it >= 0 } ?: 2,
        flagEmoji = flagEmoji(code.value),
    )
}

/**
 * ISO 4217 codes start with the issuing country's ISO 3166 code (JPY → JP, EUR → EU),
 * so the flag is two regional-indicator symbols. X-codes (XAF, XCD, …) are
 * supranational and have no single flag.
 */
internal fun flagEmoji(code: String): String {
    if (code.startsWith("X")) return ""
    return code.take(2).map { c -> String(Character.toChars(0x1F1E6 + (c - 'A'))) }
        .joinToString("")
}
