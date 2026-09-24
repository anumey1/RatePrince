package com.dicereligion.rateprince.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.dicereligion.rateprince.domain.model.CurrencyCode
import com.dicereligion.rateprince.domain.model.RateConfig
import com.dicereligion.rateprince.domain.model.RateSource
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** On-disk shape of [RateConfig]. */
@Serializable
data class StoredRateConfig(
    val homeCurrency: String = DEFAULT_HOME,
    val localCurrency: String = DEFAULT_LOCAL,
    /** `BigDecimal.toPlainString()` — never a float. "0" means "not configured yet". */
    val rate: String = "0",
    val updatedAtMillis: Long = 0L,
    /** [RateSource] name, not ordinal, so reordering the enum can't corrupt old files. */
    val source: String = RateSource.MANUAL.name,
    val schemaVersion: Int = SCHEMA_VERSION,
) {
    companion object {
        const val DEFAULT_HOME = "INR"
        const val DEFAULT_LOCAL = "JPY"
        const val SCHEMA_VERSION = 1
    }
}

object StoredRateConfigSerializer : Serializer<StoredRateConfig> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: StoredRateConfig = StoredRateConfig()

    override suspend fun readFrom(input: InputStream): StoredRateConfig =
        try {
            json.decodeFromString<StoredRateConfig>(input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("rate config", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("rate config", e)
        }

    override suspend fun writeTo(t: StoredRateConfig, output: OutputStream) {
        output.write(json.encodeToString(StoredRateConfig.serializer(), t).encodeToByteArray())
    }
}

/** Never throws: unreadable fields fall back to defaults so the widget always renders. */
internal fun StoredRateConfig.toDomain() = RateConfig(
    homeCurrency = codeOrDefault(homeCurrency, StoredRateConfig.DEFAULT_HOME),
    localCurrency = codeOrDefault(localCurrency, StoredRateConfig.DEFAULT_LOCAL),
    rate = rate.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 } ?: java.math.BigDecimal.ZERO,
    updatedAtEpochMillis = updatedAtMillis,
    source = RateSource.entries.firstOrNull { it.name == source } ?: RateSource.MANUAL,
)

private fun codeOrDefault(raw: String, default: String) =
    runCatching { CurrencyCode(raw) }.getOrElse { CurrencyCode(default) }
