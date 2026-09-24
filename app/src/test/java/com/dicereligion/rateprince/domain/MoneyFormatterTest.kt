package com.dicereligion.rateprince.domain

import com.dicereligion.rateprince.domain.TestCurrencies.INR
import com.dicereligion.rateprince.domain.TestCurrencies.JPY
import com.dicereligion.rateprince.domain.TestCurrencies.KWD
import com.dicereligion.rateprince.domain.TestCurrencies.LKR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

class MoneyFormatterTest {

    private val us = MoneyFormatter(Locale.US)

    @Test
    fun `INR uses lakh grouping on en-IN`() {
        val inIndia = MoneyFormatter(Locale.forLanguageTag("en-IN"))
        assertEquals("₹1,23,456.00", inIndia.format(BigDecimal("123456.00"), INR))
        assertEquals("₹1,00,00,000.00", inIndia.format(BigDecimal("10000000.00"), INR))
        assertEquals("₹999.00", inIndia.format(BigDecimal("999.00"), INR))
        assertEquals("1,23,456.00", inIndia.formatPlain(BigDecimal("123456"), INR))
    }

    @Test
    fun `western grouping is in threes`() {
        assertEquals("₹10,000,000.00", us.format(BigDecimal("10000000.00"), INR))
        assertEquals("¥100,000", us.format(BigDecimal(100_000), JPY))
    }

    @Test
    fun `grouping uses the locale's separator`() {
        assertEquals("1.234.567,50", MoneyFormatter(Locale.GERMANY).formatPlain(BigDecimal("1234567.5"), INR))
    }

    @Test
    fun `fraction digits come from the currency`() {
        assertEquals("¥1,500", us.format(BigDecimal(1500), JPY))
        assertEquals("₹870.00", us.format(BigDecimal("870.00"), INR))
        assertEquals("KD 3.546", us.format(BigDecimal("3.546"), KWD))
    }

    @Test
    fun `plain format omits the symbol`() {
        assertEquals("870.00", us.formatPlain(BigDecimal("870"), INR))
        assertEquals("1,500", us.formatPlain(BigDecimal(1500), JPY))
    }

    @Test
    fun `typed amounts keep their own decimals`() {
        assertEquals("1,500", us.formatAmount(BigDecimal("1500")))
        assertEquals("12.5", us.formatAmount(BigDecimal("12.5")))
        assertEquals("1,500.25", us.formatAmount(BigDecimal("1500.25")))
        assertEquals("1,50,000", MoneyFormatter(Locale.forLanguageTag("en-IN")).formatAmount(BigDecimal("150000")))
    }

    @Test
    fun `typed input keeps intermediate states for display`() {
        assertEquals("1,500", us.formatTyped("1500"))
        assertEquals("12.", us.formatTyped("12."))
        assertEquals("12.50", us.formatTyped("12.50"))
        assertEquals("0.5", us.formatTyped(".5"))
        assertEquals("1,234,567.8", us.formatTyped("1234567,8"))
        assertEquals("0", us.formatTyped("000"))
        assertEquals("1.500,25", MoneyFormatter(Locale.GERMANY).formatTyped("1500.25"))
        assertEquals("1,50,000.", MoneyFormatter(Locale.forLanguageTag("en-IN")).formatTyped("150000."))
        assertEquals(null, us.formatTyped(""))
        assertEquals(null, us.formatTyped("."))
    }

    @Test
    fun `letter symbols get a no-break space before digits`() {
        assertEquals("Rs 1,000.00", us.format(BigDecimal(1000), LKR))
    }

    @Test
    fun `symbol follows the locale's placement`() {
        val fr = MoneyFormatter(Locale.FRANCE).format(BigDecimal(1500), JPY)
        assertTrue(fr, fr.endsWith("¥"))
        assertTrue(fr, fr.startsWith("1"))
    }

    @Test
    fun `rate is shown at its own precision, not the home currency's`() {
        assertEquals("0.58", us.formatRate(BigDecimal("0.58")))
        assertEquals("0.0058", us.formatRate(BigDecimal("0.0058")))
        assertEquals("0.000018921", us.formatRate(BigDecimal("0.000018921")))
        assertEquals("100", us.formatRate(BigDecimal("100")))
        assertEquals("485.123", us.formatRate(BigDecimal("485.123456")))
        assertEquals("1.72414", us.formatRate(BigDecimal.ONE.divide(BigDecimal("0.58"), MathContext.DECIMAL64)))
    }
}
