package com.dicereligion.rateprince.domain.model

import java.math.BigDecimal

data class RateConfig(
    val homeCurrency: CurrencyCode,       // what you think in: INR
    val localCurrency: CurrencyCode,      // where you are: JPY
    /** Home currency units per ONE unit of local currency. Never stored inverted. */
    val rate: BigDecimal,
    val updatedAtEpochMillis: Long,
    val source: RateSource,
) {
    /** A zero rate is the "not configured yet" signal that drives first-run UI. */
    val isUsable: Boolean get() = rate > BigDecimal.ZERO
}

enum class RateSource { MANUAL, FETCHED_REMOTE }
