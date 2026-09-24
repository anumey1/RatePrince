package com.dicereligion.rateprince.domain

import com.dicereligion.rateprince.domain.model.CurrencyMeta
import com.dicereligion.rateprince.domain.model.RateConfig
import java.math.BigDecimal

object LadderSpec {
    /**
     * 46 rows, ascending, treated as an immutable contract.
     *
     * Tuned for a local currency in the JPY/KRW/VND magnitude band. For USD/EUR the
     * rows above ~5,000 are dead weight; a magnitude-aware generator is a Phase 6
     * candidate and can replace this without touching the UI.
     */
    val DEFAULT: List<Int> = listOf(
        1, 2, 5, 10, 20, 30, 40, 50,
        100, 150, 200, 250, 300, 400, 500, 600, 700, 800, 900,
        1_000, 1_500, 2_000, 2_500, 3_000, 3_500, 4_000, 4_500, 5_000,
        6_000, 7_000, 8_000, 9_000,
        10_000, 15_000, 20_000, 25_000, 30_000, 35_000, 40_000, 45_000, 50_000,
        60_000, 70_000, 80_000, 90_000, 100_000,
    )
}

data class LadderRow(
    val localAmount: BigDecimal,
    val homeAmount: BigDecimal,
    val localLabel: String,
    val homeLabel: String,
)

/**
 * Computed, never stored: 46 multiplications is microseconds, and a cache would be a
 * correctness liability the first time the rate changes. Uses [ConversionEngine.convert],
 * the same call as the live field, so the two can never disagree.
 */
fun buildLadder(
    config: RateConfig,
    local: CurrencyMeta,
    home: CurrencyMeta,
    engine: ConversionEngine,
    formatter: MoneyFormatter,
): List<LadderRow> {
    require(local.code == config.localCurrency && home.code == config.homeCurrency) {
        "currency metadata does not match the config pair"
    }
    return LadderSpec.DEFAULT.map { n ->
        val localAmount = BigDecimal(n)
        val homeAmount = engine.convert(localAmount, config, home.fractionDigits)
        LadderRow(
            localAmount = localAmount,
            homeAmount = homeAmount,
            localLabel = formatter.format(localAmount, local),
            homeLabel = formatter.format(homeAmount, home),
        )
    }
}
