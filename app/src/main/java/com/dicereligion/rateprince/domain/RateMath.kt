package com.dicereligion.rateprince.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 10 significant digits: enough that inverting twice returns the original rate,
 * bounded so the stored string doesn't grow on repeated swaps.
 */
private val INVERT_CONTEXT = MathContext(10, RoundingMode.HALF_UP)

private val EDIT_CONTEXT = MathContext(6, RoundingMode.HALF_UP)

/** 1 / [rate], for swapping direction. [rate] must be positive. */
fun invertRate(rate: BigDecimal): BigDecimal =
    BigDecimal.ONE.divide(rate, INVERT_CONTEXT).stripTrailingZeros()

/**
 * A rate as text the user can edit and [RateParser] accepts: six significant digits,
 * no grouping, `.` separator, never exponent notation. 1/0.58 → "1.72414".
 */
fun rateForEditing(rate: BigDecimal): String =
    rate.round(EDIT_CONTEXT).stripTrailingZeros().toPlainString()
