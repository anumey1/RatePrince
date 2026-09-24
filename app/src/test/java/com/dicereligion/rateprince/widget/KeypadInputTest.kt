package com.dicereligion.rateprince.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeypadInputTest {

    private fun type(vararg keys: String): String =
        keys.fold("") { acc, key -> KeypadInput.append(acc, key) ?: acc }

    @Test
    fun `digits append`() {
        assertEquals("1500", type("1", "5", "0", "0"))
    }

    @Test
    fun `leading separator becomes zero-point`() {
        assertEquals("0.5", type(".", "5"))
    }

    @Test
    fun `no leading zeros`() {
        assertEquals("5", type("0", "5"))
        assertEquals("0", type("0", "0"))
        assertEquals("0.05", type("0", ".", "0", "5"))
    }

    @Test
    fun `second separator is ignored`() {
        assertNull(KeypadInput.append("1.5", "."))
        assertEquals("1.5", type("1", ".", "5", "."))
    }

    @Test
    fun `thirteenth integer digit and seventh decimal are ignored`() {
        assertNull(KeypadInput.append("999999999999", "9"))
        assertNull(KeypadInput.append("1.123456", "7"))
    }

    @Test
    fun `backspace removes one character and stops at empty`() {
        assertEquals("12", KeypadInput.backspace("12."))
        assertEquals("", KeypadInput.backspace("1"))
        assertEquals("", KeypadInput.backspace(""))
    }
}
