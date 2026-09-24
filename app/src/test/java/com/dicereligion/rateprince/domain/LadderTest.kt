package com.dicereligion.rateprince.domain

import com.dicereligion.rateprince.domain.TestCurrencies.INR
import com.dicereligion.rateprince.domain.TestCurrencies.JPY
import com.dicereligion.rateprince.domain.TestCurrencies.config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class LadderTest {

    private val engine = ConversionEngine()
    private val formatter = MoneyFormatter(Locale.US)

    @Test
    fun `spec has 46 strictly ascending rows from 1 to 100000`() {
        val spec = LadderSpec.DEFAULT
        assertEquals(46, spec.size)
        assertEquals(1, spec.first())
        assertEquals(100_000, spec.last())
        assertTrue(spec.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `matches the worked example in Appendix A`() {
        val ladder = buildLadder(config(JPY, INR, "0.58"), JPY, INR, engine, formatter)
            .associate { it.localAmount.toInt() to it.homeAmount }
        mapOf(
            1 to "0.58", 2 to "1.16", 5 to "2.90", 10 to "5.80", 100 to "58.00",
            1_000 to "580.00", 5_000 to "2900.00", 10_000 to "5800.00",
            50_000 to "29000.00", 100_000 to "58000.00",
        ).forEach { (jpy, inr) -> assertEquals("¥$jpy", BigDecimal(inr), ladder[jpy]) }
    }

    @Test
    fun `labels are formatted with each side's currency`() {
        val row = buildLadder(config(JPY, INR, "0.58"), JPY, INR, engine, formatter)
            .first { it.localAmount.toInt() == 1_500 }
        assertEquals("¥1,500", row.localLabel)
        assertEquals("₹870.00", row.homeLabel)
    }

    @Test
    fun `mismatched currency metadata is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildLadder(config(JPY, INR, "0.58"), INR, JPY, engine, formatter)
        }
    }
}
