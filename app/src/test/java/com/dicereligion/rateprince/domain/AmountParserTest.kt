package com.dicereligion.rateprince.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AmountParserTest {

    @Test
    fun `comma decimal separator parses identically to period`() {
        assertEquals(AmountParser.parse("1250.5"), AmountParser.parse("1250,5"))
        assertEquals(BigDecimal("1250.5"), AmountParser.parse("1250,5"))
    }

    @Test
    fun `twelve integer digits accepted, thirteen rejected`() {
        assertEquals(BigDecimal("999999999999"), AmountParser.parse("999999999999"))
        assertNull(AmountParser.parse("1000000000000"))
        assertTrue(AmountParser.isEditable("999999999999"))
        assertFalse(AmountParser.isEditable("1000000000000"))
    }

    @Test
    fun `six decimal places accepted, seven rejected`() {
        assertEquals(BigDecimal("0.123456"), AmountParser.parse("0.123456"))
        assertNull(AmountParser.parse("0.1234567"))
    }

    @Test
    fun `not-yet-a-number states parse to null but are editable`() {
        listOf("", ".", ",", "  ").forEach {
            assertNull("'$it'", AmountParser.parse(it))
            assertTrue("'$it'", AmountParser.isEditable(it.trim()))
        }
    }

    @Test
    fun `trailing separator is a valid intermediate state`() {
        assertTrue(AmountParser.isEditable("12."))
        assertEquals(BigDecimal("12"), AmountParser.parse("12."))
    }

    @Test
    fun `leading separator parses`() {
        assertEquals(BigDecimal("0.5"), AmountParser.parse(".5"))
    }

    @Test
    fun `spaces and no-break spaces are ignored`() {
        assertEquals(BigDecimal("1500"), AmountParser.parse(" 1 500 "))
        assertEquals(BigDecimal("1500"), AmountParser.parse("1 500"))
    }

    @Test
    fun `garbage, signs, grouping and double separators are rejected`() {
        listOf("abc", "-5", "+5", "1,000.50", "1.2.3", "1e5", "12a").forEach {
            assertNull("'$it'", AmountParser.parse(it))
            assertFalse("'$it'", AmountParser.isEditable(it))
        }
    }

    @Test
    fun `rate parser rejects zero and allows eight decimals`() {
        assertNull(RateParser.parse("0"))
        assertNull(RateParser.parse("0.000"))
        assertNull(RateParser.parse(""))
        assertEquals(BigDecimal("0.00001892"), RateParser.parse("0.00001892"))
        assertNull(RateParser.parse("0.000018921"))
        assertEquals(BigDecimal("0.58"), RateParser.parse("0,58"))
        assertTrue(RateParser.isEditable("0."))
    }
}
