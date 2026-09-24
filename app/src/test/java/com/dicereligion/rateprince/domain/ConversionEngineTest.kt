package com.dicereligion.rateprince.domain

import com.dicereligion.rateprince.domain.TestCurrencies.IDR
import com.dicereligion.rateprince.domain.TestCurrencies.INR
import com.dicereligion.rateprince.domain.TestCurrencies.JPY
import com.dicereligion.rateprince.domain.TestCurrencies.KWD
import com.dicereligion.rateprince.domain.TestCurrencies.MATRIX
import com.dicereligion.rateprince.domain.TestCurrencies.config
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class ConversionEngineTest {

    private val engine = ConversionEngine()
    private val formatter = MoneyFormatter(Locale.US)

    @Test
    fun `ladder and live field agree for every ladder amount`() {
        MATRIX.forEach { (local, home, rate) ->
            val config = config(local, home, rate)
            buildLadder(config, local, home, engine, formatter).forEach { row ->
                val live = engine.convert(row.localAmount, config, home.fractionDigits)
                assertEquals("${local.code}→${home.code} @ $rate, ${row.localAmount}", live, row.homeAmount)
            }
        }
    }

    @Test
    fun `result always has exactly the target currency's fraction digits`() {
        MATRIX.forEach { (local, home, rate) ->
            val result = engine.convert(BigDecimal("1234.5"), config(local, home, rate), home.fractionDigits)
            assertEquals("${home.code}", home.fractionDigits, result.scale())
        }
    }

    @Test
    fun `zero fraction digit currency never shows decimals`() {
        val result = engine.convert(BigDecimal(3), config(INR, JPY, "1.7241"), JPY.fractionDigits)
        assertEquals(BigDecimal("5"), result)       // 5.1723 → 5
        assertEquals("¥5", formatter.format(result, JPY))
    }

    @Test
    fun `three fraction digit currency keeps three digits`() {
        val result = engine.convert(BigDecimal(1000), config(INR, KWD, "0.003546"), KWD.fractionDigits)
        assertEquals(BigDecimal("3.546"), result)
    }

    @Test
    fun `rate with six decimals rounds half up at target scale`() {
        // 1000 × 0.005825 = 5.825 exactly: HALF_UP gives 5.83, HALF_EVEN would give 5.82.
        val result = engine.convert(BigDecimal(1000), config(IDR, INR, "0.005825"), INR.fractionDigits)
        assertEquals(BigDecimal("5.83"), result)
    }

    @Test
    fun `rounds once at the end, not per step`() {
        // Double arithmetic gives 1.7399999999999998 here.
        val result = engine.convert(BigDecimal(3), config(JPY, INR, "0.58"), INR.fractionDigits)
        assertEquals(BigDecimal("1.74"), result)
    }
}
