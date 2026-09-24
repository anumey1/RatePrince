package com.dicereligion.rateprince.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.dicereligion.rateprince.domain.model.CurrencyCode
import com.dicereligion.rateprince.domain.model.RateConfig
import com.dicereligion.rateprince.domain.model.RateSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

val Context.rateConfigStore: DataStore<StoredRateConfig> by dataStore(
    fileName = "rate_config.json",
    serializer = StoredRateConfigSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { StoredRateConfigSerializer.defaultValue },
)

/**
 * Single source of truth for the global [RateConfig]. Owns two responsibilities
 * nobody else has:
 * - Rate validation: `rate <= 0` is rejected here, so the engine never divides by zero.
 * - Widget invalidation: every mutation calls [onConfigChanged], so no settings path
 *   can update the app and leave the widget stale.
 */
class RateConfigRepository(
    private val store: DataStore<StoredRateConfig>,
    private val onConfigChanged: suspend () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {

    val config: Flow<RateConfig> = store.data
        .catch { e ->
            // Never let the widget crash on a read failure; render the unconfigured state.
            if (e is IOException) emit(StoredRateConfigSerializer.defaultValue) else throw e
        }
        .map { it.toDomain() }

    suspend fun current(): RateConfig = config.first()

    suspend fun setRate(rate: BigDecimal) {
        require(rate > BigDecimal.ZERO) { "rate must be positive" }
        require(rate < MAX_RATE) { "rate must have at most 12 integer digits" }
        store.updateData {
            it.copy(
                rate = rate.stripTrailingZeros().toPlainString(),
                updatedAtMillis = now(),
                source = RateSource.MANUAL.name,
            )
        }
        onConfigChanged()
    }

    /**
     * Changes the currency pair. Picking the exact reverse of the current pair is a swap
     * and inverts the rate. Any other change clears the rate, because a JPY rate is
     * meaningless for USD and a wrong number is worse than a "set your rate" prompt.
     */
    suspend fun setPair(home: CurrencyCode, local: CurrencyCode) {
        require(home != local) { "home and local currency must differ" }
        val current = current()
        when {
            home == current.homeCurrency && local == current.localCurrency -> return
            home == current.localCurrency && local == current.homeCurrency -> {
                swapPair()
                return
            }
        }
        store.updateData {
            it.copy(
                homeCurrency = home.value,
                localCurrency = local.value,
                rate = "0",
                updatedAtMillis = now(),
                source = RateSource.MANUAL.name,
            )
        }
        onConfigChanged()
    }

    /** Swaps home and local and inverts the rate, in one atomic update. */
    suspend fun swapPair() {
        store.updateData {
            val rate = it.rate.toBigDecimalOrNull()?.takeIf { r -> r > BigDecimal.ZERO }
            it.copy(
                homeCurrency = it.localCurrency,
                localCurrency = it.homeCurrency,
                // An unconfigured rate stays unconfigured.
                rate = rate?.let(::invert)?.toPlainString() ?: "0",
                updatedAtMillis = now(),
            )
        }
        onConfigChanged()
    }

    private companion object {
        val MAX_RATE: BigDecimal = BigDecimal.TEN.pow(12)

        /**
         * 10 significant digits: enough that inverting twice returns the original rate,
         * bounded so the stored string doesn't grow on repeated swaps.
         */
        val INVERT_CONTEXT = MathContext(10, RoundingMode.HALF_UP)

        fun invert(rate: BigDecimal): BigDecimal =
            BigDecimal.ONE.divide(rate, INVERT_CONTEXT).stripTrailingZeros()
    }
}
