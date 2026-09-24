package com.dicereligion.rateprince.domain

import com.dicereligion.rateprince.domain.model.RateConfig
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The only place money is multiplied. Rules (enforced by unit tests):
 * 1. Work at [MathContext] 20 / HALF_UP and round once, at the end, to the *target* currency's digits.
 * 2. Never round an intermediate, never round to the source currency's digits.
 * 3. HALF_UP everywhere, so the ladder and the live field can never disagree.
 * 4. Ladder rows go through [convert] too — no second code path.
 * 5. Callers guarantee [RateConfig.isUsable]; the repository rejects rate <= 0.
 */
class ConversionEngine {

    /** Working precision: generous enough that no ladder row is ever off by a display unit. */
    private val mathContext = MathContext(20, RoundingMode.HALF_UP)

    /** Local → home. */
    fun convert(
        amount: BigDecimal,
        config: RateConfig,
        targetFractionDigits: Int,
    ): BigDecimal =
        amount.multiply(config.rate, mathContext)
            .setScale(targetFractionDigits, RoundingMode.HALF_UP)
}
