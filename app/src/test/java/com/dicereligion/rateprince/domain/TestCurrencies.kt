package com.dicereligion.rateprince.domain

import com.dicereligion.rateprince.domain.model.CurrencyCode
import com.dicereligion.rateprince.domain.model.CurrencyMeta
import com.dicereligion.rateprince.domain.model.RateConfig
import com.dicereligion.rateprince.domain.model.RateSource
import java.math.BigDecimal

object TestCurrencies {
    val JPY = meta("JPY", "¥", 0)
    val INR = meta("INR", "₹", 2)
    val KWD = meta("KWD", "KD", 3)
    val IDR = meta("IDR", "Rp", 2)
    val LKR = meta("LKR", "Rs", 2)

    private fun meta(code: String, symbol: String, digits: Int) =
        CurrencyMeta(CurrencyCode(code), code, symbol, digits, "")

    fun config(local: CurrencyMeta, home: CurrencyMeta, rate: String) = RateConfig(
        homeCurrency = home.code,
        localCurrency = local.code,
        rate = BigDecimal(rate),
        updatedAtEpochMillis = 0L,
        source = RateSource.MANUAL,
    )

    /** local, home, rate — 0-, 2- and 3-digit currencies, rates from 0.000058 to 92.5. */
    val MATRIX = listOf(
        Triple(JPY, INR, "0.5832"),
        Triple(IDR, INR, "0.005812"),
        Triple(IDR, INR, "0.000058"),
        Triple(KWD, INR, "92.5"),
        Triple(INR, JPY, "1.7146"),
        Triple(INR, KWD, "0.003546"),
        Triple(IDR, KWD, "0.00001892"),
        Triple(KWD, JPY, "485.123456"),
        Triple(JPY, KWD, "0.00206"),
    )
}
