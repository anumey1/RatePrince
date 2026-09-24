package com.dicereligion.rateprince.ui

import com.dicereligion.rateprince.domain.invertRate
import com.dicereligion.rateprince.ui.rate.RateDraft
import com.dicereligion.rateprince.ui.rate.RateField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class RateDraftTest {

    @Test
    fun `clean draft shows the stored rate both ways and is not dirty`() {
        val draft = RateDraft.from(BigDecimal("0.58"))
        assertEquals("0.58", draft.forward)
        assertEquals("1.72414", draft.inverse)
        assertFalse(draft.isDirty)
        assertNull(draft.rate)
    }

    @Test
    fun `unset rate gives empty fields`() {
        val draft = RateDraft.from(BigDecimal.ZERO)
        assertEquals("", draft.forward)
        assertEquals("", draft.inverse)
    }

    @Test
    fun `editing forward updates inverse and saves the typed value`() {
        val draft = RateDraft.from(BigDecimal.ZERO).editForward("0.5")!!
        assertEquals("2", draft.inverse)
        assertEquals(RateField.FORWARD, draft.editedField)
        assertEquals(BigDecimal("0.5"), draft.rate)
    }

    @Test
    fun `editing inverse saves the exact inversion, not the rounded forward text`() {
        val draft = RateDraft.from(BigDecimal.ZERO).editInverse("172")!!
        assertEquals("0.00581395", draft.forward)
        assertEquals(invertRate(BigDecimal("172")), draft.rate)
        assertEquals(BigDecimal("0.005813953488"), draft.rate)
    }

    @Test
    fun `comma separator works in either field`() {
        assertEquals(BigDecimal("0.58"), RateDraft.from(BigDecimal.ZERO).editForward("0,58")!!.rate)
    }

    @Test
    fun `invalid keystrokes are rejected`() {
        val draft = RateDraft.from(BigDecimal("0.58"))
        assertNull(draft.editForward("0.5a"))
        assertNull(draft.editForward("-1"))
        assertNull(draft.editInverse("1.2.3"))
    }

    @Test
    fun `zero is an error, empty is not`() {
        val zero = RateDraft.from(BigDecimal.ZERO).editForward("0")!!
        assertTrue(zero.hasError)
        assertFalse(zero.canSave)
        assertEquals("", zero.inverse)

        val empty = RateDraft.from(BigDecimal("0.58")).editForward("")!!
        assertFalse(empty.hasError)
        assertFalse(empty.canSave)
    }

    @Test
    fun `intermediate trailing separator is savable`() {
        val draft = RateDraft.from(BigDecimal.ZERO).editForward("2.")!!
        assertFalse(draft.hasError)
        assertEquals(BigDecimal("2"), draft.rate)
    }
}
